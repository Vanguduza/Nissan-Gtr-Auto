package co.zw.nissangtr.customer.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.CompareArrows
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import co.zw.nissangtr.customer.visual.GtrPremiumColors
import co.zw.nissangtr.customer.visual.PremiumAccountRow
import co.zw.nissangtr.customer.visual.PremiumPrimaryButton
import co.zw.nissangtr.customer.visual.PremiumSecondaryButton
import co.zw.nissangtr.customer.visual.PremiumSurfaceCard

/**
 * Preview bottom-tab Account destination. All business destinations are supplied by the existing
 * app shell so this component owns presentation only.
 */
@Composable
fun PremiumAccountTab(
    signedInEmail: String?,
    liveRpc: Boolean,
    onSignIn: () -> Unit,
    onSignOut: () -> Unit,
    onEditProfile: () -> Unit,
    onOrders: () -> Unit,
    onReturns: () -> Unit,
    onLoyalty: () -> Unit,
    onKits: () -> Unit,
    onAddresses: () -> Unit,
    onPay: () -> Unit,
    onGarage: () -> Unit,
    onCompare: () -> Unit,
    onTrack: () -> Unit,
    onChat: () -> Unit,
    onNotifications: () -> Unit,
    onCoupons: () -> Unit,
    onSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(GtrPremiumColors.Background)
            .verticalScroll(rememberScrollState()),
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 18.dp)) {
            Text(
                "My Account",
                color = GtrPremiumColors.TextPrimary,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.padding(top = 8.dp))
            PremiumSurfaceCard {
                Column {
                    Text(
                        signedInEmail ?: if (liveRpc) "Guest" else "Demo account",
                        color = GtrPremiumColors.TextPrimary,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        if (signedInEmail == null) {
                            "Sign in to sync orders, garage and saved items."
                        } else {
                            "Nissan GTR Auto customer"
                        },
                        color = GtrPremiumColors.TextSecondary,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }

        PremiumAccountRow(Icons.Filled.AccountCircle, "Profile", "Personal and contact details", onEditProfile)
        PremiumAccountRow(Icons.Filled.ReceiptLong, "Orders", "History and order status", onOrders)
        PremiumAccountRow(Icons.Filled.LocalShipping, "Returns", "Request and track returns", onReturns)
        PremiumAccountRow(Icons.Filled.Star, "GTR Rewards", "Points and eligible benefits", onLoyalty)
        PremiumAccountRow(Icons.Filled.Build, "Service kits", "Maintenance bundles", onKits)
        PremiumAccountRow(Icons.Filled.LocationOn, "Addresses", "Delivery addresses", onAddresses)
        PremiumAccountRow(Icons.Filled.CreditCard, "Payment", "Secure payment", onPay)
        PremiumAccountRow(Icons.Filled.DirectionsCar, "My Garage", "Saved vehicles", onGarage)
        PremiumAccountRow(Icons.Filled.CompareArrows, "Compare", "Compare selected products", onCompare)
        PremiumAccountRow(Icons.Filled.LocalShipping, "Track delivery", "Latest permitted point and ETA", onTrack)
        PremiumAccountRow(Icons.Filled.Chat, "Support", "Chat with the parts counter", onChat)
        PremiumAccountRow(Icons.Filled.Notifications, "Notifications", "Order and account updates", onNotifications)
        PremiumAccountRow(Icons.Filled.CardGiftcard, "Coupons", "Available promotions", onCoupons)
        PremiumAccountRow(Icons.Filled.Settings, "Settings", "Preferences, privacy and help", onSettings)

        Column(Modifier.padding(16.dp)) {
            if (signedInEmail == null) {
                PremiumPrimaryButton(
                    text = "Sign in",
                    onClick = onSignIn,
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                PremiumSecondaryButton(
                    text = "Sign out",
                    onClick = onSignOut,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Spacer(Modifier.padding(bottom = 20.dp))
        }
    }
}
