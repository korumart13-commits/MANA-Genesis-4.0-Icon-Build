package com.mana.os.integration

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.spec.GCMParameterSpec

/**
 * Stores only an encrypted, revocable MANA device token. AES key material remains in Android
 * Keystore and is not exportable through this API. Call provision only after explicit owner
 * approval of a trusted device-enrollment flow.
 */
class ManaAndroidGatewayCredentialStore(context: Context) : ManaGatewayCredentialProvider {
    private val appContext = context.applicationContext
    private val preferences = appContext.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)

    @Synchronized
    fun provisionAfterOwnerApproval(token: String) {
        require(token.matches(Regex("[A-Za-z0-9._~+/=-]{16,4096}"))) {
            "Invalid MANA device authorization token."
        }
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val encrypted = cipher.doFinal(token.toByteArray(Charsets.UTF_8))
        val saved = preferences.edit()
            .putString(KEY_CIPHERTEXT, Base64.encodeToString(encrypted, Base64.NO_WRAP))
            .putString(KEY_IV, Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
            .commit()
        check(saved) { "Could not persist encrypted MANA device authorization." }
    }

    @Synchronized
    override fun currentBearerToken(): String? {
        val ciphertext = preferences.getString(KEY_CIPHERTEXT, null) ?: return null
        val iv = preferences.getString(KEY_IV, null) ?: return null
        return runCatching {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(
                Cipher.DECRYPT_MODE,
                getExistingKey() ?: return null,
                GCMParameterSpec(128, Base64.decode(iv, Base64.NO_WRAP))
            )
            String(cipher.doFinal(Base64.decode(ciphertext, Base64.NO_WRAP)), Charsets.UTF_8)
                .takeIf { it.matches(Regex("[A-Za-z0-9._~+/=-]{16,4096}")) }
        }.getOrNull()
    }

    /** Removes the encrypted token and Keystore key so the credential cannot be reused locally. */
    @Synchronized
    fun revoke() {
        preferences.edit().remove(KEY_CIPHERTEXT).remove(KEY_IV).commit()
        runCatching {
            val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE)
            keyStore.load(null)
            if (keyStore.containsAlias(KEY_ALIAS)) keyStore.deleteEntry(KEY_ALIAS)
        }
    }

    private fun getExistingKey(): java.security.Key? {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE)
        keyStore.load(null)
        return if (keyStore.containsAlias(KEY_ALIAS)) keyStore.getKey(KEY_ALIAS, null) else null
    }

    private fun getOrCreateKey(): java.security.Key {
        getExistingKey()?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build()
        )
        return generator.generateKey()
    }

    private companion object {
        const val PREFERENCES = "mana_gateway_credentials"
        const val KEY_ALIAS = "mana_gateway_device_token_v1"
        const val KEY_CIPHERTEXT = "device_token_ciphertext"
        const val KEY_IV = "device_token_iv"
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
    }
}
