package com.shayor.apkextractor.data

object FileNames {
    private val ILLEGAL = Regex("[\\\\/:*?\"<>|\\p{Cntrl}]")
    private val WHITESPACE = Regex("\\s+")

    /** Max bytes for the base name; leaves room for an extension and " (99)" suffixes within 255 bytes. */
    private const val MAX_BYTES = 180

    fun build(app: AppInfo, format: NameFormat, extension: String): String =
        build(app.label, app.packageName, app.displayVersion, format, extension)

    fun build(label: String, packageName: String, version: String, format: NameFormat, extension: String): String {
        val v = version.trim().let { if (it.startsWith("v", ignoreCase = true)) it else "v$it" }
        val raw = when (format) {
            NameFormat.LABEL_VERSION -> "${label}_$v"
            NameFormat.PACKAGE_VERSION -> "${packageName}_$v"
            NameFormat.LABEL_PACKAGE_VERSION -> "${label}_${packageName}_$v"
        }
        return sanitize(raw, fallback = sanitize(packageName, "app")) + "." + extension
    }

    /** Makes [input] safe to use as a file name on any storage provider (FAT/exFAT SD cards included). */
    fun sanitize(input: String, fallback: String): String {
        // Collapse whitespace (incl. tabs/newlines in labels) first, then replace what's left.
        val cleaned = ILLEGAL.replace(input.replace(WHITESPACE, " "), "_")
            .trim()
            .trim('.', ' ') // leading dots create hidden files; trailing dots break FAT
        val name = cleaned.ifEmpty { fallback }
        return truncateUtf8(name, MAX_BYTES)
    }

    /** Truncates without splitting a multi-byte character or surrogate pair. */
    internal fun truncateUtf8(s: String, maxBytes: Int): String {
        var bytes = 0
        var i = 0
        while (i < s.length) {
            val cp = s.codePointAt(i)
            val len = when {
                cp < 0x80 -> 1
                cp < 0x800 -> 2
                cp < 0x10000 -> 3
                else -> 4
            }
            if (bytes + len > maxBytes) return s.substring(0, i).trimEnd('.', ' ', '_')
            bytes += len
            i += Character.charCount(cp)
        }
        return s
    }
}
