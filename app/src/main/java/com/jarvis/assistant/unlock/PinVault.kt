package com.jarvis.assistant.unlock

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.util.Log
import java.security.KeyStore
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Keeps the phone PIN encrypted with a key that lives in the Android Keystore (the key cannot be exported).
 * The secret phrase is only stored as a salted hash. Nothing here is ever sent over the network.
 */
object PinVault {
    private const val TAG = "PinVault"
    private const val PREFS = "jarvis_unlock"
    private const val ALIAS = "jarvis_unlock_key"

    private fun sp(ctx: Context) = ctx.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun key(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore")
        ks.load(null)
        val existing = ks.getKey(ALIAS, null)
        if (existing is SecretKey) return existing
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        gen.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return gen.generateKey()
    }

    // ---------------------------------------------------------------- PIN
    fun savePin(ctx: Context, pin: String): Boolean {
        return try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, key())
            val iv = cipher.iv
            val enc = cipher.doFinal(pin.toByteArray(Charsets.UTF_8))
            sp(ctx).edit()
                .putString("pin_iv", Base64.encodeToString(iv, Base64.NO_WRAP))
                .putString("pin_enc", Base64.encodeToString(enc, Base64.NO_WRAP))
                .apply()
            true
        } catch (e: Exception) {
            Log.e(TAG, "savePin failed: ${e.message}")
            false
        }
    }

    fun loadPin(ctx: Context): String? {
        return try {
            val iv = Base64.decode(sp(ctx).getString("pin_iv", null) ?: return null, Base64.NO_WRAP)
            val enc = Base64.decode(sp(ctx).getString("pin_enc", null) ?: return null, Base64.NO_WRAP)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, iv))
            String(cipher.doFinal(enc), Charsets.UTF_8)
        } catch (e: Exception) {
            Log.e(TAG, "loadPin failed: ${e.message}")
            null
        }
    }

    fun hasPin(ctx: Context): Boolean = sp(ctx).getString("pin_enc", null) != null

    fun clearPin(ctx: Context) {
        sp(ctx).edit().remove("pin_iv").remove("pin_enc").putBoolean("enabled", false).putBoolean("auto", false).apply()
    }

    // ---------------------------------------------------------------- secret phrase (optional)
    private fun hash(phrase: String, salt: ByteArray): ByteArray {
        val md = MessageDigest.getInstance("SHA-256")
        md.update(salt)
        md.update(phrase.trim().lowercase().toByteArray(Charsets.UTF_8))
        return md.digest()
    }

    fun savePhrase(ctx: Context, phrase: String) {
        if (phrase.isBlank()) {
            sp(ctx).edit().remove("phrase_hash").remove("phrase_salt").apply()
            return
        }
        val salt = ByteArray(16)
        SecureRandom().nextBytes(salt)
        sp(ctx).edit()
            .putString("phrase_salt", Base64.encodeToString(salt, Base64.NO_WRAP))
            .putString("phrase_hash", Base64.encodeToString(hash(phrase, salt), Base64.NO_WRAP))
            .apply()
    }

    fun hasPhrase(ctx: Context): Boolean = sp(ctx).getString("phrase_hash", null) != null

    fun checkPhrase(ctx: Context, phrase: String): Boolean {
        return try {
            val salt = Base64.decode(sp(ctx).getString("phrase_salt", null) ?: return false, Base64.NO_WRAP)
            val expected = Base64.decode(sp(ctx).getString("phrase_hash", null) ?: return false, Base64.NO_WRAP)
            MessageDigest.isEqual(expected, hash(phrase, salt))
        } catch (e: Exception) {
            false
        }
    }

    // ---------------------------------------------------------------- switches
    fun isEnabled(ctx: Context): Boolean = sp(ctx).getBoolean("enabled", false) && hasPin(ctx)
    fun setEnabled(ctx: Context, on: Boolean) {
        sp(ctx).edit().putBoolean("enabled", on).apply()
    }

    fun isAuto(ctx: Context): Boolean = isEnabled(ctx) && sp(ctx).getBoolean("auto", false)
    fun setAuto(ctx: Context, on: Boolean) {
        sp(ctx).edit().putBoolean("auto", on).apply()
    }

    // ---------------------------------------------------------------- wrong-phrase limiter
    fun registerFailure(ctx: Context) {
        val now = System.currentTimeMillis()
        val since = sp(ctx).getLong("fail_since", 0L)
        var count = sp(ctx).getInt("fail_count", 0)
        if (now - since > 10 * 60_000L) count = 0
        sp(ctx).edit().putInt("fail_count", count + 1).putLong("fail_since", if (count == 0) now else since).apply()
    }

    fun isLockedOut(ctx: Context): Boolean {
        val now = System.currentTimeMillis()
        val since = sp(ctx).getLong("fail_since", 0L)
        return sp(ctx).getInt("fail_count", 0) >= 3 && now - since <= 10 * 60_000L
    }

    fun resetFailures(ctx: Context) {
        sp(ctx).edit().putInt("fail_count", 0).apply()
    }
}
