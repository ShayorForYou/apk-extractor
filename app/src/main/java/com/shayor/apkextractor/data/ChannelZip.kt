package com.shayor.apkextractor.data

import java.io.Closeable
import java.io.EOFException
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel
import java.util.zip.Inflater
import java.util.zip.InflaterInputStream
import java.util.zip.ZipException

/**
 * A read-only zip over an open [FileChannel], with random access: only the central directory and
 * the entries asked for are ever read. Unlike [java.util.zip.ZipFile] it needs no file path, so it
 * works on a file descriptor from a content provider (re-opening one through /proc/self/fd is
 * refused on FUSE storage). Supports Zip64, so bundles over 4 GB work too.
 */
internal class ChannelZip(private val channel: FileChannel, private val onClose: Closeable = channel) : Closeable {

    class Entry(
        val name: String,
        internal val method: Int,
        internal val compressedSize: Long,
        val size: Long,
        internal val localOffset: Long,
    ) {
        val isDirectory get() = name.endsWith('/')
    }

    val entries: List<Entry>
    private val byName: Map<String, Entry>

    init {
        try {
            entries = readCentralDirectory()
            byName = entries.associateBy { it.name }
        } catch (e: Exception) {
            runCatching { onClose.close() }
            throw if (e is ZipException) e else ZipException("Not a readable zip").apply { initCause(e) }
        }
    }

    fun getEntry(name: String): Entry? = byName[name]

    /** The entry's uncompressed bytes. Reads are positional, so several streams can be open at once. */
    fun getInputStream(entry: Entry): InputStream {
        val header = read(entry.localOffset, LOCAL_HEADER)
        if (header.getInt(0) != LOCAL_SIG) throw ZipException("Bad local header: ${entry.name}")
        val dataStart = entry.localOffset + LOCAL_HEADER + u16(header, 26) + u16(header, 28)
        val raw = RangeStream(dataStart, entry.compressedSize)
        return when (entry.method) {
            STORED -> raw
            DEFLATED -> object : InflaterInputStream(raw, Inflater(true), BUFFER) {
                private var closed = false
                private var dummySent = false

                // Raw deflate may need one extra byte after the data to finish (as ZipFile does).
                override fun fill() {
                    if (dummySent) throw EOFException("Unexpected end of ZLIB input stream")
                    len = `in`.read(buf, 0, buf.size)
                    if (len == -1) {
                        buf[0] = 0
                        len = 1
                        dummySent = true
                    }
                    inf.setInput(buf, 0, len)
                }

                override fun close() {
                    if (closed) return
                    closed = true
                    inf.end()
                    super.close()
                }
            }
            else -> throw ZipException("Unsupported compression in ${entry.name}")
        }
    }

    override fun close() = onClose.close()

    // ---------------------------------------------------------------------------------------------

