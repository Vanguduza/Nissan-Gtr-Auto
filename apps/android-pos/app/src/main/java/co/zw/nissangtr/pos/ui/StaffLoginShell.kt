package co.zw.nissangtr.pos.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import co.zw.nissangtr.pos.api.LivePosClient
import co.zw.nissangtr.ui.theme.GtrColors
import co.zw.nissangtr.ui.theme.GtrShapes
import kotlinx.coroutines.launch

/**
 * Staff login — Fake: any non-empty credentials.
 * Live: [resolve_staff_login_email] → GoTrue password (mirror management).
 */
@Composable
fun StaffLoginShell(
    liveClient: LivePosClient? = null,
    forceFake: Boolean = true,
    onSignIn: (staffName: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var identifier by remember { mutableStateOf("t.moyo@nissangtr.co.zw") }
    var password by remember { mutableStateOf("pos") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val useLive = !forceFake && liveClient?.usesLive == true

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(GtrColors.Steel)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 420.dp)
                .fillMaxWidth()
                .background(GtrColors.SteelLift, GtrShapes.medium)
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "NISSAN GTR AUTO",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = GtrColors.Chalk,
            )
            Text(
                text = if (useLive) "POS till · Live staff auth" else "POS till · Fake auth",
                style = MaterialTheme.typography.bodyMedium,
                color = GtrColors.SilverDim,
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = identifier,
                onValueChange = { identifier = it; error = null },
                label = { Text(if (useLive) "Emp# / email / phone" else "Staff email") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                colors = fieldColors(),
            )
            OutlinedTextField(
                value = password,
                onValueChange = { password = it; error = null },
                label = { Text("Password") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
                colors = fieldColors(),
            )
            error?.let {
                Text(it, color = GtrColors.Danger, style = MaterialTheme.typography.bodySmall)
            }
            Button(
                onClick = {
                    if (useLive && liveClient != null) {
                        scope.launch {
                            busy = true
                            error = null
                            try {
                                val email = liveClient.resolveStaffLoginEmail(identifier)
                                liveClient.signInWithEmail(email, password)
                                val name = liveClient.currentUserEmail()
                                    ?.substringBefore("@")
                                    ?.replace('.', ' ')
                                    ?.split(' ')
                                    ?.joinToString(" ") { part ->
                                        part.replaceFirstChar { c -> c.uppercaseChar() }
                                    }
                                    ?: "Staff"
                                liveClient.setStaffDisplayName(name)
                                onSignIn(name)
                            } catch (_: Exception) {
                                error = "Sign-in failed"
                            } finally {
                                busy = false
                            }
                        }
                    } else {
                        val name = identifier.substringBefore("@").replace('.', ' ')
                            .split(' ')
                            .joinToString(" ") { part ->
                                part.replaceFirstChar { c -> c.uppercaseChar() }
                            }
                        onSignIn(name.ifBlank { "Staff" })
                    }
                },
                enabled = !busy && identifier.isNotBlank() && password.isNotBlank(),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp),
                shape = GtrShapes.small,
                colors = ButtonDefaults.buttonColors(
                    containerColor = GtrColors.Primary,
                    contentColor = GtrColors.PrimaryInk,
                ),
            ) {
                Text(
                    if (busy) "Signing in…" else "Sign in",
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

@Composable
private fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = GtrColors.Chalk,
    unfocusedTextColor = GtrColors.Silver,
    focusedBorderColor = GtrColors.Primary,
    unfocusedBorderColor = GtrColors.SilverDim,
    focusedLabelColor = GtrColors.Silver,
    unfocusedLabelColor = GtrColors.SilverDim,
    cursorColor = GtrColors.Primary,
)
