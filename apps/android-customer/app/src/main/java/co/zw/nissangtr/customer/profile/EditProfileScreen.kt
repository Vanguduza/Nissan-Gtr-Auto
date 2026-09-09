package co.zw.nissangtr.customer.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import co.zw.nissangtr.customer.rpc.PreferredReceiptChannel
import co.zw.nissangtr.customer.rpc.RpcClient
import co.zw.nissangtr.customer.visual.GtrPremiumColors
import co.zw.nissangtr.customer.visual.PremiumMessageBanner
import co.zw.nissangtr.customer.visual.PremiumMessageKind
import co.zw.nissangtr.customer.visual.PremiumPrimaryButton
import co.zw.nissangtr.customer.visual.PremiumScreenHeader
import co.zw.nissangtr.customer.visual.PremiumSurfaceCard

@Composable
fun EditProfileScreen(
    rpc: RpcClient,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: EditProfileViewModel = viewModel(factory = EditProfileViewModel.factory(rpc)),
) {
    val state by viewModel.state.collectAsState()

    Column(modifier.fillMaxSize().background(GtrPremiumColors.Background)) {
        PremiumScreenHeader("Profile", "Personal and communication details", onBack)
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            PremiumSurfaceCard {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Personal information", color = GtrPremiumColors.TextPrimary, style = MaterialTheme.typography.titleMedium)
                    OutlinedTextField(state.firstName, viewModel::onFirstName, Modifier.fillMaxWidth(), label = { Text("First name") }, singleLine = true, enabled = !state.busy)
                    OutlinedTextField(state.lastName, viewModel::onLastName, Modifier.fillMaxWidth(), label = { Text("Last name") }, singleLine = true, enabled = !state.busy)
                    OutlinedTextField(state.company, viewModel::onCompany, Modifier.fillMaxWidth(), label = { Text("Company / display name") }, singleLine = true, enabled = !state.busy)
                }
            }
            PremiumSurfaceCard {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Contact details", color = GtrPremiumColors.TextPrimary, style = MaterialTheme.typography.titleMedium)
                    OutlinedTextField(state.email, viewModel::onEmail, Modifier.fillMaxWidth(), label = { Text("Email") }, singleLine = true, enabled = !state.busy)
                    OutlinedTextField(state.phone, viewModel::onPhone, Modifier.fillMaxWidth(), label = { Text("Mobile / WhatsApp") }, singleLine = true, enabled = !state.busy)
                    Text("Preferred receipt channel", color = GtrPremiumColors.TextSecondary, style = MaterialTheme.typography.labelMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        PreferredReceiptChannel.entries.forEach { channel ->
                            FilterChip(
                                selected = state.preferredContact == channel,
                                onClick = { viewModel.onPreferred(channel) },
                                enabled = !state.busy,
                                label = {
                                    Text(when (channel) {
                                        PreferredReceiptChannel.Whatsapp -> "WhatsApp"
                                        PreferredReceiptChannel.Sms -> "SMS"
                                        PreferredReceiptChannel.Email -> "Email"
                                    })
                                },
                            )
                        }
                    }
                }
            }
            if (state.customerId != null) {
                PremiumSurfaceCard {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Communication preferences", color = GtrPremiumColors.TextPrimary, style = MaterialTheme.typography.titleMedium)
                        FilterChip(
                            selected = state.marketingOptIn,
                            onClick = { viewModel.onMarketing(!state.marketingOptIn) },
                            enabled = !state.busy,
                            label = { Text("Promotional messages") },
                        )
                    }
                }
            }
            PremiumPrimaryButton(
                text = if (state.busy) "Saving…" else "Save details",
                onClick = viewModel::save,
                enabled = !state.busy,
                modifier = Modifier.fillMaxWidth(),
            )
            state.message?.let { PremiumMessageBanner(it, PremiumMessageKind.Success) }
            state.error?.let { PremiumMessageBanner(it, PremiumMessageKind.Error) }
        }
    }
}
