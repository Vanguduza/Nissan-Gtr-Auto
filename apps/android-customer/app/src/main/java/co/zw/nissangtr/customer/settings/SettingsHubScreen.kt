package co.zw.nissangtr.customer.settings

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Policy
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import co.zw.nissangtr.customer.BuildConfig
import co.zw.nissangtr.customer.prefs.CustomerPrefs
import co.zw.nissangtr.customer.prefs.ThemeMode
import co.zw.nissangtr.customer.visual.GtrPremiumColors
import co.zw.nissangtr.customer.visual.PremiumAccountRow
import co.zw.nissangtr.customer.visual.PremiumScreenHeader
import co.zw.nissangtr.customer.visual.PremiumSecondaryButton
import co.zw.nissangtr.customer.visual.PremiumSurfaceCard

private data class LegalLink(val title: String, val url: String)

private val legalLinks = listOf(
    LegalLink("Privacy Policy", "https://nissangtrauto.co.zw/privacy"),
    LegalLink("Terms of Service", "https://nissangtrauto.co.zw/terms"),
    LegalLink("Returns Policy", "https://nissangtrauto.co.zw/returns"),
    LegalLink("Contact & Support", "https://nissangtrauto.co.zw/contact"),
    LegalLink("FAQ", "https://nissangtrauto.co.zw/faq"),
)

/**
 * The approved storefront is dark-first. The old theme selector is intentionally not surfaced:
 * CustomerShopTheme remains premium dark so a settings toggle cannot break the locked preview.
 * Parameters stay source-compatible.
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
    @Suppress("UNUSED_VARIABLE")
    val sourceCompatibility = themeMode to onThemeModeChange
    val context = LocalContext.current
    var receivePush by remember { mutableStateOf(prefs.receivePush) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    Column(
        modifier = rootModifier
            .fillMaxSize()
            .background(GtrPremiumColors.Background)
            .verticalScroll(rememberScrollState()),
    ) {
        PremiumScreenHeader(
            title = "Settings",
            subtitle = "Preferences, privacy and help",
            onBack = onOpenAccount,
        )

        Column(
            Modifier.padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            PremiumSurfaceCard {
                Column {
                    Text(
                        "Appearance",
                        color = GtrPremiumColors.TextPrimary,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        "Nissan GTR Auto Premium Dark",
                        color = GtrPremiumColors.TextSecondary,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }

            PremiumSurfaceCard {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            "Push notifications",
                            color = GtrPremiumColors.TextPrimary,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            "Order, delivery and account updates",
                            color = GtrPremiumColors.TextSecondary,
                            style = MaterialTheme.typography.bodySmall,
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
            }
        }

        Spacer(Modifier.padding(top = 8.dp))
        PremiumAccountRow(Icons.Filled.AccountCircle, "Edit profile", null, onEditProfile)
        legalLinks.forEach { link ->
            PremiumAccountRow(
                icon = if (link.title.contains("Privacy") || link.title.contains("Terms") || link.title.contains("Returns")) {
                    Icons.Filled.Policy
                } else {
                    Icons.Filled.HelpOutline
                },
                title = link.title,
                onClick = {
                    runCatching {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(link.url)))
                    }
                },
            )
        }

        Column(
            Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (signedInEmail == null && onSignIn != null) {
                PremiumSecondaryButton(
                    text = "Sign in",
                    onClick = onSignIn,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (signedInEmail != null && onSignOut != null) {
                PremiumSecondaryButton(
                    text = "Sign out",
                    onClick = onSignOut,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            PremiumSecondaryButton(
                text = "Delete account",
                onClick = { showDeleteConfirm = true },
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                "App version ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                color = GtrPremiumColors.TextDisabled,
                style = MaterialTheme.typography.bodySmall,
            )
            Spacer(Modifier.padding(bottom = 20.dp))
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete account?") },
            text = {
                Text("Account deletion is handled by Nissan GTR Auto support until a verified delete-account endpoint is available.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirm = false
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
