package com.shayor.apkextractor.data

import java.io.BufferedOutputStream
import java.io.ByteArrayOutputStream
import java.io.EOFException
import java.io.File
import java.io.FilterInputStream
import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.io.BufferedInputStream
import java.io.Closeable
import java.util.zip.Inflater
import java.util.zip.ZipException

/**
 * A stand-in APK holding only what PackageManager needs to show an app before installing it: the
 * manifest, the resource table and res/ (for the name and icon). Code, native libraries and assets,
 * nearly all of an APK's size, are left out, so a preview never copies the whole file. The real
 * APK is streamed straight from where it lives when installing.
 */
internal object SlimApk {

    fun keep(name: String) = name == MANIFEST || name == "resources.arsc" || name.startsWith("res/")

    /**
     * Copies the entries [keep] selects from [zip] to [target] byte for byte (no recompressing),
     * with uncompressed data 4-byte aligned like zipalign so the resource table maps in place.
     */
    fun write(zip: ChannelZip, target: File) {
        val kept = zip.entries.filter { !it.isDirectory && keep(it.name) }
        if (kept.none { it.name == MANIFEST }) throw ZipException("No manifest")
        RawZipWriter(target).use { out ->
            for (e in kept) {
                zip.getRawStream(e).use { out.add(e.name, e.method, e.crc, e.compressedSize, e.size, it) }
            }
        }
    }

    /**
     * For an APK compressed inside a bundle, which can't be read in place: one pass over it,
     * walking its local headers. Entries [keep] selects are copied still compressed; the rest
     * (code, libraries, assets) are skipped without being inflated. The signers are read on the
     * way: the signing block sits near the end, so the last part of the stream is kept. Returns
     * the signers' certificate digests. Throws [ZipException] for APKs that can't be walked this
     * way (sizes only after the data), so the caller can extract the APK instead.
     */
    fun stream(apk: InputStream, target: File): Set<String> {
        val tail = TailStream(apk, TAIL)
        val input = BufferedInputStream(tail, BUFFER)
        val v1 = ArrayList<ByteArray>()
        val header = ByteBuffer.allocate(LOCAL).order(ByteOrder.LITTLE_ENDIAN)
        var manifest = false
        RawZipWriter(target).use { out ->
            while (true) {
                header.clear()
                if (!readFully(input, header.array(), 0, 4)) break
                // Past the last entry: the signing block or the central directory.
                if (header.getInt(0) != LOCAL_SIG) break
                if (!readFully(input, header.array(), 4, LOCAL - 4)) throw ZipException("Truncated")
                val flags = u16(header, 6)
                val method = u16(header, 8)
                val crc = u32(header, 14)
                val compressed = u32(header, 18)
                val size = u32(header, 22)
                val nameLen = u16(header, 26)
                val extraLen = u16(header, 28)
                if (flags and DATA_DESCRIPTOR != 0 || compressed == MAX32 || size == MAX32) throw ZipException("Can't walk")
                val nameBytes = ByteArray(nameLen)
                if (!readFully(input, nameBytes, 0, nameLen)) throw ZipException("Truncated")
                val name = String(nameBytes, Charsets.UTF_8)
                skipFully(input, extraLen.toLong())
                val data = BoundedStream(input, compressed)
                when {
                    name.endsWith('/') -> Unit
                    keep(name) -> {
                        if (name == MANIFEST) manifest = true
                        out.add(name, method, crc, compressed, size, data)
                    }
                    ApkSignatures.isV1Signature(name) && v1.size < 8 && size < MAX_V1 -> {
                        val raw = data.readBytes()
                        v1 += if (method == DEFLATED) inflate(raw, size.toInt()) else raw
                    }
                }
                data.skipRest()
            }
        }
        if (!manifest) throw ZipException("No manifest")
        tail.drain()
        return runCatching { tail.signingBlockCerts() }.getOrNull()?.ifEmpty { null }
            ?: ApkSignatures.fromV1Files(v1)
    }

