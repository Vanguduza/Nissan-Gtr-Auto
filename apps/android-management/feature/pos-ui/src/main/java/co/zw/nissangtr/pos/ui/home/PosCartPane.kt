package co.zw.nissangtr.pos.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import co.zw.nissangtr.pos.design.icons.ArrowRight
import co.zw.nissangtr.pos.design.icons.CreditCard
import co.zw.nissangtr.pos.design.icons.Pause
import co.zw.nissangtr.pos.design.icons.PosIcons
import co.zw.nissangtr.pos.design.icons.Trash2
import co.zw.nissangtr.pos.design.primitives.posFocusRing
import co.zw.nissangtr.pos.design.primitives.posNeuRaised
import co.zw.nissangtr.pos.design.primitives.posNeuRaisedSmall
import co.zw.nissangtr.pos.design.theme.PosTheme
import co.zw.nissangtr.pos.domain.model.CartLine
import co.zw.nissangtr.pos.domain.model.CartProjection
import co.zw.nissangtr.pos.ui.R
import co.zw.nissangtr.pos.ui.common.PosIcon
import co.zw.nissangtr.pos.ui.common.PosText
import co.zw.nissangtr.pos.ui.common.formatMoney
import co.zw.nissangtr.pos.ui.common.formatQty

/**
 * Cart pane (Blueprint §6.5): header · scrollable lines · Add Customer · subtotal / discount ·
 * divider · total · Proceed to Payment. Only the line list scrolls. Every money row is the
 * backend's value with its currency; the zero discount row stays visible (CART-08).
 */
