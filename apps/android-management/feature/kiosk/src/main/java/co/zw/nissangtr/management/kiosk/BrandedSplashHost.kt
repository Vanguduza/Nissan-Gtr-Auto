package co.zw.nissangtr.management.kiosk

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/**
 * Tablet cold-start branded splash. Plays once per process by default;
 * skip after ordinary logout ([skipSplashThisProcess]).
 *
 * Engine audio: **default OFF** via [KioskDevicePrefs]. Plays only when the
 * Device Admin toggle is ON and a replaceable `res/raw/engine_splash` asset exists.
 */
object SplashSessionGate {
    /** Set true after logout so the next AuthGate remount does not replay splash. */
    @Volatile
    var skipSplashThisProcess: Boolean = false

    /** True after splash completed (or skipped) this process. */
    @Volatile
    var splashCompletedThisProcess: Boolean = false
}

@Composable
fun BrandedSplashHost(
    prefs: KioskDevicePrefs,
    tabletKiosk: Boolean,
    durationMs: Long = 4_500L,
    onFinished: () -> Unit,
    content: @Composable () -> Unit,
) {
    val shouldShow =
        tabletKiosk &&
            !SplashSessionGate.splashCompletedThisProcess &&
            !SplashSessionGate.skipSplashThisProcess

    if (!shouldShow) {
        SplashSessionGate.splashCompletedThisProcess = true
        content()
        return
    }

    var finished by remember { mutableStateOf(false) }
    val engineAudio by prefs.engineAudioEnabled.collectAsState(
        initial = KioskDevicePrefs.DEFAULT_ENGINE_AUDIO_ENABLED,
    )
    val context = LocalContext.current

    if (finished) {
        content()
        return
    }

    DisposableEffect(engineAudio) {
        val player = if (engineAudio) startEngineAudioIfPresent(context) else null
        onDispose {
            player?.run {
                runCatching {
                    if (isPlaying) stop()
                    release()
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        delay(durationMs)
        SplashSessionGate.splashCompletedThisProcess = true
        finished = true
        onFinished()
    }

    BrandedSplashCanvas(
        onSkip = {
            SplashSessionGate.splashCompletedThisProcess = true
            finished = true
            onFinished()
        },
    )
}

@Composable
private fun BrandedSplashCanvas(onSkip: () -> Unit) {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        progress.animateTo(
            1f,
            animationSpec = tween(durationMillis = 4_200, easing = FastOutSlowInEasing),
        )
    }

    val steel = Color(0xFF12151C)
    val chalk = Color(0xFFF4F5F7)
    val accent = Color(0xFFC8102E)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(steel),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val p = progress.value
            val y = size.height * 0.48f
            val startX = -size.width * 0.2f
            val endX = size.width * 1.1f
            val x = startX + (endX - startX) * p
            // Silhouette bar + headlight flare
            drawRect(
                color = chalk.copy(alpha = 0.12f + 0.35f * p),
                topLeft = Offset(x - size.width * 0.18f, y - 18f),
                size = androidx.compose.ui.geometry.Size(size.width * 0.28f, 36f),
            )
            drawCircle(
                color = accent.copy(alpha = 0.55f * p),
                radius = 28f + 40f * p,
                center = Offset(x, y),
            )
            drawCircle(
                color = chalk.copy(alpha = 0.85f * p.coerceIn(0f, 1f)),
                radius = 10f,
                center = Offset(x + 40f, y - 6f),
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                "NISSAN GTR AUTO",
                style = MaterialTheme.typography.headlineLarge,
                color = chalk,
            )
            Text(
                "INITIALIZING BUSINESS SYSTEM",
                style = MaterialTheme.typography.titleMedium,
                color = chalk.copy(alpha = 0.75f),
                modifier = Modifier.padding(top = 12.dp),
            )
            TextButton(onClick = onSkip, modifier = Modifier.padding(top = 24.dp)) {
                Text("Skip", color = chalk.copy(alpha = 0.6f))
            }
        }
    }
}

/**
 * Opens replaceable `raw/engine_splash` when present. Missing asset = silent
 * (licensed pack can be dropped in without controller rewrite).
 */
private fun startEngineAudioIfPresent(context: Context): MediaPlayer? {
    val resId = context.resources.getIdentifier(
        "engine_splash",
        "raw",
        context.packageName,
    )
    if (resId == 0) return null
    return runCatching {
        MediaPlayer.create(context, resId)?.apply {
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build(),
            )
            setOnCompletionListener { mp ->
                runCatching { mp.release() }
            }
            start()
        }
    }.getOrNull()
}
