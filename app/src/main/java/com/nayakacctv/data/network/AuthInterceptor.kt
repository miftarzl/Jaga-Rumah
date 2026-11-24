package com.nayakacctv.data.network

import com.nayakacctv.data.local.TokenManager
import okhttp3.Interceptor
import okhttp3.Response
import okhttp3.HttpUrl.Companion.toHttpUrl

class AuthInterceptor(
    private val tokenManager: TokenManager,
    baseUrl: String
) : Interceptor {
    private val apiBaseUrl = baseUrl.toHttpUrl()

    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()
        val builder = original.newBuilder()

        if (original.header("Accept") == null) {
            builder.header("Accept", "application/json")
        }

        val requestUrl = original.url
        if (requestUrl.scheme == apiBaseUrl.scheme &&
            requestUrl.host == apiBaseUrl.host &&
            requestUrl.port == apiBaseUrl.port
        ) {
            builder.header("X-Installation-Id", tokenManager.getInstallationId())

            val token = tokenManager.getAccessToken()
            if (!token.isNullOrBlank()) {
                builder.header("Authorization", "Bearer $token")
            }
        }

        return chain.proceed(builder.build())
    }
}
