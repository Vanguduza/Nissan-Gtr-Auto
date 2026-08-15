package co.zw.nissangtr.pos.customer

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import co.zw.nissangtr.pos.api.CustomerRef
import co.zw.nissangtr.ui.theme.GtrColors
import co.zw.nissangtr.ui.theme.GtrShapes

@Composable
fun CustomerSelectSheet(
    customers: List<CustomerRef>,
    query: String,
    selectedId: String?,
    onQueryChange: (String) -> Unit,
    onSelect: (CustomerRef?) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(GtrColors.SteelLift, GtrShapes.medium)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = "Select customer",
            style = MaterialTheme.typography.titleMedium,
            color = GtrColors.Chalk,
            fontWeight = FontWeight.Bold,
        )
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            label = { Text("Search") },
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = GtrColors.Chalk,
                unfocusedTextColor = GtrColors.Chalk,
                focusedBorderColor = GtrColors.Primary,
                unfocusedBorderColor = GtrColors.SilverDim,
                cursorColor = GtrColors.Primary,
            ),
        )
        TextButton(onClick = { onSelect(null) }) {
            Text("Walk-in (clear)", color = GtrColors.Silver)
        }
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 280.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            items(customers, key = { it.id }) { c ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            if (c.id == selectedId) GtrColors.Steel else GtrColors.Steel,
                            GtrShapes.small,
                        )
                        .clickable { onSelect(c) }
                        .padding(12.dp),
                ) {
                    Text(c.displayName, color = GtrColors.Chalk, fontWeight = FontWeight.SemiBold)
                    if (c.creditHold) {
                        Text("Credit hold · needs finance", color = GtrColors.Warning)
                    } else {
                        Text(c.currency, color = GtrColors.SilverDim, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
        TextButton(onClick = onDismiss) {
            Text("Close", color = GtrColors.Silver)
        }
    }
}
