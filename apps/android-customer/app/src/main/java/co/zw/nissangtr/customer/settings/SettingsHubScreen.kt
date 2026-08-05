package co.zw.nissangtr.customer.settings

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import co.zw.nissangtr.customer.BuildConfig
import co.zw.nissangtr.customer.prefs.CustomerPrefs
import co.zw.nissangtr.customer.prefs.ThemeMode
import co.zw.nissangtr.ui.shop.ShopDefaultScreen
import co.zw.nissangtr.ui.shop.ShopHonestEmpty

private data class LegalLink(val title: String, val url: String)

private val legalLinks = listOf(
    LegalLink("Privacy Policy", "https://nissangtrauto.co.zw/privacy"),
    LegalLink("Terms of Service", "https://nissangtrauto.co.zw/terms"),
    LegalLink("Cookie Policy", "https://nissangtrauto.co.zw/cookies"),
    LegalLink("Returns Policy", "https://nissangtrauto.co.zw/returns"),
    LegalLink("Contact", "https://nissangtrauto.co.zw/contact"),
    LegalLink("About", "https://nissangtrauto.co.zw/about"),
    LegalLink("FAQ", "https://nissangtrauto.co.zw/faq"),
)

@Composable
fun SettingsHubScreen(
    prefs: CustomerPrefs,
    themeMode: ThemeMode,
    onThemeModeChange: (ThemeMode) -> Unit,
    signedInEmail: String?,
    liveRpc: Boolean,
    onSignOut: (() -> Unit)?,
    onOpenAccount: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var receivePush by remember { mutableStateOf(prefs.receivePush) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var deleteMessage by remember { mutableStateOf<String?>(null) }

    ShopDefaultScreen(
        title = "Settings",
        subtitle = "Prefs · legal · account",
        onBack = null,
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                if (liveRpc) "Live Supabase session" else "Fake mode — auth optional",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            signedInEmail?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium)
            }

            Spacer(Modifier.height(8.dp))
            Text("Appearance", style = MaterialTheme.typography.titleMedium)
            Column(Modifier.selectableGroup()) {
                ThemeMode.entries.forEach { mode ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(
                                selected = themeMode == mode,
                                onClick = {
                                    onThemeModeChange(mode)
                                    prefs.themeMode = mode
                                },
                                role = Role.RadioButton,
                            )
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = themeMode == mode,
                            onClick = null,
                        )
                        Text(
                            when (mode) {
                                ThemeMode.System -> "System"
                                ThemeMode.Light -> "Light"
                                ThemeMode.Dark -> "Dark"
                            },
                            modifier = Modifier.padding(start = 8.dp),
                        )
                    }
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            Text("Notifications", style = MaterialTheme.typography.titleMedium)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Receive push notifications")
                    Text(
                        "Preference saved on device. Push delivery needs FCM wiring — not enabled yet.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = receivePush,
                    onCheckedChange = {
                        receivePush = it
                        prefs.receivePush = it
                    },
                )
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            Text("Legal & help", style = MaterialTheme.typography.titleMedium)
            legalLinks.forEach { link ->
                OutlinedButton(
                    onClick = {
                        runCatching {
                            context.startActivity(
                                Intent(Intent.ACTION_VIEW, Uri.parse(link.url)),
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(link.title) }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            Text("Account", style = MaterialTheme.typography.titleMedium)
            OutlinedButton(
                onClick = onOpenAccount,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("My Account") }
            if (onSignOut != null && signedInEmail != null) {
                OutlinedButton(
                    onClick = onSignOut,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Sign out") }
            }
            OutlinedButton(
                onClick = { showDeleteConfirm = true },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Delete account") }
            deleteMessage?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 4.dp),
                )
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            Text(
                "App version ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(24.dp))
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete account?") },
            text = {
                ShopHonestEmpty(
                    title = "No delete-account RPC",
                    body = "There is no customer delete-account endpoint yet. Contact support at nissangtrauto.co.zw/contact — we will not pretend this succeeded.",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirm = false
                        deleteMessage =
                            "Contact support to delete your account — no in-app delete RPC is available."
                        runCatching {
                            context.startActivity(
                                Intent(
                                    Intent.ACTION_VIEW,
                                    Uri.parse("https://nissangtrauto.co.zw/contact"),
                                ),
                            )
                        }
                    },
                ) { Text("Contact support") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel") }
            },
        )
    }
}