@Composable
fun PosCartPane(
    cart: CartProjection,
    busy: Boolean,
    customerName: String?,
    onQty: (CartLine, Double) -> Unit,
    onRemove: (CartLine) -> Unit,
    /** Voids the whole sale; needs manager approval. */
    onClear: () -> Unit,
    onAddCustomer: () -> Unit,
    onPay: () -> Unit,
    onPark: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = PosTheme.palette
    val type = PosTheme.type
    Column(
        modifier
            .fillMaxSize()
            .padding(start = 0.dp, top = 0.dp, end = 16.dp, bottom = 16.dp)
            .posNeuRaised(cornerRadius = 22.dp)
            .clip(PosTheme.shape.lg)
            .background(palette.surfacePrimary)
            .padding(20.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            PosText(stringResource(R.string.pos_cart_title), type.heading2.copy(fontWeight = FontWeight.Bold), palette.textPrimary, modifier = Modifier.weight(1f))
            if (!cart.isEmpty) {
                Row(
                    Modifier
                        .clip(PosTheme.shape.sm)
                        .clickable(enabled = !busy, role = Role.Button, onClick = onClear)
                        .padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    PosIcon(PosIcons.Trash2, tint = palette.brandRed, size = 16.dp)
                    PosText(stringResource(R.string.pos_cart_clear), type.labelAction, palette.brandRed)
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Box(Modifier.weight(1f).fillMaxWidth()) {
            if (cart.isEmpty) {
                PosText(
                    stringResource(R.string.pos_cart_empty),
                    type.bodyPrimary,
                    palette.textMuted,
                    align = TextAlign.Center,
                    modifier = Modifier.align(Alignment.Center).padding(horizontal = 12.dp),
                )
            } else {
                LazyColumn(Modifier.fillMaxSize()) {
                    items(cart.lines, key = { it.lineId }) { line ->
                        CartRow(line, busy, onQty, onRemove)
                        Box(Modifier.fillMaxWidth().height(1.dp).background(palette.borderSubtle))
                    }
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        SoftButton(customerName ?: stringResource(R.string.pos_add_customer), PosIcons.User, enabled = true, onClick = onAddCustomer)
        Spacer(Modifier.height(16.dp))
        Box(Modifier.fillMaxWidth().height(1.dp).background(palette.borderSubtle))
        Spacer(Modifier.height(12.dp))
        MoneyRow(stringResource(R.string.pos_subtotal), formatMoney(cart.subtotal))
        Spacer(Modifier.height(6.dp))
        MoneyRow(stringResource(R.string.pos_discount), formatMoney(cart.discount))
        Spacer(Modifier.height(12.dp))
        Box(Modifier.fillMaxWidth().height(1.dp).background(palette.borderSubtle))
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            PosText(stringResource(R.string.pos_total), type.heading2.copy(fontWeight = FontWeight.Bold), palette.textPrimary, modifier = Modifier.weight(1f))
            PosText(formatMoney(cart.total), type.numericTotal.copy(fontSize = type.numericTotal.fontSize * 1.25f), palette.textPrimary, maxLines = 1)
        }
        Spacer(Modifier.height(16.dp))
        val payEnabled = !cart.isEmpty && !busy
        Row(
            Modifier
                .fillMaxWidth()
                .height(56.dp)
                .alpha(if (payEnabled) 1f else 0.45f)
                .posNeuRaised()
                .clip(PosTheme.shape.md)
                .background(palette.brandRed)
                .posFocusRing(PosTheme.shape.md)
                .clickable(enabled = payEnabled, role = Role.Button, onClick = onPay),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PosIcon(PosIcons.CreditCard, tint = palette.textOnBrand, size = 20.dp)
            Spacer(Modifier.width(10.dp))
            PosText(stringResource(R.string.pos_proceed_payment), type.heading3.copy(fontWeight = FontWeight.Bold), palette.textOnBrand)
            Spacer(Modifier.width(10.dp))
            PosIcon(PosIcons.ArrowRight, tint = palette.textOnBrand, size = 20.dp)
        }
        if (!cart.isEmpty) {
            Spacer(Modifier.height(8.dp))
            Row(
                Modifier
                    .align(Alignment.CenterHorizontally)
                    .clip(PosTheme.shape.sm)
                    .clickable(enabled = !busy, role = Role.Button, onClick = onPark)
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                PosIcon(PosIcons.Pause, tint = palette.brandRed, size = 14.dp)
                PosText(stringResource(R.string.pos_park_sale), type.labelAction, palette.brandRed)
            }
        }
    }
}

@Composable
private fun CartRow(line: CartLine, busy: Boolean, onQty: (CartLine, Double) -> Unit, onRemove: (CartLine) -> Unit) {
    val palette = PosTheme.palette
    val type = PosTheme.type
    val haptics = LocalHapticFeedback.current
    Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.Top) {
        Box(
            Modifier.size(52.dp).clip(PosTheme.shape.sm).background(palette.canvas),
            contentAlignment = Alignment.Center,
        ) {
            PosIcon(PosIcons.Package, tint = palette.textMuted, size = 22.dp)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            PosText(line.name, type.labelAction.copy(fontWeight = FontWeight.SemiBold), palette.textPrimary, maxLines = 2)
            PosText(line.oemPartNumber, type.monoReference, palette.textMuted, maxLines = 1)
            Spacer(Modifier.height(4.dp))
            PosText(formatMoney(line.unitPrice), type.numericPrice, palette.textPrimary, maxLines = 1)
        }
        Column(horizontalAlignment = Alignment.End) {
            Box(
                Modifier
                    .size(32.dp)
                    .clip(PosTheme.shape.sm)
                    .posFocusRing()
                    .clickable(enabled = !busy, role = Role.Button) { onRemove(line) },
                contentAlignment = Alignment.Center,
            ) {
                PosIcon(PosIcons.Trash2, tint = palette.textSecondary, size = 16.dp, contentDescription = stringResource(R.string.pos_remove_line, line.name))
            }
            Spacer(Modifier.height(6.dp))
            if (!line.isCoreCharge) {
                Row(
                    Modifier
                        .posNeuRaisedSmall()
                        .clip(PosTheme.shape.sm)
                        .background(palette.surfacePrimary),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    StepButton(PosIcons.Minus, stringResource(R.string.pos_qty_decrease), palette.textSecondary, enabled = !busy) {
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onQty(line, line.qty - 1)
                    }
                    PosText(formatQty(line.qty), type.numericQuantity, palette.textPrimary, align = TextAlign.Center, modifier = Modifier.width(28.dp))
                    StepButton(PosIcons.Plus, stringResource(R.string.pos_qty_increase), palette.brandRed, enabled = !busy) {
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onQty(line, line.qty + 1)
                    }
                }
            }
        }
    }
}

@Composable
private fun StepButton(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, tint: androidx.compose.ui.graphics.Color, enabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .size(32.dp)
            .posFocusRing()
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        PosIcon(icon, tint = tint, size = 16.dp, contentDescription = label)
    }
}

@Composable
private fun MoneyRow(label: String, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        PosText(label, PosTheme.type.bodyPrimary, PosTheme.palette.textSecondary, modifier = Modifier.weight(1f))
        PosText(value, PosTheme.type.numericPrice, PosTheme.palette.textPrimary, maxLines = 1)
    }
}
