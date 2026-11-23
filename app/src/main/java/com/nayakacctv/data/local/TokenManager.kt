package com.nayakacctv.data.local

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.google.gson.Gson
import com.nayakacctv.data.model.Customer
import com.nayakacctv.data.model.CustomerCameraEdit
import com.nayakacctv.data.model.Recording
import java.security.MessageDigest
import java.util.Locale
import java.util.UUID

class TokenManager(context: Context) {

    private val prefs: SharedPreferences = try {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        EncryptedSharedPreferences.create(
            context,
            "nayaka_secure_prefs",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    } catch (e: Exception) {
        // Fallback for devices where keystore might throw
        context.getSharedPreferences("nayaka_standard_prefs", Context.MODE_PRIVATE)
    }

    private val gson = Gson()

    companion object {
        private const val KEY_ACCESS_TOKEN = "access_token"
        private const val KEY_REFRESH_TOKEN = "refresh_token"
        private const val KEY_INSTALLATION_ID = "installation_id"
        private const val KEY_USER_DATA = "user_data"
        private const val KEY_BIOMETRIC_ENABLED = "biometric_enabled"
        private const val KEY_BIOMETRIC_REFRESH_TOKEN = "biometric_refresh_token"
        private const val KEY_BIOMETRIC_CUSTOMER_DATA = "biometric_customer_data"
        private const val KEY_BIOMETRIC_ACCOUNT_KEYS = "biometric_account_keys"
        private const val KEY_BIOMETRIC_LAST_ACCOUNT = "biometric_last_account"
        private const val KEY_BIOMETRIC_MIGRATION_COMPLETE = "biometric_account_migration_complete"
        private const val KEY_AUTH_PROVIDER = "auth_provider"
    }

    init {
        if (!prefs.getBoolean(KEY_BIOMETRIC_MIGRATION_COMPLETE, false)) {
            prefs.edit()
                .remove(KEY_BIOMETRIC_ENABLED)
                .remove(KEY_BIOMETRIC_REFRESH_TOKEN)
                .remove(KEY_BIOMETRIC_CUSTOMER_DATA)
                .putBoolean(KEY_BIOMETRIC_MIGRATION_COMPLETE, true)
                .apply()
        }
    }

    fun getAccessToken(): String? = prefs.getString(KEY_ACCESS_TOKEN, null)

    fun getRefreshToken(): String? = prefs.getString(KEY_REFRESH_TOKEN, null)

    fun saveTokens(accessToken: String?, refreshToken: String?) {
        prefs.edit().apply {
            if (accessToken != null) putString(KEY_ACCESS_TOKEN, accessToken) else remove(KEY_ACCESS_TOKEN)
            if (refreshToken != null) {
                putString(KEY_REFRESH_TOKEN, refreshToken)
            } else {
                remove(KEY_REFRESH_TOKEN)
            }
            apply()
        }
    }

    fun saveRefreshedTokens(accessToken: String, refreshToken: String) {
        saveTokens(accessToken, refreshToken)
        val customer = getCustomer()
        if (isBiometricEnabled(customer)) {
            updateBiometricToken(refreshToken, customer)
        }
    }

    fun getInstallationId(): String {
        var id = prefs.getString(KEY_INSTALLATION_ID, null)
        if (id == null) {
            id = "android-" + UUID.randomUUID().toString()
            prefs.edit().putString(KEY_INSTALLATION_ID, id).apply()
        }
        return id
    }

    fun saveCustomer(customer: Customer?) {
        val editor = prefs.edit()
        if (customer != null) {
            val json = gson.toJson(customer)
            editor.putString(KEY_USER_DATA, json)
        } else {
            editor.remove(KEY_USER_DATA)
        }
        editor.apply()
    }

    fun getCustomer(): Customer? {
        val json = prefs.getString(KEY_USER_DATA, null) ?: return null
        return try {
            gson.fromJson(json, Customer::class.java)
        } catch (e: Exception) {
            null
        }
    }

    fun isBiometricEnabled(customer: Customer? = getCustomer()): Boolean {
        val key = biometricAccountKey(customer) ?: return false
        return isBiometricAccountEnabled(key)
    }

    fun setBiometricEnabled(enabled: Boolean) {
        val customer = getCustomer() ?: return
        val key = biometricAccountKey(customer) ?: return
        if (!enabled) {
            disableBiometric(customer)
        } else if (!prefs.getString(biometricRefreshTokenKey(key), null).isNullOrBlank()) {
            prefs.edit().putBoolean(biometricEnabledKey(key), true).apply()
        }
    }

    fun enableBiometric(refreshToken: String, customer: Customer?): Boolean {
        val key = biometricAccountKey(customer) ?: return false
        if (refreshToken.isBlank() || customer == null) return false
        val accounts = prefs.getStringSet(KEY_BIOMETRIC_ACCOUNT_KEYS, emptySet())
            .orEmpty()
            .toMutableSet()
            .apply { add(key) }
        val editor = prefs.edit()
            .putBoolean(biometricEnabledKey(key), true)
            .putString(biometricRefreshTokenKey(key), refreshToken)
            .putString(biometricCustomerDataKey(key), gson.toJson(customer))
            .putString(KEY_BIOMETRIC_LAST_ACCOUNT, key)
            .putStringSet(KEY_BIOMETRIC_ACCOUNT_KEYS, accounts)
        editor.apply()
        return true
    }

    fun disableBiometric(customer: Customer? = getCustomer()) {
        val key = biometricAccountKey(customer) ?: return
        val accounts = prefs.getStringSet(KEY_BIOMETRIC_ACCOUNT_KEYS, emptySet())
            .orEmpty()
            .toMutableSet()
        accounts.remove(key)
        val editor = prefs.edit()
            .remove(biometricEnabledKey(key))
            .remove(biometricRefreshTokenKey(key))
            .remove(biometricCustomerDataKey(key))
            .putStringSet(KEY_BIOMETRIC_ACCOUNT_KEYS, accounts)
        if (prefs.getString(KEY_BIOMETRIC_LAST_ACCOUNT, null) == key) {
            if (accounts.isEmpty()) {
                editor.remove(KEY_BIOMETRIC_LAST_ACCOUNT)
            } else {
                editor.putString(KEY_BIOMETRIC_LAST_ACCOUNT, accounts.first())
            }
        }
        editor.apply()
    }

    fun getBiometricRefreshToken(customer: Customer? = getBiometricCustomer()): String? {
        val key = biometricAccountKey(customer) ?: return null
        if (!isBiometricAccountEnabled(key)) return null
        return prefs.getString(biometricRefreshTokenKey(key), null)
    }

    fun updateBiometricToken(
        newRefreshToken: String,
        customer: Customer? = getBiometricCustomer()
    ) {
        val key = biometricAccountKey(customer) ?: return
        if (isBiometricAccountEnabled(key)) {
            prefs.edit()
                .putString(biometricRefreshTokenKey(key), newRefreshToken)
                .putString(biometricCustomerDataKey(key), gson.toJson(customer))
                .apply()
        }
    }

    fun getBiometricCustomer(): Customer? {
        val accounts = prefs.getStringSet(KEY_BIOMETRIC_ACCOUNT_KEYS, emptySet()).orEmpty()
        val lastAccount = prefs.getString(KEY_BIOMETRIC_LAST_ACCOUNT, null)
        val key = lastAccount?.takeIf { it in accounts && isBiometricAccountEnabled(it) }
            ?: accounts.firstOrNull { isBiometricAccountEnabled(it) }
            ?: return null
        val json = prefs.getString(biometricCustomerDataKey(key), null) ?: return null
        return try {
            gson.fromJson(json, Customer::class.java)
        } catch (e: Exception) {
            null
        }
    }

    private fun isBiometricAccountEnabled(key: String): Boolean =
        prefs.getBoolean(biometricEnabledKey(key), false) &&
            !prefs.getString(biometricRefreshTokenKey(key), null).isNullOrBlank()

    private fun biometricAccountKey(customer: Customer?): String? {
        val id = customer?.id?.trim()?.takeIf {
            it.isNotBlank() && it !in setOf("cust-1", "cust-new", "cust-google", "cust-bio")
        }
        val email = customer?.email?.trim()?.lowercase(Locale.ROOT)?.takeIf {
            it.isNotBlank() && it != "user@gmail.com"
        }
        val identity = id ?: email
            ?: return null
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(identity.lowercase(Locale.ROOT).toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { byte -> "%02x".format(byte) }
    }

    private fun biometricEnabledKey(accountKey: String) = "${KEY_BIOMETRIC_ENABLED}_$accountKey"
    private fun biometricRefreshTokenKey(accountKey: String) = "${KEY_BIOMETRIC_REFRESH_TOKEN}_$accountKey"
    private fun biometricCustomerDataKey(accountKey: String) = "${KEY_BIOMETRIC_CUSTOMER_DATA}_$accountKey"

    fun saveAuthProvider(provider: String) {
        prefs.edit().putString(KEY_AUTH_PROVIDER, provider).apply()
    }

    fun getAuthProvider(): String? = prefs.getString(KEY_AUTH_PROVIDER, null)

    fun isGoogleUser(): Boolean {
        val provider = getAuthProvider()
        if (provider != null) {
            return provider == "google"
        }
        val customer = getCustomer() ?: getBiometricCustomer()
        return customer?.id == "cust-google" || (customer?.email?.endsWith("@gmail.com") == true)
    }

    fun getCameraOverrides(): Map<String, CustomerCameraEdit> {
        val json = prefs.getString("camera_overrides", null) ?: return emptyMap()
        return try {
            val type = object : com.google.gson.reflect.TypeToken<Map<String, CustomerCameraEdit>>() {}.type
            gson.fromJson(json, type) ?: emptyMap()
        } catch (e: Exception) {
            emptyMap()
        }
    }

    fun saveCameraOverride(id: String, name: String, location: String, streamUrl: String) {
        val current = getCameraOverrides().toMutableMap()
        current[id] = CustomerCameraEdit(id, name, location, streamUrl)
        prefs.edit().putString("camera_overrides", gson.toJson(current)).apply()
    }

    fun getDeletedCameraIds(): Set<String> {
        return prefs.getStringSet("deleted_camera_ids", emptySet()) ?: emptySet()
    }

    fun markCameraDeleted(cameraId: String) {
        val current = getDeletedCameraIds().toMutableSet()
        current.add(cameraId)
        val overrides = getCameraOverrides().toMutableMap()
        overrides.remove(cameraId)
        prefs.edit()
            .putStringSet("deleted_camera_ids", current)
            .putString("camera_overrides", gson.toJson(overrides))
            .apply()
    }

    fun unmarkCameraDeleted(cameraId: String) {
        val current = getDeletedCameraIds().toMutableSet()
        current.remove(cameraId)
        prefs.edit().putStringSet("deleted_camera_ids", current).apply()
    }

    fun getLocalRecordings(): List<Recording> {
        val json = prefs.getString("local_recordings", null) ?: return emptyList()
        return try {
            val type = object : com.google.gson.reflect.TypeToken<List<Recording>>() {}.type
            gson.fromJson(json, type) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun saveLocalRecording(recording: Recording) {
        val current = getLocalRecordings().toMutableList()
        current.removeAll { it.id == recording.id }
        current.add(0, recording)
        prefs.edit().putString("local_recordings", gson.toJson(current)).apply()
    }

    fun deleteLocalRecording(id: String) {
        val current = getLocalRecordings().toMutableList()
        current.removeAll { it.id == id }
        prefs.edit().putString("local_recordings", gson.toJson(current)).apply()
    }

    fun clearSession(preserveBiometric: Boolean = true) {
        val editor = prefs.edit()
            .remove(KEY_ACCESS_TOKEN)
            .remove(KEY_REFRESH_TOKEN)
            .remove(KEY_USER_DATA)
            .remove(KEY_AUTH_PROVIDER)
        if (!preserveBiometric) {
            prefs.all.keys
                .filter {
                    it.startsWith("${KEY_BIOMETRIC_ENABLED}_") ||
                        it.startsWith("${KEY_BIOMETRIC_REFRESH_TOKEN}_") ||
                        it.startsWith("${KEY_BIOMETRIC_CUSTOMER_DATA}_")
                }
                .forEach(editor::remove)
            editor.remove(KEY_BIOMETRIC_ENABLED)
                .remove(KEY_BIOMETRIC_REFRESH_TOKEN)
                .remove(KEY_BIOMETRIC_CUSTOMER_DATA)
                .remove(KEY_BIOMETRIC_ACCOUNT_KEYS)
                .remove(KEY_BIOMETRIC_LAST_ACCOUNT)
        }
        editor.apply()
    }

    fun isLoggedIn(): Boolean = !getAccessToken().isNullOrBlank()
}