    /** Writes a zip of entries given still compressed; stored data is 4-byte aligned. */
    private class RawZipWriter(target: File) : Closeable {
        private val out = BufferedOutputStream(target.outputStream(), BUFFER)
        private val central = ByteArrayOutputStream()
        private var offset = 0L
        private var count = 0

        fun add(name: String, method: Int, crc: Long, compressed: Long, size: Long, data: InputStream) {
            if (compressed > MAX32 || size > MAX32 || offset > MAX32) throw ZipException("Too big")
            if (++count > 0xFFFF) throw ZipException("Too many entries")
            val nameBytes = name.toByteArray(Charsets.UTF_8)
            val pad = if (method == STORED) ((4 - (offset + LOCAL + nameBytes.size) % 4) % 4).toInt() else 0
            out.write(
                le(LOCAL + nameBytes.size + pad) {
                    putInt(LOCAL_SIG); putShort(20); putShort(UTF8); putShort(method.toShort())
                    putShort(0); putShort(DATE); putInt(crc.toInt())
                    putInt(compressed.toInt()); putInt(size.toInt())
                    putShort(nameBytes.size.toShort()); putShort(pad.toShort()); put(nameBytes)
                },
            )
            if (data.copyCounting(out) != compressed) throw EOFException(name)
            central.write(
                le(CENTRAL + nameBytes.size) {
                    putInt(CENTRAL_SIG); putShort(20); putShort(20); putShort(UTF8); putShort(method.toShort())
                    putShort(0); putShort(DATE); putInt(crc.toInt())
                    putInt(compressed.toInt()); putInt(size.toInt())
                    putShort(nameBytes.size.toShort()); putShort(0); putShort(0); putShort(0); putShort(0)
                    putInt(0); putInt(offset.toInt()); put(nameBytes)
                },
            )
            offset += LOCAL + nameBytes.size + pad + compressed
        }

        override fun close() {
            out.use {
                if (offset > MAX32) throw ZipException("Too big")
                central.writeTo(it)
                it.write(
                    le(EOCD) {
                        putInt(EOCD_SIG); putShort(0); putShort(0)
                        putShort(count.toShort()); putShort(count.toShort())
                        putInt(central.size()); putInt(offset.toInt()); putShort(0)
                    },
                )
            }
        }
    }

    /** At most [length] bytes of [input]; [skipRest] moves past whatever wasn't read. */
    private class BoundedStream(private val input: InputStream, length: Long) : InputStream() {
        private var left = length

        override fun read(): Int {
            if (left <= 0) return -1
            val b = input.read()
            if (b < 0) throw EOFException()
            left--
            return b
        }

        override fun read(b: ByteArray, off: Int, len: Int): Int {
            if (left <= 0) return -1
            val n = input.read(b, off, minOf(len.toLong(), left).toInt())
            if (n < 0) throw EOFException()
            left -= n
            return n
        }

        fun skipRest() {
            skipFully(input, left)
            left = 0
        }
    }

    private fun readFully(input: InputStream, b: ByteArray, off: Int, len: Int): Boolean {
        var done = 0
        while (done < len) {
            val n = input.read(b, off + done, len - done)
            if (n < 0) {
                if (done == 0) return false
                throw EOFException()
            }
            done += n
        }
        return true
    }

    private fun skipFully(input: InputStream, n: Long) {
        var left = n
        while (left > 0) {
            val skipped = input.skip(left)
            if (skipped <= 0) {
                if (input.read() < 0) throw EOFException()
                left--
            } else {
                left -= skipped
            }
        }
    }

    private fun inflate(raw: ByteArray, size: Int): ByteArray {
        val inflater = Inflater(true)
        try {
            inflater.setInput(raw)
            val out = ByteArray(size)
            var n = 0
            while (n < size && !inflater.finished()) {
                val r = inflater.inflate(out, n, size - n)
                if (r == 0 && (inflater.needsInput() || inflater.needsDictionary())) break
                n += r
            }
            return out
        } finally {
            inflater.end()
        }
    }

    private fun u16(b: ByteBuffer, i: Int) = b.getShort(i).toInt() and 0xFFFF
    private fun u32(b: ByteBuffer, i: Int) = b.getInt(i).toLong() and 0xFFFFFFFFL

