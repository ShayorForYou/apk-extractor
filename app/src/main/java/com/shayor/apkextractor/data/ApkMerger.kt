package com.shayor.apkextractor.data

import com.android.apksig.ApkSigner
import com.reandroid.apk.ApkBundle
import com.reandroid.apk.ApkModule
import com.reandroid.app.AndroidManifest
import com.reandroid.arsc.chunk.TableBlock
import com.reandroid.arsc.chunk.xml.ResXmlElement
import java.io.File
import java.security.PrivateKey
import java.security.cert.X509Certificate
import java.util.regex.Pattern

/** Key + certificate used to re-sign merged APKs. */
class SigningKey(val privateKey: PrivateKey, val certificate: X509Certificate)

/**
 * Turns a split app (base.apk + split_config.*.apk) into one standalone, installable APK:
 *
 *  1. merges every split's files and resource table into the base,
 *  2. removes the manifest flags that make Android refuse to install the base without its splits,
 *  3. strips the original (now invalid) signatures,
 *  4. zip-aligns and re-signs with v1 + v2 + v3 signatures.
 *
 * Pure JVM code (no Android APIs) so it can be unit tested on the host.
 */
class ApkMerger(private val keyProvider: () -> SigningKey) {

    /**
     * @param apks base APK first, then its splits.
     * @param onStep called as each of the [STEPS] steps starts (1-based), with a short label for it.
     * @return the signed APK inside [workDir]; the caller owns (and deletes) it.
     */
    fun merge(apks: List<File>, workDir: File, onStep: (step: Int, label: String) -> Unit = { _, _ -> }): File {
        require(apks.isNotEmpty())
        workDir.mkdirs()
        val unsigned = File(workDir, "merged-unsigned.apk")
        val signed = File(workDir, "merged.apk")
        unsigned.delete()
        signed.delete()
        try {
            writeMerged(apks, unsigned, onStep)
            onStep(4, "Signing")
            sign(unsigned, signed)
            return signed
        } catch (t: Throwable) {
            signed.delete()
            throw t
        } finally {
            unsigned.delete()
        }
    }

    private fun writeMerged(apks: List<File>, out: File, onStep: (Int, String) -> Unit) {
        val bundle = ApkBundle()
        try {
            apks.forEachIndexed { i, file ->
                onStep(1, "Reading part ${i + 1} of ${apks.size}")
                // Module names must be unique; file names are (base.apk, split_config.xx.apk).
                val name = file.name.removeSuffix(".apk").ifEmpty { "module$i" }
                val module = ApkModule.loadApkFile(file, name)
                // Merging works on resource IDs; ARSCLib merges with frameworks disabled itself.
                // Not loading them lets the app ship without ARSCLib's bundled android.jar tables.
                module.setLoadDefaultFramework(false)
                if (module.hasTableBlock()) densify(module.tableBlock)
                bundle.addModule(module)
            }
            onStep(2, "Combining resources")
            val merged = bundle.mergeModules()
            try {
                if (merged.hasTableBlock()) densify(merged.tableBlock)
                removeSignatures(merged)
                sanitizeManifest(merged)
                merged.refreshTable()
                merged.refreshManifest()
                onStep(3, "Building APK")
                merged.writeApk(out)
            } finally {
                runCatching { merged.close() }
            }
        } finally {
            runCatching { bundle.close() }
        }
    }

