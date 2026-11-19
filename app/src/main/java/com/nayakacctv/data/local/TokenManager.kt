package com.nayakacctv.data.local

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.google.gson.Gson
import com.nayakacctv.data.model.Customer
import com.nayakacctv.data.model.CustomerCameraEdit
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
        private const val KEY_AUTH_PROVIDER = "auth_provider"
    }

    fun getAccessToken(): String? = prefs.getString(KEY_ACCESS_TOKEN, null)

    fun getRefreshToken(): String? = prefs.getString(KEY_REFRESH_TOKEN, null)

    fun saveTokens(accessToken: String?, refreshToken: String?) {
        prefs.edit().apply {
            if (accessToken != null) putString(KEY_ACCESS_TOKEN, accessToken) else remove(KEY_ACCESS_TOKEN)
            if (refreshToken != null) {
                putString(KEY_REFRESH_TOKEN, refreshToken)
                putString(KEY_BIOMETRIC_REFRESH_TOKEN, refreshToken)
            } else {
                remove(KEY_REFRESH_TOKEN)
            }
            apply()
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
            editor.putString(KEY_BIOMETRIC_CUSTOMER_DATA, json)
        } else {
            editor.remove(KEY_USER_DATA)
        }
        editor.apply()
    }

    fun getCustomer(): Customer? {
        val json = prefs.getString(KEY_USER_DATA, null)
            ?: prefs.getString(KEY_BIOMETRIC_CUSTOMER_DATA, null)
            ?: return null
        return try {
            gson.fromJson(json, Customer::class.java)
        } catch (e: Exception) {
            null
        }
    }

    fun isBiometricEnabled(): Boolean = prefs.getBoolean(KEY_BIOMETRIC_ENABLED, true)

    fun setBiometricEnabled(enabled: Boolean) {
        if (!enabled) {
            disableBiometric()
        } else {
            prefs.edit().putBoolean(KEY_BIOMETRIC_ENABLED, true).apply()
        }
    }

    fun enableBiometric(refreshToken: String, customer: Customer?) {
        val editor = prefs.edit()
            .putBoolean(KEY_BIOMETRIC_ENABLED, true)
            .putString(KEY_BIOMETRIC_REFRESH_TOKEN, refreshToken)
        if (customer != null) {
            editor.putString(KEY_BIOMETRIC_CUSTOMER_DATA, gson.toJson(customer))
        }
        editor.apply()
    }

    fun disableBiometric() {
        prefs.edit()
            .putBoolean(KEY_BIOMETRIC_ENABLED, false)
            .remove(KEY_BIOMETRIC_REFRESH_TOKEN)
            .remove(KEY_BIOMETRIC_CUSTOMER_DATA)
            .apply()
    }

    fun getBiometricRefreshToken(): String? =
        prefs.getString(KEY_BIOMETRIC_REFRESH_TOKEN, null) ?: getRefreshToken()

    fun updateBiometricToken(newRefreshToken: String) {
        prefs.edit().putString(KEY_BIOMETRIC_REFRESH_TOKEN, newRefreshToken).apply()
    }

    fun getBiometricCustomer(): Customer? {
        val json = prefs.getString(KEY_BIOMETRIC_CUSTOMER_DATA, null)
            ?: prefs.getString(KEY_USER_DATA, null)
            ?: return null
        return try {
            gson.fromJson(json, Customer::class.java)
        } catch (e: Exception) {
            null
        }
    }

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

    fun clearSession(preserveBiometric: Boolean = true) {
        val editor = prefs.edit()
            .remove(KEY_ACCESS_TOKEN)
            .remove(KEY_REFRESH_TOKEN)
            .remove(KEY_USER_DATA)
        if (!preserveBiometric) {
            editor.remove(KEY_BIOMETRIC_ENABLED)
                .remove(KEY_BIOMETRIC_REFRESH_TOKEN)
                .remove(KEY_BIOMETRIC_CUSTOMER_DATA)
                .remove(KEY_AUTH_PROVIDER)
        }
        editor.apply()
    }

    fun isLoggedIn(): Boolean = !getAccessToken().isNullOrBlank()
}
