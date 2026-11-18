package com.nayakacctv.data.network

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.nayakacctv.data.local.TokenManager
import com.nayakacctv.data.model.ApiResponse
import com.nayakacctv.data.model.AuthTokens
import com.nayakacctv.data.model.RefreshTokenRequest
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody

class TokenAuthenticator(
    private val tokenManager: TokenManager,
    private val baseUrl: String
) : Authenticator {

    private val gson = Gson()

    override fun authenticate(route: Route?, response: Response): Request? {
        // Prevent infinite loops if refresh endpoint itself failed
        if (response.request.url.encodedPath.contains("auth/refresh") ||
            response.request.url.encodedPath.contains("auth/login")) {
            return null
        }

        synchronized(this) {
            val currentToken = tokenManager.getAccessToken()
            val headerToken = response.request.header("Authorization")?.removePrefix("Bearer ")

            // If token already changed in another thread, retry immediately
            if (currentToken != null && currentToken != headerToken) {
                return response.request.newBuilder()
                    .header("Authorization", "Bearer $currentToken")
                    .build()
            }

            val refreshToken = tokenManager.getRefreshToken() ?: return null

            // Perform synchronous refresh call
            val client = OkHttpClient()
            val bodyJson = gson.toJson(RefreshTokenRequest(refreshToken, tokenManager.getInstallationId()))
            val refreshRequest = Request.Builder()
                .url("${baseUrl}mobile/auth/refresh")
                .post(bodyJson.toRequestBody("application/json".toMediaType()))
                .build()

            try {
                val refreshResponse = client.newCall(refreshRequest).execute()
                if (refreshResponse.isSuccessful) {
                    val resString = refreshResponse.body?.string() ?: return null
                    val type = object : TypeToken<ApiResponse<AuthTokens>>() {}.type
                    val apiRes: ApiResponse<AuthTokens> = gson.fromJson(resString, type)
                    val newTokens = apiRes.data ?: return null

                    tokenManager.saveTokens(newTokens.accessToken, newTokens.refreshToken)

                    return response.request.newBuilder()
                        .header("Authorization", "Bearer ${newTokens.accessToken}")
                        .build()
                } else {
                    // Token invalid or revoked -> clear session
                    tokenManager.clearSession()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }

            return null
        }
    }
}
