package com.shayor.apkextractor.data

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.math.BigInteger
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.cert.X509Certificate
import java.util.Date
import javax.security.auth.x500.X500Principal

/**
 * A per-device signing key kept in the Android Keystore (it never leaves secure hardware).
 *
 * Merged APKs can't keep the developer's signature, so they are re-signed with this key.
 * Using one stable key means a newer merged version of an app can be installed as an
 * update over an older merged version from this device.
 */
object DeviceSigningKey {

    private const val ALIAS = "apk_extractor_resign"
    private const val PROVIDER = "AndroidKeyStore"
    private const val DAY_MS = 24L * 60 * 60 * 1000

    @Synchronized
    fun get(): SigningKey {
        val keyStore = KeyStore.getInstance(PROVIDER).apply { load(null) }
        if (!keyStore.containsAlias(ALIAS)) generate()
        val entry = keyStore.getEntry(ALIAS, null) as? KeyStore.PrivateKeyEntry
            ?: throw IllegalStateException("Signing key is unavailable")
        return SigningKey(entry.privateKey, entry.certificate as X509Certificate)
    }

    private fun generate() {
        val now = System.currentTimeMillis()
        val spec = KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_SIGN)
            .setKeySize(2048)
            // SHA-1 is only used for v1 signatures of apps that still support Android < 4.3.
            .setDigests(KeyProperties.DIGEST_SHA1, KeyProperties.DIGEST_SHA256, KeyProperties.DIGEST_SHA512)
            .setSignaturePaddings(KeyProperties.SIGNATURE_PADDING_RSA_PKCS1)
            .setCertificateSubject(X500Principal("CN=APK Extractor, O=Re-signed on device"))
            .setCertificateSerialNumber(BigInteger.valueOf(now))
            .setCertificateNotBefore(Date(now - DAY_MS))
            .setCertificateNotAfter(Date(now + 30 * 365 * DAY_MS))
            .build()
        KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_RSA, PROVIDER).apply {
            initialize(spec)
            generateKeyPair()
        }
    }
}
