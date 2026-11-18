package com.nayakacctv

import android.app.Application
import com.nayakacctv.data.local.TokenManager
import com.nayakacctv.data.network.NetworkClient
import com.nayakacctv.data.repository.NayakaRepository

class NayakaApp : Application() {

    lateinit var tokenManager: TokenManager
        private set

    lateinit var repository: NayakaRepository
        private set

    override fun onCreate() {
        super.onCreate()
        tokenManager = TokenManager(this)
        val apiService = NetworkClient.getApiService(this)
        repository = NayakaRepository(apiService, tokenManager)
    }
}
