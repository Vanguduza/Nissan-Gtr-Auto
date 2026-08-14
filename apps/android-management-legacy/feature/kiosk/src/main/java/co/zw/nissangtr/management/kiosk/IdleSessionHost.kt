package co.zw.nissangtr.management.kiosk

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import co.zw.nissangtr.management.rpc.SupabaseRpcClient
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Wraps authenticated content with idle detection.
 * After [KioskDevicePrefs] idle minutes (default 3), clears in-memory UI via [onIdleLock]
 * and shows an in-app reauth overlay — never navigates to the system launcher.
 */
@Composable
fun IdleSessionHost(
    prefs: KioskDevicePrefs,
    enabled: Boolean,
    liveRpc: Boolean,
    supabase: SupabaseRpcClient?,
    signedInEmail: String?,
    onIdleLock: () -> Unit,
    content: @Composable () -> Unit,
) {
    if (!enabled) {
        content()
        return
    }

    val idleMinutes by prefs.idleMinutes.collectAsState(initial = KioskDevicePrefs.DEFAULT_IDLE_MINUTES)
    var lastActiveAt by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var locked by remember { mutableStateOf(false) }
    var contentGeneration by remember { mutableStateOf(0) }

    fun bump() {
        lastActiveAt = System.currentTimeMillis()
    }

    LaunchedEffect(idleMinutes, locked) {
        if (locked) return@LaunchedEffect
        while (true) {
            delay(5_000L)
            val timeoutMs = idleMinutes.coerceIn(
                KioskDevicePrefs.MIN_IDLE_MINUTES,
                KioskDevicePrefs.MAX_IDLE_MINUTES,
            ) * 60_000L
            if (System.currentTimeMillis() - lastActiveAt >= timeoutMs) {
                locked = true
                contentGeneration += 1
                onIdleLock()
                break
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures {
                    if (!locked) bump()
                }
            },
    ) {
        // Remount clears in-memory nav/session UI while Lock Task stays active.
        androidx.compose.runtime.key(contentGeneration) {
            content()
        }
        if (locked) {
            IdleLockOverlay(
                signedInEmail = signedInEmail,
                liveRpc = liveRpc,
                supabase = supabase,
                onUnlocked = {
                    locked = false
                    bump()
                },
            )
        }
    }
}

@Composable
fun IdleLockOverlay(
    signedInEmail: String?,
    liveRpc: Boolean,
    supabase: SupabaseRpcClient?,
    onUnlocked: () -> Unit,
) {
    var password by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.92f))
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
            ) { /* block underlying UI */ },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                "Session locked",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onPrimary,
            )
            Text(
                "Inactivity timeout — reauthenticate to continue. Device stays in kiosk.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f),
                modifier = Modifier.padding(top = 8.dp, bottom = 16.dp),
            )
            if (signedInEmail != null) {
                Text(
                    signedInEmail,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f),
                )
            }
            OutlinedTextField(
                value = password,
                onValueChange = {
                    password = it
                    error = null
                },
                label = { Text("Password") },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
                singleLine = true,
                enabled = !busy,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            )
            Button(
                onClick = {
                    scope.launch {
                        busy = true
                        error = null
                        try {
                            when {
                                !liveRpc || supabase == null -> {
                                    password = ""
                                    onUnlocked()
                                }
                                signedInEmail.isNullOrBlank() -> {
                                    error = "No signed-in account"
                                }
                                else -> {
                                    supabase.signInWithEmail(signedInEmail, password)
                                    password = ""
                                    onUnlocked()
                                }
                            }
                        } catch (e: Exception) {
                            error = e.message ?: "Reauthentication failed"
                        } finally {
                            busy = false
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
                enabled = !busy && (password.isNotBlank() || !liveRpc),
            ) {
                Text(if (busy) "Unlocking…" else "Unlock")
            }
            error?.let {
                Text(
                    it,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
    }
}
