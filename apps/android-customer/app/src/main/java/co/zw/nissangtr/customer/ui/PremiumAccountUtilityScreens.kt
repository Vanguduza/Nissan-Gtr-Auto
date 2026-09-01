package co.zw.nissangtr.customer.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import co.zw.nissangtr.customer.visual.GtrPremiumColors
import co.zw.nissangtr.customer.visual.PremiumEmptyState
import co.zw.nissangtr.customer.visual.PremiumScreenHeader

@Composable
fun PremiumNotificationsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    androidx.compose.foundation.layout.Column(
        modifier.fillMaxSize().background(GtrPremiumColors.Background)
    ) {
        PremiumScreenHeader(
            title = "Notifications",
            subtitle = "Order, delivery and account updates",
            onBack = onBack,
        )
        Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
            PremiumEmptyState(
                title = "No notifications yet",
                body = "Important order and delivery updates will appear here when the customer inbox service is available.",
            )
        }
    }
}

@Composable
fun PremiumCouponsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    androidx.compose.foundation.layout.Column(
        modifier.fillMaxSize().background(GtrPremiumColors.Background)
    ) {
        PremiumScreenHeader(
            title = "Coupons",
            subtitle = "Available Nissan GTR Auto offers",
            onBack = onBack,
        )
        Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
            PremiumEmptyState(
                title = "No coupons available",
                body = "Active promotions will appear here when they are published.",
            )
        }
    }
}
