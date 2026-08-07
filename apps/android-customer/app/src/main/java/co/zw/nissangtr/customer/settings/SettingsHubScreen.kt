package co.zw.nissangtr.customer.settings

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
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

/**
 * Settings tab content under the shell Scaffold.
 *
 * Must NOT use ShopDefaultScreen here: that wraps a nested Material3 Scaffold with
 * fillMaxSize + verticalScroll. As a child of the shell Column (unbounded max height),
 * nested Scaffold measurement throws and crashes when opening Settings.
 */
@Composable
fun SettingsHubScreen(
    prefs: CustomerPrefs,
    themeMode: ThemeMode,
    onThemeModeChange: (ThemeMode) -> Unit,
    signedInEmail: String?,
    onSignOut: (() -> Unit)?,
    onOpenAccount: () -> Unit,
    onEditProfile: () -> Unit = onOpenAccount,
    onSignIn: (() -> Unit)? = null,
    rootModifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var receivePush by remember { mutableStateOf(prefs.receivePush) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var deleteMessage by remember { mutableStateOf<String?>(null) }

    Column(
        rootModifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text("Settings", style = MaterialTheme.typography.titleLarge)

        signedInEmail?.let {
            Text(it, style = MaterialTheme.typography.bodyMedium)
        }

        Spacer(Modifier.height(8.dp))
        Text("Appearance", style = MaterialTheme.typography.titleMedium)
        Column(Modifier.selectableGroup()) {
            ThemeMode.entries.forEach { mode ->
                Row(
                    Modifier
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
                        Modifier.padding(start = 8.dp),
                    )
                }
            }
        }

        HorizontalDivider(Modifier.padding(vertical = 8.dp))
        Text("Notifications", style = MaterialTheme.typography.titleMedium)
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "Receive push notifications",
                Modifier.weight(1f),
            )
            Switch(
                checked = receivePush,
                onCheckedChange = {
                    receivePush = it
                    prefs.receivePush = it
                },
            )
        }

        HorizontalDivider(Modifier.padding(vertical = 8.dp))
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

        HorizontalDivider(Modifier.padding(vertical = 8.dp))
        Text("Account", style = MaterialTheme.typography.titleMedium)
        if (signedInEmail == null && onSignIn != null) {
            OutlinedButton(
                onClick = onSignIn,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Sign in") }
        }
        OutlinedButton(
            onClick = onEditProfile,
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Edit profile") }
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

        HorizontalDivider(Modifier.padding(vertical = 8.dp))
        Text(
            "App version ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(24.dp))
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete account?") },
            text = {
                Text(
                    "There is no customer delete-account endpoint yet. Contact support at nissangtrauto.co.zw/contact — we will not pretend this succeeded.",
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
