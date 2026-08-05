package co.zw.nissangtr.customer.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
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
import co.zw.nissangtr.ui.shop.ShopDefaultScreen
import co.zw.nissangtr.ui.shop.ShopSectionHeader

/** Edit profile — mirrors web `profile-form.tsx` via PostgREST + storefront RPCs. */
@Composable
fun EditProfileScreen(
    rpc: RpcClient,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: EditProfileViewModel = viewModel(factory = EditProfileViewModel.factory(rpc)),
) {
    val state by viewModel.state.collectAsState()
    val sharp = MaterialTheme.shapes.extraSmall

    ShopDefaultScreen(
        title = "Edit profile",
        subtitle = "Personal · contact · marketing",
        onBack = onBack,
        modifier = modifier,
        loading = state.busy && state.firstName.isEmpty() && state.email.isEmpty(),
    ) {
        ShopSectionHeader(title = "Personal information", actionLabel = null)
        OutlinedTextField(
            value = state.firstName,
            onValueChange = viewModel::onFirstName,
            label = { Text("First name") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            enabled = !state.busy,
            shape = sharp,
        )
        OutlinedTextField(
            value = state.lastName,
            onValueChange = viewModel::onLastName,
            label = { Text("Last name") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            enabled = !state.busy,
            shape = sharp,
        )
        OutlinedTextField(
            value = state.company,
            onValueChange = viewModel::onCompany,
            label = { Text("Company / display name") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            enabled = !state.busy,
            shape = sharp,
        )

        ShopSectionHeader(title = "Contact details", actionLabel = null)
        OutlinedTextField(
            value = state.email,
            onValueChange = viewModel::onEmail,
            label = { Text("Email") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            enabled = !state.busy,
            shape = sharp,
        )
        OutlinedTextField(
            value = state.phone,
            onValueChange = viewModel::onPhone,
            label = { Text("Mobile / WhatsApp") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            enabled = !state.busy,
            shape = sharp,
        )
        Text("Preferred receipt channel", style = MaterialTheme.typography.labelMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PreferredReceiptChannel.entries.forEach { channel ->
                FilterChip(
                    selected = state.preferredContact == channel,
                    onClick = { viewModel.onPreferred(channel) },
                    enabled = !state.busy,
                    label = {
                        Text(
                            when (channel) {
                                PreferredReceiptChannel.Whatsapp -> "WhatsApp"
                                PreferredReceiptChannel.Sms -> "SMS"
                                PreferredReceiptChannel.Email -> "Email"
                            },
                        )
                    },
                )
            }
        }

        if (state.customerId != null) {
            ShopSectionHeader(title = "Marketing", actionLabel = null)
            FilterChip(
                selected = state.marketingOptIn,
                onClick = { viewModel.onMarketing(!state.marketingOptIn) },
                enabled = !state.busy,
                label = { Text("Promotional messages") },
            )
            state.lastPromoAt?.let {
                Text(
                    "Last promo: $it",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Button(
            onClick = viewModel::save,
            enabled = !state.busy,
            modifier = Modifier.fillMaxWidth(),
            shape = sharp,
        ) { Text(if (state.busy) "Saving…" else "Save details") }

        state.message?.let {
            Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
        }
        state.error?.let {
            Text(it, color = MaterialTheme.colorScheme.error)
        }
    }
}
