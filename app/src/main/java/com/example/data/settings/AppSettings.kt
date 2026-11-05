package com.example.data.settings

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.example.model.MonitoringSettings
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.json.JSONObject

data class ConnectionSettings(
    val url: String = "",
    val token: String = "",
    val personalKey: String = "",
    val model: String = "gemini-3.8-flash",
) {
    val configured: Boolean
        get() = (url.startsWith("https://") && token.isNotBlank()) || personalKey.isNotBlank()
}

/** Tokens are encrypted with a non-exportable Android Keystore key. No API key is bundled. */
class AppSettings(private val context: Context) {
    private val prefs = context.getSharedPreferences("private_settings", Context.MODE_PRIVATE)

    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        return (store.getKey("ltm_connection", null) as? SecretKey)
            ?: KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
                .apply {
                    init(
                        KeyGenParameterSpec.Builder(
                                "ltm_connection",
                                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                            )
                            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                            .build()
                    )
                }
                .generateKey()
    }

    private fun decrypt(name: String = "token"): String =
        runCatching {
                val parts = prefs.getString(name, "")!!.split(":")
                if (parts.size != 2) ""
                else
                    Cipher.getInstance("AES/GCM/NoPadding").run {
                        init(
                            Cipher.DECRYPT_MODE,
                            key(),
                            GCMParameterSpec(128, Base64.decode(parts[0], Base64.NO_WRAP)),
                        )
                        String(doFinal(Base64.decode(parts[1], Base64.NO_WRAP)), Charsets.UTF_8)
                    }
            }
            .getOrDefault("")

    private val _connection =
        MutableStateFlow(
            ConnectionSettings(
                prefs.getString("url", "") ?: "",
                decrypt(),
                decrypt("personal_key").ifBlank { legacyKey() },
                prefs.getString("model", null)
                    ?: context
                        .getSharedPreferences("settings", Context.MODE_PRIVATE)
                        .getString("model", "gemini-3.8-flash")
                    ?: "gemini-3.8-flash",
            )
        )
    val connection = _connection.asStateFlow()
    private val _monitoring =
        MutableStateFlow(
            runCatching {
                    val o = JSONObject(prefs.getString("monitoring", "{}")!!)
                    MonitoringSettings(
                        sampleRateSeconds = o.optInt("sampleRate", 8).coerceIn(3, 30),
                        alertOnlyFalse = o.optBoolean("onlyFalse", false),
                        alertMisleading = o.optBoolean("misleading", true),
                        alertInsufficientEvidence = o.optBoolean("insufficient", false),
                        hapticFeedback = o.optBoolean("haptic", true),
                        continuousMonitoring = o.optBoolean("continuous", false),
                        reduceTransparency = o.optBoolean("opaque", false),
                        reduceMotion = o.optBoolean("reduceMotion", false),
                    )
                }
                .getOrDefault(MonitoringSettings())
        )
    val monitoring = _monitoring.asStateFlow()

    fun saveConnection(url: String, token: String) {
        val parsed = url.trim().toHttpUrlOrNull()
        require(
            parsed != null &&
                parsed.isHttps &&
                parsed.username.isEmpty() &&
                parsed.password.isEmpty() &&
                parsed.query == null &&
                parsed.fragment == null
        ) {
            "Nhập địa chỉ máy chủ HTTPS hợp lệ, không kèm mã trong URL."
        }
        require(token.isNotBlank()) { "Nhập mã kết nối." }
        val cipher =
            Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key()) }
        val encrypted =
            Base64.encodeToString(cipher.iv, Base64.NO_WRAP) +
                ":" +
                Base64.encodeToString(cipher.doFinal(token.trim().toByteArray()), Base64.NO_WRAP)
        check(
            prefs
                .edit()
                .putString("url", url.trim().trimEnd('/'))
                .putString("token", encrypted)
                .commit()
        ) {
            "Không lưu được kết nối."
        }
        _connection.value =
            _connection.value.copy(url = url.trim().trimEnd('/'), token = token.trim())
    }

    private fun legacyKey(): String =
        runCatching {
                val legacy = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
                val encrypted = legacy.getString("key", "").orEmpty()
                if (encrypted.isBlank()) ""
                else {
                    val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
                    val secret = store.getKey("ltm-gemini", null) as SecretKey
                    Cipher.getInstance("AES/GCM/NoPadding").run {
                        init(
                            Cipher.DECRYPT_MODE,
                            secret,
                            GCMParameterSpec(
                                128,
                                Base64.decode(legacy.getString("iv", "").orEmpty(), Base64.NO_WRAP),
                            ),
                        )
                        String(doFinal(Base64.decode(encrypted, Base64.NO_WRAP)), Charsets.UTF_8)
                    }
                }
            }
            .getOrDefault("")

    fun savePersonalKey(value: String, model: String) {
        require(value.isNotBlank()) { "Nhập khóa Gemini API cá nhân." }
        require(Regex("[a-zA-Z0-9.-]+").matches(model.trim())) { "Tên mô hình không hợp lệ." }
        val cipher =
            Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key()) }
        val encrypted =
            Base64.encodeToString(cipher.iv, Base64.NO_WRAP) +
                ":" +
                Base64.encodeToString(cipher.doFinal(value.trim().toByteArray()), Base64.NO_WRAP)
        check(
            prefs
                .edit()
                .putString("personal_key", encrypted)
                .putString("model", model.trim())
                .putString("url", "")
                .remove("token")
                .commit()
        ) {
            "Không lưu được khóa cá nhân."
        }
        _connection.value = ConnectionSettings(personalKey = value.trim(), model = model.trim())
    }

    @Synchronized
    fun reservePersonalRequest() {
        val today =
            java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date())
        val count =
            if (prefs.getString("budget_day", "") == today) prefs.getInt("budget_used", 0) else 0
        check(count < 100) { "Đã đạt giới hạn 100 yêu cầu cá nhân trong ngày." }
        check(
            prefs.edit().putString("budget_day", today).putInt("budget_used", count + 1).commit()
        ) {
            "Không cập nhật được hạn mức."
        }
    }

    fun saveMonitoring(s: MonitoringSettings) {
        val json =
            JSONObject()
                .put("sampleRate", s.sampleRateSeconds)
                .put("onlyFalse", s.alertOnlyFalse)
                .put("misleading", s.alertMisleading)
                .put("insufficient", s.alertInsufficientEvidence)
                .put("haptic", s.hapticFeedback)
                .put("continuous", s.continuousMonitoring)
                .put("opaque", s.reduceTransparency)
                .put("reduceMotion", s.reduceMotion)
        prefs.edit().putString("monitoring", json.toString()).apply()
        _monitoring.value = s
    }
}
