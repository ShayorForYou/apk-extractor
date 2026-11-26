package com.shayor.apkextractor

import com.android.apksig.ApkVerifier
import com.shayor.apkextractor.data.ApkSignatures
import com.shayor.apkextractor.data.ChannelZip
import com.shayor.apkextractor.data.SlimApk
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

/**
 * Checks the install preview against real APKs: the stand-in holds exactly the manifest, resource
 * table and res/ of the original, byte for byte, and the signers read from the signature block
 * match what apksig finds by fully verifying the APK. APKs come from -DtestApks (a folder) and the
 * app's own debug build; tests skip when none are there.
 */
class SlimApkTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private fun apks(): List<File> {
        val dirs = listOfNotNull((System.getenv("TEST_APKS") ?: System.getProperty("testApks"))?.let(::File), File("build/outputs/apk/debug"))
        return dirs.flatMap { d -> d.walkTopDown().filter { it.isFile && it.name.endsWith(".apk") }.toList() }
            .also { assumeTrue("no APKs to test with", it.isNotEmpty()) }
    }

    private fun open(file: File): ChannelZip = FileInputStream(file).let { ChannelZip(it.channel, it) }

    private fun sha256(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    /** Signers as apksig sees them after verifying the whole APK (null if it isn't signed/valid). */
    private fun verifiedCerts(apk: File): Set<String>? {
        val result = ApkVerifier.Builder(apk).build().verify()
        if (!result.isVerified) return null
        val certs = result.signerCertificates.mapTo(HashSet()) { sha256(it.encoded) }
        result.signingCertificateLineage?.certificatesInLineage?.forEach { certs += sha256(it.encoded) }
        return certs
    }

    private fun assertSlimMatches(original: File, slim: File) {
        ZipFile(original).use { src ->
            ZipFile(slim).use { out ->
                val expected = src.entries().toList().filter { !it.isDirectory && SlimApk.keep(it.name) }.map { it.name }.sorted()
                val actual = out.entries().toList().map { it.name }.sorted()
                assertEquals(original.name, expected, actual)
                assertTrue("manifest", "AndroidManifest.xml" in actual)
                for (name in expected) {
                    // ZipFile checks each entry's CRC as it reads it.
                    val a = src.getInputStream(src.getEntry(name)).use { it.readBytes() }
                    val b = out.getInputStream(out.getEntry(name)).use { it.readBytes() }
                    assertArrayEquals(name, a, b)
                }
            }
        }
    }

    @Test
    fun slimCopyKeepsOnlyWhatThePreviewNeeds() {
        for (apk in apks()) {
            val zip = runCatching { open(apk) }.getOrNull() ?: continue
            zip.use {
                if (it.getEntry("AndroidManifest.xml") == null) return@use
                val slim = tmp.newFile()
                SlimApk.write(it, slim)
                assertSlimMatches(apk, slim)
                assertTrue("smaller", slim.length() <= apk.length())
                println("${apk.name}: ${apk.length() / 1024} KB -> ${slim.length() / 1024} KB")
            }
        }
    }

    @Test
    fun signersMatchFullVerification() {
        var checked = 0
        for (apk in apks()) {
            val expected = verifiedCerts(apk) ?: continue
            val actual = open(apk).use(ApkSignatures::fromZip)
            assertTrue("${apk.name}: $actual vs $expected", actual.isNotEmpty() && actual == expected)
            checked++
        }
        assumeTrue("no signed APKs", checked > 0)
    }

    /** An APK stored uncompressed in a bundle is read in place, like a file of its own. */
    @Test
    fun storedApkInsideBundle() {
        val apk = apks().firstOrNull { verifiedCerts(it) != null } ?: return
        val bundle = tmp.newFile("app.apks")
        val bytes = apk.readBytes()
        ZipOutputStream(bundle.outputStream()).use { z ->
            z.putNextEntry(ZipEntry("toc.pb"))
            z.write(ByteArray(100) { 1 })
            z.closeEntry()
            z.putNextEntry(ZipEntry("splits/base-master.apk").apply {
                method = ZipEntry.STORED
                size = bytes.size.toLong()
                crc = CRC32().apply { update(bytes) }.value
            })
            z.write(bytes)
            z.closeEntry()
        }
        open(bundle).use { outer ->
            val entry = outer.getEntry("splits/base-master.apk")!!
            outer.openNested(entry).use { nested ->
                assertEquals(verifiedCerts(apk), ApkSignatures.fromZip(nested))
                val slim = tmp.newFile("nested.slim")
                SlimApk.write(nested, slim)
                assertSlimMatches(apk, slim)
            }
            // The outer zip is still usable after the nested one is closed.
            assertEquals(100, outer.getInputStream(outer.getEntry("toc.pb")!!).use { it.readBytes() }.size)
        }
    }

    /** An APK compressed in a bundle is read in one streaming pass, signers included. */
    @Test
    fun compressedApkInsideBundle() {
        for (apk in apks()) {
            val expected = verifiedCerts(apk) ?: continue
            val bundle = tmp.newFile()
            ZipOutputStream(bundle.outputStream()).use { z ->
                z.putNextEntry(ZipEntry("base.apk"))
                apk.inputStream().use { it.copyTo(z) }
                z.closeEntry()
            }
            open(bundle).use { outer ->
                val slim = tmp.newFile()
                val certs = outer.getInputStream(outer.getEntry("base.apk")!!).use { SlimApk.stream(it, slim) }
                assertEquals(apk.name, expected, certs)
                assertSlimMatches(apk, slim)
            }
        }
    }
}
