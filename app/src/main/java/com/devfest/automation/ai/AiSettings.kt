package com.devfest.automation.ai

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

data class AiConfig(val baseUrl: String, val model: String, val apiKey: String) {
    val isComplete: Boolean get() = baseUrl.isNotBlank() && model.isNotBlank() && apiKey.isNotBlank()
}

data class AiPreset(val name: String, val baseUrl: String, val suggestedModel: String, val keyUrl: String)

/**
 * Base URL and model in plain prefs; the API key encrypted with an Android Keystore key.
 * The prefs file is excluded from backup because the Keystore key never leaves the phone.
 */
class AiSettings(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun load(): AiConfig = AiConfig(
        baseUrl = prefs.getString(KEY_BASE_URL, null) ?: PRESETS.first().baseUrl,
        model = prefs.getString(KEY_MODEL, null) ?: PRESETS.first().suggestedModel,
        apiKey = prefs.getString(KEY_API_KEY, null)?.let(::decrypt).orEmpty()
    )

    fun save(config: AiConfig) {
        prefs.edit()
            .putString(KEY_BASE_URL, config.baseUrl.trim().trimEnd('/'))
            .putString(KEY_MODEL, config.model.trim())
            .putString(KEY_API_KEY, config.apiKey.trim().takeIf { it.isNotEmpty() }?.let(::encrypt))
            .apply()
    }

    private fun key(): SecretKey {
        val store = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        (store.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE).apply {
            init(
                KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .build()
            )
        }.generateKey()
    }

    private fun encrypt(plain: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, key()) }
        val sealed = cipher.iv + cipher.doFinal(plain.toByteArray())
        return Base64.encodeToString(sealed, Base64.NO_WRAP)
    }

    /** Returns null if the key was lost (for example after a restore), so the owner re-enters it. */
    private fun decrypt(encoded: String): String? = runCatching {
        val sealed = Base64.decode(encoded, Base64.NO_WRAP)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, sealed, 0, IV_BYTES))
        String(cipher.doFinal(sealed, IV_BYTES, sealed.size - IV_BYTES))
    }.getOrNull()

    companion object {
        const val PREFS = "ai_settings"
        private const val KEY_BASE_URL = "base_url"
        private const val KEY_MODEL = "model"
        private const val KEY_API_KEY = "api_key"
        private const val KEYSTORE = "AndroidKeyStore"
        private const val KEY_ALIAS = "ai_api_key"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val IV_BYTES = 12

        val PRESETS = listOf(
            AiPreset("Groq", "https://api.groq.com/openai/v1", "openai/gpt-oss-120b", "https://console.groq.com/keys"),
            AiPreset("OpenAI", "https://api.openai.com/v1", "gpt-5-mini", "https://platform.openai.com/api-keys")
        )
    }
}
