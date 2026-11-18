package com.nayakacctv

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.fragment.app.FragmentActivity
import androidx.navigation.compose.rememberNavController
import com.nayakacctv.ui.navigation.AppNavHost
import com.nayakacctv.ui.theme.NayakaCCTVTheme

class MainActivity : FragmentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val app = application as NayakaApp
        val repository = app.repository

        setContent {
            NayakaCCTVTheme {
                val navController = rememberNavController()
                AppNavHost(
                    navController = navController,
                    repository = repository
                )
            }
        }
    }
}
