package com.nayakacctv.ui.screens.splash

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.nayakacctv.R
import com.nayakacctv.ui.theme.PrimaryBlue
import kotlinx.coroutines.delay

@Composable
fun JagaRumahSplashScreen(onFinished: () -> Unit) {
    LaunchedEffect(Unit) {
        delay(800)
        onFinished()
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color(0xFFF8FAFC)
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(28.dp)
            ) {
                Image(
                    painter = painterResource(id = R.drawable.ic_jaga_rumah_horizontal),
                    contentDescription = "JAGA RUMAH - Smart Home Security",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxWidth(0.86f)
                        .aspectRatio(1440f / 512f)
                )
                CircularProgressIndicator(
                    modifier = Modifier.size(22.dp),
                    color = PrimaryBlue,
                    strokeWidth = 2.dp
                )
            }
        }
    }
}
