package co.zw.nissangtr.customer.catalog

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import co.zw.nissangtr.customer.rpc.SelectedFitmentVehicle
import co.zw.nissangtr.customer.rpc.VehicleCascade
import co.zw.nissangtr.customer.rpc.VehicleMasterRow

private val FieldShape = RoundedCornerShape(14.dp)
private val CtaShape = RoundedCornerShape(20.dp)

@Composable
fun VehicleSelectorSection(
    vehicleRows: List<VehicleMasterRow>,
    confirmedVehicle: SelectedFitmentVehicle?,
    busy: Boolean,
    error: String?,
    onConfirmCascade: (maker: String, model: String, generation: String, engine: String?) -> Unit,
    onConfirmVin: (vin: String) -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
    sectionTitle: String = "Select vehicle",
    confirmLabel: String = "Confirm vehicle",
    showClear: Boolean = true,
) {
    var maker by remember { mutableStateOf("") }
    var model by remember { mutableStateOf("") }
    var generation by remember { mutableStateOf("") }
    var engine by remember { mutableStateOf("") }
    var vin by remember { mutableStateOf("") }

    LaunchedEffect(confirmedVehicle) {
        val v = confirmedVehicle
        if (v != null) {
            maker = v.make.orEmpty()
            model = v.model
            generation = v.generation
            engine = v.engine.orEmpty()
            vin = v.vin.orEmpty()
        }
    }

    val makers = remember(vehicleRows) { VehicleCascade.makers(vehicleRows) }
    val models = remember(vehicleRows, maker) {
        if (maker.isEmpty()) emptyList() else VehicleCascade.models(vehicleRows, maker)
    }
    val generations = remember(vehicleRows, maker, model) {
        if (maker.isEmpty() || model.isEmpty()) emptyList()
        else VehicleCascade.generations(vehicleRows, maker, model)
    }
    val engines = remember(vehicleRows, maker, model, generation) {
        if (maker.isEmpty() || model.isEmpty() || generation.isEmpty()) emptyList()
        else VehicleCascade.engines(vehicleRows, maker, model, generation)
    }

    val canCascade = maker.isNotEmpty() && model.isNotEmpty() && generation.isNotEmpty() &&
        (engines.isEmpty() || engine.isNotEmpty())
    val canVin = vin.trim().length in 11..17
    val showingConfirmed = confirmedVehicle != null &&
        maker == confirmedVehicle.make.orEmpty() &&
        model == confirmedVehicle.model &&
        generation == confirmedVehicle.generation &&
        engine == (confirmedVehicle.engine.orEmpty())

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(sectionTitle, style = MaterialTheme.typography.titleSmall)
        if (!busy && makers.isEmpty()) {
            Text(
                "Maker stays locked until the live vehicle catalog returns rows.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            CascadeDropdown(
                label = "Maker",
                value = maker,
                options = makers,
                enabled = makers.isNotEmpty() && !busy,
                dimmed = showingConfirmed,
                onSelect = {
                    maker = it
                    model = ""
                    generation = ""
                    engine = ""
                },
                modifier = Modifier.weight(1f),
            )
            CascadeDropdown(
                label = "Model",
                value = model,
                options = models,
                enabled = maker.isNotEmpty() && !busy,
                dimmed = showingConfirmed,
                onSelect = {
                    model = it
                    generation = ""
                    engine = ""
                },
                modifier = Modifier.weight(1f),
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            CascadeDropdown(
                label = "Generation",
                value = generation,
                options = generations,
                enabled = model.isNotEmpty() && !busy,
                dimmed = showingConfirmed,
                onSelect = {
                    generation = it
                    engine = ""
                },
                modifier = Modifier.weight(1f),
            )
            CascadeDropdown(
                label = "Engine",
                value = engine,
                options = engines,
                enabled = generation.isNotEmpty() && engines.isNotEmpty() && !busy,
                dimmed = showingConfirmed,
                onSelect = { engine = it },
                modifier = Modifier.weight(1f),
            )
        }

        OutlinedTextField(
            value = vin,
            onValueChange = { raw ->
                vin = raw.uppercase().filter { it.isLetterOrDigit() }.take(17)
            },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            enabled = !busy,
            label = { Text("VIN / chassis") },
            shape = FieldShape,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
            colors = if (showingConfirmed) {
                OutlinedTextFieldDefaults.colors(
                    focusedTextColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f),
                    unfocusedTextColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f),
                    disabledTextColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f),
                )
            } else {
                OutlinedTextFieldDefaults.colors()
            },
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Button(
                onClick = {
                    if (canVin) {
                        onConfirmVin(vin.trim())
                    } else if (canCascade) {
                        onConfirmCascade(
                            maker,
                            model,
                            generation,
                            engine.takeIf { it.isNotEmpty() },
                        )
                    }
                },
                enabled = !busy && (canVin || canCascade),
                modifier = Modifier.weight(1f),
                shape = CtaShape,
            ) {
                Text(if (busy) "Working…" else confirmLabel)
            }
            if (showClear) {
                TextButton(
                    onClick = {
                        maker = ""
                        model = ""
                        generation = ""
                        engine = ""
                        vin = ""
                        onClear()
                    },
                    enabled = !busy,
                ) {
                    Text("Clear")
                }
            }
        }

        error?.let {
            Text(
                it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}

@Composable
private fun CascadeDropdown(
    label: String,
    value: String,
    options: List<String>,
    enabled: Boolean,
    dimmed: Boolean,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    val dimColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f)
    Box(modifier = modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = value,
            onValueChange = {},
            readOnly = true,
            enabled = enabled,
            label = { Text(label) },
            trailingIcon = {
                Icon(Icons.Filled.ArrowDropDown, contentDescription = null)
            },
            colors = if (dimmed) {
                OutlinedTextFieldDefaults.colors(
                    focusedTextColor = dimColor,
                    unfocusedTextColor = dimColor,
                    disabledTextColor = dimColor,
                    focusedLabelColor = dimColor,
                    unfocusedLabelColor = dimColor,
                    disabledLabelColor = dimColor,
                    focusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f),
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f),
                    disabledBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f),
                )
            } else {
                OutlinedTextFieldDefaults.colors()
            },
            shape = FieldShape,
            modifier = Modifier.fillMaxWidth(),
        )
        if (enabled) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .clickable { expanded = true },
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            if (options.isEmpty()) {
                DropdownMenuItem(
                    text = { Text("No options in catalog") },
                    onClick = { expanded = false },
                    enabled = false,
                )
            } else {
                options.forEach { opt ->
                    DropdownMenuItem(
                        text = { Text(opt) },
                        onClick = {
                            onSelect(opt)
                            expanded = false
                        },
                    )
                }
            }
        }
    }
}