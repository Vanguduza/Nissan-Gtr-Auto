package co.zw.nissangtr.ui.shop

/**
 * Forked from Shopping-By-KMP `presentation/ui/splash/SplashScreen.kt`
 * (MIT © 2023 Mahdi Razzaghi Ghaleh). Fashion product pager / Stallion font
 * omitted (Compose Multiplatform resources). Keeps expanding primary circle,
 * delayed brand reveal, bottom progress — branded with GTR logo + tokens.
 */

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import co.zw.nissangtr.ui.theme.GtrColors
import co.zw.nissangtr.ui.theme.GtrLogo
import kotlinx.coroutines.delay

@Composable
fun ShopSplash(
    brand: String = "Nissan GTR Auto",
    tagline: String = "Genuine parts · Harare & nationwide",
    onFinished: () -> Unit,
    modifier: Modifier = Modifier,
    holdMs: Long = 2200,
) {
    val showBrand = produceState(initialValue = false) {
        delay(1000)
        value = true
    }
    LaunchedEffect(Unit) {
        delay(holdMs)
        onFinished()
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        ShopSplashCircle(
            modifier = Modifier.align(Alignment.TopCenter),
            color = MaterialTheme.colorScheme.primary,
        )
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            AnimatedVisibility(
                visible = showBrand.value,
                enter = fadeIn(animationSpec = tween(500)),
                exit = fadeOut(animationSpec = tween(500)),
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    GtrLogo(modifier = Modifier.height(48.dp).width(160.dp))
                    Spacer(modifier = Modifier.height(24.dp))
                    Text(
                        brand,
                        style = MaterialTheme.typography.headlineMedium.copy(fontSize = 28.sp),
                        color = GtrColors.Chalk,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        tagline,
                        style = MaterialTheme.typography.bodyMedium,
                        color = GtrColors.Chalk.copy(alpha = 0.85f),
                    )
                }
            }
        }
        CircularProgressIndicator(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(16.dp),
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

/** KMP SplashScreen [Circle] — expanding primary canvas from top center. */
@Composable
private fun ShopSplashCircle(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    radiusEnd: Float = 1800f,
) {
    val targetValue = produceState(initialValue = 0f) {
        delay(500)
        value = radiusEnd
    }
    val floatAnim by animateFloatAsState(
        targetValue = targetValue.value,
        animationSpec = keyframes { durationMillis = 200 },
        label = "splashCircle",
    )
    Canvas(modifier = modifier.fillMaxSize()) {
        val centerOffset = Offset(size.width / 2f, 0f)
        drawCircle(
            color = color,
            radius = floatAnim,
            center = centerOffset,
        )
    }
}
