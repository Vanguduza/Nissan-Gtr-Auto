package com.joker.coolmall.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import co.zw.nissangtr.management.gtradapter.GtrIdleLockController
import co.zw.nissangtr.management.gtradapter.GtrStaffAuthAdapter
import co.zw.nissangtr.management.gtradapter.GtrStaffSession
import com.joker.coolmall.core.data.state.AppState
import com.joker.coolmall.core.ui.component.button.AppButton
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Cheap 3-minute idle lock overlay (web STAFF_IDLE_LOCK_MS parity).
 * Does not persist across process death (sessionStorage parity is a follow-up).
 */
@Composable
fun StaffIdleLockHost(
    appState: AppState,
    session: GtrStaffSession,
    idleLock: GtrIdleLockController,
    auth: GtrStaffAuthAdapter,
    content: @Composable () -> Unit,
) {
    val isLoggedIn by appState.isLoggedIn.collectAsState()
    val locked by idleLock.locked.collectAsState()
    val scope = rememberCoroutineScope()
    var unlockPassword by remember { mutableStateOf("") }
    var unlockError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(isLoggedIn, session.context) {
        idleLock.setEnabled(isLoggedIn && session.context != null)
    }

    LaunchedEffect(isLoggedIn) {
        while (isActive && isLoggedIn) {
            idleLock.tick()
            delay(5_000)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        content()
        if (locked && isLoggedIn) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.92f))
                    .padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = "Session locked",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
                Text(
                    text = "Idle for 3 minutes — enter password to continue",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.padding(top = 8.dp, bottom = 16.dp),
                )
                OutlinedTextField(
                    value = unlockPassword,
                    onValueChange = {
                        unlockPassword = it
                        unlockError = null
                    },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("Password") },
                    visualTransformation = PasswordVisualTransformation(),
                )
                if (unlockError != null) {
                    Text(
                        text = unlockError!!,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
                AppButton(
                    text = "Unlock",
                    onClick = {
                        scope.launch {
                            auth.reauthWithPassword(unlockPassword).fold(
                                onSuccess = {
                                    unlockPassword = ""
                                    unlockError = null
                                    idleLock.unlock()
                                },
                                onFailure = { unlockError = "Unlock failed" },
                            )
                        }
                    },
                    modifier = Modifier.padding(top = 16.dp),
                )
            }
        }
    }
}