    /** Passes a stream through, counting its bytes and keeping the last [capacity] of them. */
    private class TailStream(input: InputStream, capacity: Int) : FilterInputStream(input) {
        private val ring = ByteArray(capacity)
        private val scratch = ByteArray(BUFFER)
        private var total = 0L

        override fun read(): Int {
            val one = ByteArray(1)
            return if (read(one, 0, 1) <= 0) -1 else one[0].toInt() and 0xFF
        }

        override fun read(b: ByteArray, off: Int, len: Int): Int {
            val n = `in`.read(b, off, len)
            if (n > 0) record(b, off, n)
            return n
        }

        // Skipped bytes must still pass through the ring.
        override fun skip(n: Long): Long {
            val buf = scratch
            var left = n
            while (left > 0) {
                val r = read(buf, 0, minOf(left, buf.size.toLong()).toInt())
                if (r < 0) break
                left -= r
            }
            return n - left
        }

        override fun markSupported() = false

        private fun record(b: ByteArray, off: Int, n: Int) {
            var o = off
            var left = n
            if (left > ring.size) {
                o += left - ring.size
                total += left - ring.size
                left = ring.size
            }
            while (left > 0) {
                val at = (total % ring.size).toInt()
                val chunk = minOf(left, ring.size - at)
                System.arraycopy(b, o, ring, at, chunk)
                o += chunk
                left -= chunk
                total += chunk
            }
        }

        fun drain() {
            while (read(scratch, 0, scratch.size) >= 0) Unit
        }

        /** Signers from the APK Signing Block, found through the end record kept in the ring. */
        fun signingBlockCerts(): Set<String> {
            val kept = minOf(total, ring.size.toLong()).toInt()
            val start = total - kept
            val bytes = ByteArray(kept)
            val first = (start % ring.size).toInt()
            val head = minOf(kept, ring.size - first)
            System.arraycopy(ring, first, bytes, 0, head)
            System.arraycopy(ring, 0, bytes, head, kept - head)
            val buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
            var eocd = -1
            for (i in kept - EOCD downTo maxOf(0, kept - EOCD - 0xFFFF)) {
                if (buf.getInt(i) == EOCD_SIG) { eocd = i; break }
            }
            if (eocd < 0) return emptySet()
            val cdOffset = buf.getInt(eocd + 16).toLong() and 0xFFFFFFFFL
            return ApkSignatures.fromSigningBlock({ pos, len ->
                val i = pos - start
                if (i < 0 || i + len > kept) throw EOFException()
                ByteBuffer.wrap(bytes, i.toInt(), len).slice().order(ByteOrder.LITTLE_ENDIAN)
            }, cdOffset)
        }
    }

    private inline fun le(size: Int, fill: ByteBuffer.() -> Unit): ByteArray =
        ByteBuffer.allocate(size).order(ByteOrder.LITTLE_ENDIAN).apply(fill).array()

    private fun InputStream.copyCounting(out: OutputStream): Long {
        val buf = ByteArray(BUFFER)
        var copied = 0L
        while (true) {
            val n = read(buf)
            if (n < 0) return copied
            out.write(buf, 0, n)
            copied += n
        }
    }

    private const val MANIFEST = "AndroidManifest.xml"
    private const val LOCAL_SIG = 0x04034b50
    private const val CENTRAL_SIG = 0x02014b50
    private const val EOCD_SIG = 0x06054b50
    private const val LOCAL = 30
    private const val CENTRAL = 46
    private const val EOCD = 22
    private const val UTF8: Short = 0x0800
    private const val DATE: Short = 0x0021 // 1980-01-01
    private const val MAX32 = 0xFFFFFFFFL
    private const val STORED = 0
    private const val DEFLATED = 8
    private const val DATA_DESCRIPTOR = 0x08
    private const val MAX_V1 = 1L shl 20
    private const val BUFFER = 128 * 1024

    /** Room for the signing block and central directory at the end of an APK. */
    private const val TAIL = 4 * 1024 * 1024
}
