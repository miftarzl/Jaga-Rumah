package com.nayakacctv.data.network

import android.content.Context
import android.net.Uri
import com.nayakacctv.data.local.TokenManager
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object NetworkClient {

    const val BASE_URL = "http://dev.nayakacloud.com:701/api/v1/"

    private var apiService: ApiService? = null
    @Volatile private var authenticatedClient: OkHttpClient? = null

    fun getApiService(context: Context): ApiService {
        return apiService ?: synchronized(this) {
            apiService ?: createApiService(context).also { apiService = it }
        }
    }

    fun getAuthenticatedOkHttpClient(context: Context): OkHttpClient {
        return authenticatedClient ?: synchronized(this) {
            authenticatedClient ?: createOkHttpClient(context).also { authenticatedClient = it }
        }
    }

    fun resolveUrl(url: String): String {
        val trimmed = url.trim()
        if (trimmed.startsWith("http://", ignoreCase = true) ||
            trimmed.startsWith("https://", ignoreCase = true)
        ) {
            return trimmed
        }

        val baseUri = Uri.parse(BASE_URL)
        val origin = "${baseUri.scheme}://${baseUri.authority}"
        return if (trimmed.startsWith("/")) {
            origin + trimmed
        } else if (trimmed.startsWith("api/v1/")) {
            "$origin/$trimmed"
        } else {
            BASE_URL.trimEnd('/') + "/" + trimmed.trimStart('/')
        }
    }

    private fun createApiService(context: Context): ApiService {
        val retrofit = Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(getAuthenticatedOkHttpClient(context))
            .addConverterFactory(GsonConverterFactory.create())
            .build()

        return retrofit.create(ApiService::class.java)
    }

    private fun createOkHttpClient(context: Context): OkHttpClient {
        val tokenManager = TokenManager(context)

        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        }

        return OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .addInterceptor(AuthInterceptor(tokenManager, BASE_URL))
            .authenticator(TokenAuthenticator(tokenManager, BASE_URL))
            .addInterceptor(logging)
            .build()
    }
}
