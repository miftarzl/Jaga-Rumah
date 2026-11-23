package com.nayakacctv.data.network

import com.nayakacctv.data.local.TokenManager
import okhttp3.Interceptor
import okhttp3.Response

class AuthInterceptor(private val tokenManager: TokenManager) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()
        val builder = original.newBuilder()
            .header("X-Installation-Id", tokenManager.getInstallationId())

        if (original.header("Accept") == null) {
            builder.header("Accept", "application/json")
        }

        val token = tokenManager.getAccessToken()
        if (!token.isNullOrBlank()) {
            builder.header("Authorization", "Bearer $token")
        }

        return chain.proceed(builder.build())
    }
}
