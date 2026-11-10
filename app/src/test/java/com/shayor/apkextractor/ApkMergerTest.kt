package com.shayor.apkextractor

import com.shayor.apkextractor.data.ApkMerger
import com.shayor.apkextractor.data.SigningKey
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.security.KeyStore
import java.security.PrivateKey
import java.security.cert.X509Certificate
import java.util.zip.ZipFile

/**
 * Merges real split apps pulled from a device. Skipped unless run with:
 *   ./gradlew testDebugUnitTest -PsplitsDir=<dir with one folder of APKs per app>
 *       -PmergeOut=<output dir> -PtestKeystore=<PKCS12, password "android">
 */
class ApkMergerTest {

    @Test
    fun mergesRealSplitApps() {
        val splitsDir = System.getProperty("splitsDir")?.let(::File)
        val outDir = System.getProperty("mergeOut")?.let(::File)
        val keystore = System.getProperty("testKeystore")?.let(::File)
        assumeTrue(splitsDir?.isDirectory == true && outDir != null && keystore?.isFile == true)

        val ks = KeyStore.getInstance("PKCS12").apply { keystore!!.inputStream().use { load(it, PASSWORD) } }
        val alias = ks.aliases().nextElement()
        val key = SigningKey(ks.getKey(alias, PASSWORD) as PrivateKey, ks.getCertificate(alias) as X509Certificate)
        val merger = ApkMerger { key }

        outDir!!.mkdirs()
        val apps = splitsDir!!.listFiles { f -> f.isDirectory }.orEmpty()
        assumeTrue(apps.isNotEmpty())
        for (app in apps) {
            val apks = app.listFiles { f -> f.name.endsWith(".apk") }.orEmpty()
                .sortedBy { if (it.name == "base.apk") "" else it.name }
            val started = System.currentTimeMillis()
            val merged = merger.merge(apks, File(outDir, "work-${app.name}"))
            val target = File(outDir, "${app.name}.apk")
            target.delete()
            assertTrue(merged.renameTo(target))
            println("${app.name}: ${apks.size} APKs -> ${target.length() / 1024} KB in ${System.currentTimeMillis() - started} ms")

            ZipFile(target).use { zip ->
                val names = zip.entries().asSequence().map { it.name }.toSet()
                assertTrue("missing manifest", "AndroidManifest.xml" in names)
                assertTrue("missing resources.arsc", "resources.arsc" in names)
                // Native libraries from the ABI split must have been merged in.
                val abiSplit = apks.any { it.name.contains("arm64") || it.name.contains("armeabi") || it.name.contains("x86") }
                if (abiSplit) assertTrue("native libs missing", names.any { it.startsWith("lib/") })
            }
        }
    }

    private companion object {
        val PASSWORD = "android".toCharArray()
    }
}
