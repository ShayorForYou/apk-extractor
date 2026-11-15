package com.shayor.apkextractor

import com.shayor.apkextractor.data.ChannelZip
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.FileInputStream
import java.util.Random
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipException
import java.util.zip.ZipOutputStream

class ChannelZipTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private fun open(file: File): ChannelZip = FileInputStream(file).let { ChannelZip(it.channel, it) }

    private fun bytes(size: Int, seed: Long, compressible: Boolean) = ByteArray(size).also { b ->
        if (compressible) b.indices.forEach { b[it] = (it % 7).toByte() } else Random(seed).nextBytes(b)
    }

    private fun entry(zip: ChannelZip, name: String): ChannelZip.Entry =
        zip.getEntry(name).also { assertNotNull(name, it) }!!

    @Test
    fun readsStoredAndDeflatedEntries() {
        val stored = bytes(300_000, 1, compressible = false)
        val deflated = bytes(500_000, 2, compressible = true)
        val file = tmp.newFile("a.xapk")
        ZipOutputStream(file.outputStream()).use { zip ->
            zip.putNextEntry(ZipEntry("Android/obb/com.x/main.1.com.x.obb").apply {
                method = ZipEntry.STORED
                size = stored.size.toLong()
                crc = CRC32().apply { update(stored) }.value
            })
            zip.write(stored)
            zip.closeEntry()
            // Deflated with a data descriptor (sizes only after the data), like streamed archives.
            zip.putNextEntry(ZipEntry("com.x.apk"))
            zip.write(deflated)
            zip.closeEntry()
            zip.putNextEntry(ZipEntry("dir/"))
            zip.closeEntry()
        }
        open(file).use { zip ->
            assertEquals(3, zip.entries.size)
            val obb = entry(zip, "Android/obb/com.x/main.1.com.x.obb")
            assertEquals(stored.size.toLong(), obb.size)
            assertArrayEquals(stored, zip.getInputStream(obb).use { it.readBytes() })
            assertArrayEquals(deflated, zip.getInputStream(entry(zip, "com.x.apk")).use { it.readBytes() })
            assertTrue(entry(zip, "dir/").isDirectory)
        }
    }

    @Test
    fun readsZip64Archives() {
        // More than 65535 entries forces a Zip64 end of central directory.
        val file = tmp.newFile("many.zip")
        ZipOutputStream(file.outputStream().buffered()).use { zip ->
            zip.setLevel(0)
            for (i in 0..70_000) {
                zip.putNextEntry(ZipEntry("x/entry-$i"))
                zip.write(i.toString().toByteArray())
                zip.closeEntry()
            }
        }
        open(file).use { zip ->
            assertEquals(70_001, zip.entries.size)
            assertEquals("70000", zip.getInputStream(entry(zip, "x/entry-70000")).use { String(it.readBytes()) })
        }
    }

    @Test(expected = ZipException::class)
    fun rejectsNonZip() {
        val file = tmp.newFile("not.zip").apply { writeBytes(bytes(1000, 3, compressible = false)) }
        open(file)
    }
}
