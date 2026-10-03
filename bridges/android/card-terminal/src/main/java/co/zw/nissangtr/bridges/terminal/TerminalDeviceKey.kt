package co.zw.nissangtr.bridges.terminal

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.Signature
import java.util.Base64

/**
 * This device's card-terminal evidence key: RSA 2048 in the Android Keystore (hardware-backed where
 * available, never exportable). An admin pairs the device by registering its public key
 * (`register_pos_card_terminal_device_key`); the server then only accepts terminal results this
 * device signed (`card-terminal-result`, RSASSA-PKCS1-v1_5 / SHA-256).
 */
class TerminalDeviceKey(private val alias: String = "gtr-card-terminal-evidence-v1") {
    private val store: KeyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }

    private fun ensure() {
        if (store.containsAlias(alias)) return
        val gen = KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_RSA, "AndroidKeyStore")
        gen.initialize(
            KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_SIGN)
                .setKeySize(2048)
                .setDigests(KeyProperties.DIGEST_SHA256)
                .setSignaturePaddings(KeyProperties.SIGNATURE_PADDING_RSA_PKCS1)
                .build(),
        )
        gen.generateKeyPair()
    }

    /** X.509 SubjectPublicKeyInfo (DER), base64 — what the server stores. */
    fun publicKeySpkiBase64(): String {
        ensure()
        return Base64.getEncoder().encodeToString(store.getCertificate(alias).publicKey.encoded)
    }

    fun publicKeySha256Hex(): String {
        ensure()
        return MessageDigest.getInstance("SHA-256").digest(store.getCertificate(alias).publicKey.encoded)
            .joinToString("") { "%02x".format(it) }
    }

    fun signBase64(message: ByteArray): String {
        ensure()
        val key = store.getKey(alias, null) as PrivateKey
        val sig = Signature.getInstance("SHA256withRSA").apply { initSign(key); update(message) }
        return Base64.getEncoder().encodeToString(sig.sign())
    }
}
