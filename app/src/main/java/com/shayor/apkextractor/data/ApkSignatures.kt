package com.shayor.apkextractor.data

import java.io.ByteArrayInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import java.security.cert.CertificateFactory

/**
 * Who signed an APK, read straight from its signature data without verifying it: the APK Signing
 * Block (schemes v2, v3, v3.1, a few KB just before the central directory), or failing that the
 * v1 PKCS#7 file in META-INF. Verifying would mean hashing the whole APK; here only the signer's
 * certificates are wanted, to warn before installing over an app signed with another key. The
 * system still verifies the APK when it installs it.
 *
 * Certificates are returned as SHA-256 hex digests of their DER encoding, the same form as
 * [ApkInstaller]'s digests of installed apps' signatures. A v3 key-rotation history is included,
 * so an update signed with a rotated key still matches the app it replaces.
 */
internal object ApkSignatures {

    /** Reads [length] bytes at [position] of the APK. */
    fun interface Reader {
        fun read(position: Long, length: Int): ByteBuffer
    }

    fun fromZip(zip: ChannelZip): Set<String> =
        runCatching { fromSigningBlock(zip::readAt, zip.centralDirectoryOffset) }.getOrNull()?.ifEmpty { null }
            ?: v1(zip)

    /** Certificates from the APK Signing Block, or empty if there is none. */
    fun fromSigningBlock(reader: Reader, cdOffset: Long): Set<String> {
        if (cdOffset < FOOTER + 8) return emptySet()
        val footer = reader.read(cdOffset - FOOTER, FOOTER).order(ByteOrder.LITTLE_ENDIAN)
        if (footer.getLong(8) != MAGIC_LO || footer.getLong(16) != MAGIC_HI) return emptySet()
        // The size counts everything after the leading size field, footer included.
        val size = footer.getLong(0)
        if (size < FOOTER || size > MAX_BLOCK || cdOffset - size - 8 < 0) return emptySet()
        val pairs = reader.read(cdOffset - size, (size - FOOTER).toInt()).order(ByteOrder.LITTLE_ENDIAN)
        val certs = HashSet<String>()
        while (pairs.remaining() >= 12) {
            val len = pairs.long
            if (len < 4 || len > pairs.remaining()) break
            val id = pairs.int
            val value = slice(pairs, (len - 4).toInt())
            runCatching {
                when (id) {
                    V2 -> signers(value, v3 = false, certs)
                    V3, V31 -> signers(value, v3 = true, certs)
                }
            }
        }
        return certs
    }

    /** v1 (JAR) signing: the certificates in META-INF/CERT.RSA, .DSA or .EC. */
    private fun v1(zip: ChannelZip): Set<String> = runCatching {
        fromV1Files(zip.entries.filter { isV1Signature(it.name) }.map { e -> zip.getInputStream(e).use { it.readBytes() } })
    }.getOrDefault(emptySet())

    /** v1 certificates from the bytes of META-INF/CERT.RSA (etc.) files. */
    fun fromV1Files(files: List<ByteArray>): Set<String> = runCatching {
        val factory = CertificateFactory.getInstance("X.509")
        files.flatMapTo(HashSet()) { bytes -> factory.generateCertificates(ByteArrayInputStream(bytes)).map { sha256(it.encoded) } }
    }.getOrDefault(emptySet())

    fun isV1Signature(name: String): Boolean {
        if (!name.startsWith("META-INF/") || name.indexOf('/', 9) >= 0) return false
        val upper = name.uppercase()
        return upper.endsWith(".RSA") || upper.endsWith(".DSA") || upper.endsWith(".EC")
    }

    /**
     * A scheme block: length-prefixed signers, each holding length-prefixed signed data, which
     * starts with the digests and then the certificates. v3 signed data then carries the SDK range
     * and attributes, among them the key-rotation lineage.
     */
    private fun signers(block: ByteBuffer, v3: Boolean, out: MutableSet<String>) {
        val signers = prefixed(block)
        while (signers.hasRemaining()) {
            val signer = prefixed(signers)
            val signedData = prefixed(signer)
            prefixed(signedData) // digests
            val certs = prefixed(signedData)
            while (certs.hasRemaining()) out += sha256(bytes(prefixed(certs)))
            if (!v3) continue
            signedData.int // min SDK
            signedData.int // max SDK
            val attributes = prefixed(signedData)
            while (attributes.hasRemaining()) {
                val attribute = prefixed(attributes)
                if (attribute.int == LINEAGE) lineage(attribute, out)
            }
        }
    }

    /** Every certificate the app has been signed with, oldest first (v3 proof-of-rotation). */
    private fun lineage(value: ByteBuffer, out: MutableSet<String>) {
        value.int // version
        while (value.hasRemaining()) {
            val node = prefixed(value)
            val signedData = prefixed(node)
            out += sha256(bytes(prefixed(signedData)))
        }
    }

    private fun prefixed(buffer: ByteBuffer): ByteBuffer {
        val len = buffer.int
        if (len < 0 || len > buffer.remaining()) throw IllegalArgumentException("Bad length")
        return slice(buffer, len)
    }

    private fun slice(buffer: ByteBuffer, len: Int): ByteBuffer {
        val slice = buffer.slice().order(ByteOrder.LITTLE_ENDIAN)
        slice.limit(len)
        buffer.position(buffer.position() + len)
        return slice
    }

    private fun bytes(buffer: ByteBuffer) = ByteArray(buffer.remaining()).also { buffer.get(it) }

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    private const val FOOTER = 24
    private const val MAX_BLOCK = 64L * 1024 * 1024
    private const val MAGIC_LO = 0x20676953204b5041L // "APK Sig "
    private const val MAGIC_HI = 0x3234206b636f6c42L // "Block 42"
    private const val V2 = 0x7109871a
    private const val V3 = 0xf05368c0.toInt()
    private const val V31 = 0x1b93ad61
    private const val LINEAGE = 0x3ba06f8c
}