    private fun readCentralDirectory(): List<Entry> {
        val fileSize = channel.size()
        if (fileSize < EOCD_MIN) throw ZipException("Too small")
        // The end record sits in the last 22 bytes + up to 64 KB of comment.
        val tailSize = minOf(fileSize, EOCD_MIN + 0xFFFFL).toInt()
        val tailStart = fileSize - tailSize
        val tail = read(tailStart, tailSize)
        var eocd = -1
        for (i in tailSize - EOCD_MIN downTo 0) {
            if (tail.getInt(i) == EOCD_SIG) { eocd = i; break }
        }
        if (eocd < 0) throw ZipException("No end of central directory")

        var count = u16(tail, eocd + 10).toLong()
        var cdSize = u32(tail, eocd + 12)
        var cdOffset = u32(tail, eocd + 16)
        if (count == 0xFFFFL || cdSize == 0xFFFFFFFFL || cdOffset == 0xFFFFFFFFL) {
            // Zip64: a locator just before the end record points to the Zip64 end record.
            val locatorPos = tailStart + eocd - 20
            if (locatorPos < 0) throw ZipException("Missing Zip64 locator")
            val locator = read(locatorPos, 20)
            if (locator.getInt(0) != ZIP64_LOCATOR_SIG) throw ZipException("Missing Zip64 locator")
            val record = read(locator.getLong(8), 56)
            if (record.getInt(0) != ZIP64_EOCD_SIG) throw ZipException("Bad Zip64 end record")
            count = record.getLong(32)
            cdSize = record.getLong(40)
            cdOffset = record.getLong(48)
        }
        if (cdSize > MAX_DIRECTORY || cdOffset + cdSize > fileSize) throw ZipException("Bad central directory")

        val cd = read(cdOffset, cdSize.toInt())
        val list = ArrayList<Entry>(count.coerceAtMost(4096).toInt())
        var p = 0
        while (p + CENTRAL_HEADER <= cd.limit() && cd.getInt(p) == CENTRAL_SIG) {
            val method = u16(cd, p + 10)
            var compressed = u32(cd, p + 20)
            var size = u32(cd, p + 24)
            val nameLen = u16(cd, p + 28)
            val extraLen = u16(cd, p + 30)
            val commentLen = u16(cd, p + 32)
            var offset = u32(cd, p + 42)
            val nameBytes = ByteArray(nameLen).also { cd.position(p + CENTRAL_HEADER); cd.get(it) }
            if (size == 0xFFFFFFFFL || compressed == 0xFFFFFFFFL || offset == 0xFFFFFFFFL) {
                // Zip64 extra field: only the overflowed values are present, in this order.
                var e = p + CENTRAL_HEADER + nameLen
                val end = e + extraLen
                while (e + 4 <= end) {
                    val id = u16(cd, e)
                    val len = u16(cd, e + 2)
                    if (id == 0x0001) {
                        var q = e + 4
                        if (size == 0xFFFFFFFFL) { size = cd.getLong(q); q += 8 }
                        if (compressed == 0xFFFFFFFFL) { compressed = cd.getLong(q); q += 8 }
                        if (offset == 0xFFFFFFFFL) { offset = cd.getLong(q) }
                        break
                    }
                    e += 4 + len
                }
            }
            list += Entry(String(nameBytes, Charsets.UTF_8), method, compressed, size, offset)
            p += CENTRAL_HEADER + nameLen + extraLen + commentLen
        }
        if (list.isEmpty()) throw ZipException("Empty zip")
        return list
    }

    private fun read(position: Long, length: Int): ByteBuffer {
        val buffer = ByteBuffer.allocate(length).order(ByteOrder.LITTLE_ENDIAN)
        var pos = position
        while (buffer.hasRemaining()) {
            val n = channel.read(buffer, pos)
            if (n < 0) throw EOFException()
            pos += n
        }
        buffer.flip()
        return buffer
    }

    /** [length] bytes from [start], read with positional reads straight into the caller's array. */
    private inner class RangeStream(start: Long, length: Long) : InputStream() {
        private var pos = start
        private val end = start + length

        override fun read(): Int {
            val one = ByteArray(1)
            return if (read(one, 0, 1) < 0) -1 else one[0].toInt() and 0xFF
        }

        override fun read(b: ByteArray, off: Int, len: Int): Int {
            if (len == 0) return 0
            val left = end - pos
            if (left <= 0) return -1
            val n = channel.read(ByteBuffer.wrap(b, off, minOf(len.toLong(), left).toInt()), pos)
            if (n < 0) throw EOFException()
            pos += n
            return n
        }

        override fun available(): Int = (end - pos).coerceIn(0, Int.MAX_VALUE.toLong()).toInt()
    }

    private companion object {
        const val LOCAL_SIG = 0x04034b50
        const val CENTRAL_SIG = 0x02014b50
        const val EOCD_SIG = 0x06054b50
        const val ZIP64_LOCATOR_SIG = 0x07064b50
        const val ZIP64_EOCD_SIG = 0x06064b50
        const val LOCAL_HEADER = 30
        const val CENTRAL_HEADER = 46
        const val EOCD_MIN = 22
        const val STORED = 0
        const val DEFLATED = 8
        const val BUFFER = 64 * 1024
        const val MAX_DIRECTORY = 64L * 1024 * 1024

        fun u16(b: ByteBuffer, i: Int) = b.getShort(i).toInt() and 0xFFFF
        fun u32(b: ByteBuffer, i: Int) = b.getInt(i).toLong() and 0xFFFFFFFFL
    }
}