    /**
     * Modern aapt2 builds (Google apps, LinkedIn, …) encode resource types as *sparse* or with
     * 16-bit offsets to save space. ARSCLib reads those fine but corrupts them when it writes a
     * merged table ("Entry … is too small" → the app crashes on launch, e.g. missing themes).
     * Converting every such type to the classic dense layout first avoids that path entirely;
     * dense tables are what every Android version understands.
     */
    private fun densify(table: TableBlock) {
        for (pkg in table.packages.asSequence().toList()) {
            for (pair in pkg.listSpecTypePairs().toList()) {
                val array = pair.typeBlockArray
                val compact = array.listItems().filter { it != null && (it.isSparse || it.isOffset16) }
                if (compact.isEmpty()) continue
                val entryCount = maxOf(pair.highestEntryCount, pair.specBlock?.entryCount ?: 0)
                for (block in compact) {
                    val dense = array.getOrCreate(block.resConfig, false, false)
                    check(dense !== block && !dense.isSparse && !dense.isOffset16) {
                        "Couldn't convert sparse resources for ${pair.typeName}"
                    }
                    dense.ensureEntriesCount(entryCount)
                    // TypeBlock.merge() copies by position, which scrambles sparse entries.
                    // Copy each entry to its real resource index instead.
                    for (entry in block.listEntries(true)) {
                        if (entry == null || entry.isNull) continue
                        dense.getOrCreateEntry(entry.id.toShort()).merge(entry)
                    }
                    block.clear()
                }
                array.removeEmptyBlocks()
            }
        }
    }

    private fun removeSignatures(module: ApkModule) {
        val entries = module.zipEntryMap
        entries.removeIf(SIGNATURE_FILES)
        entries.remove("stamp-cert-sha256")
        module.setApkSignatureBlock(null)
    }

    /** Removes everything that tells Android "this APK needs its splits to install". */
    private fun sanitizeManifest(module: ApkModule) {
        if (!module.hasAndroidManifest()) return
        val manifest = module.androidManifest
        val root = manifest.manifestElement ?: return
        val application = manifest.applicationElement

        val ids = intArrayOf(
            AndroidManifest.ID_isSplitRequired,
            AndroidManifest.ID_requiredSplitTypes,
            AndroidManifest.ID_splitTypes,
            // Split APKs usually ship native libs uncompressed + page-aligned with
            // extractNativeLibs=false. Dropping it (default = true) lets the installer
            // extract them, which works regardless of how the merged zip stores them.
            AndroidManifest.ID_extractNativeLibs,
        )
        val names = arrayOf(
            AndroidManifest.NAME_isSplitRequired,
            AndroidManifest.NAME_requiredSplitTypes,
            AndroidManifest.NAME_splitTypes,
        )
        for (element in listOfNotNull(root, application)) {
            ids.forEach { element.removeAttributesWithId(it) }
            names.forEach { element.removeAttributesWithName(it) }
        }
        // Play Store split metadata (e.g. com.android.vending.splits.required) makes
        // the package installer reject the APK as an incomplete split install.
        application?.removeElements { it.equalsName(AndroidManifest.TAG_meta_data) && isSplitMetadata(it) }
        manifest.refresh()
    }

    private fun isSplitMetadata(element: ResXmlElement): Boolean {
        val name = element.searchAttributeByResourceId(AndroidManifest.ID_name)?.valueString ?: return false
        return name.startsWith("com.android.vending.") ||
            name.startsWith("com.android.stamp.") ||
            name == "com.android.dynamic.apk.fused.modules"
    }

    private fun sign(input: File, output: File) {
        val key = keyProvider()
        val signer = ApkSigner.SignerConfig.Builder("APKEXTR", key.privateKey, listOf(key.certificate)).build()
        ApkSigner.Builder(listOf(signer))
            .setInputApk(input)
            .setOutputApk(output)
            .setV1SigningEnabled(true) // Android 7.0 and older
            .setV2SigningEnabled(true)
            .setV3SigningEnabled(true)
            .setDebuggableApkPermitted(true)
            .setAlignmentPreserved(false) // (re)align uncompressed entries
            .setLibraryPageAlignmentBytes(16 * 1024) // 16 KB page-size devices
            .setCreatedBy("APK Extractor")
            .build()
            .sign()
    }

    companion object {
        /** Number of steps reported through `merge(onStep = …)`. */
        const val STEPS = 4

        private val SIGNATURE_FILES: Pattern = Pattern.compile("^META-INF/.+\\.(MF|SF|RSA|DSA|EC)$", Pattern.CASE_INSENSITIVE)
    }
}
