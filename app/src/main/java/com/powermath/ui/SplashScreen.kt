package com.powermath.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.powermath.R
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay

private val SplashBg = Color(0xFF050926)   // same navy as the CloudPixel artwork, so its edges blend in

/**
 * Intro: the Power Math logo springs in, then "Powered by" and the CloudPixel logo rise in beneath it.
 * Calls [onFinished] after ~3.2 s, or immediately when the user taps.
 */
@Composable
fun SplashScreen(onFinished: () -> Unit) {
    val appAlpha = remember { Animatable(0f) }
    val appScale = remember { Animatable(0.82f) }
    val glow = remember { Animatable(0f) }
    val byAlpha = remember { Animatable(0f) }
    val byShift = remember { Animatable(1f) }

    LaunchedEffect(Unit) {
        coroutineScope {
            async { appAlpha.animateTo(1f, tween(700, easing = FastOutSlowInEasing)) }
            async { appScale.animateTo(1f, spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessLow)) }
            async { glow.animateTo(1f, tween(1400, easing = FastOutSlowInEasing)) }
            delay(1100)
            async { byAlpha.animateTo(1f, tween(800, easing = FastOutSlowInEasing)) }
            async { byShift.animateTo(0f, tween(800, easing = FastOutSlowInEasing)) }
        }
        delay(1700)
        onFinished()
    }

    Box(
        Modifier.fillMaxSize().background(SplashBg)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onFinished() }
            .semantics { contentDescription = "Power Math, powered by CloudPixel. Tap to skip." },
        contentAlignment = Alignment.Center
    ) {
        // soft glow behind the logo
        Box(
            Modifier.size(420.dp).graphicsLayer { alpha = glow.value * 0.55f; scaleX = 0.8f + 0.2f * glow.value; scaleY = 0.8f + 0.2f * glow.value }
                .background(Brush.radialGradient(listOf(Color(0xFF2E6BFF).copy(alpha = 0.55f), Color.Transparent)))
        )
        Column(Modifier.fillMaxSize().padding(horizontal = 32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(Modifier.weight(1f))
            Image(
                painterResource(R.drawable.logo_powermath), contentDescription = "Power Math logo", contentScale = ContentScale.Fit,
                modifier = Modifier.widthIn(max = 260.dp).fillMaxWidth(0.62f).clip(RoundedCornerShape(36.dp))
                    .graphicsLayer { alpha = appAlpha.value; scaleX = appScale.value; scaleY = appScale.value }
            )
            Spacer(Modifier.weight(1f))
            Column(
                Modifier.graphicsLayer { alpha = byAlpha.value; translationY = byShift.value * 40f },
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("Powered by", color = Color(0xFF9DB4FF), fontSize = 13.sp, fontWeight = FontWeight.Medium, letterSpacing = 2.sp)
                Image(
                    painterResource(R.drawable.logo_cloudpixel), contentDescription = "CloudPixel logo", contentScale = ContentScale.Fit,
                    modifier = Modifier.height(120.dp)
                )
                Text("CloudPixel", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 3.sp)
            }
            Spacer(Modifier.height(48.dp))
        }
    }
}
