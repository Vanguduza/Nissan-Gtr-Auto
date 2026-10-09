package co.zw.nissangtr.pos.ui.sale

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import co.zw.nissangtr.pos.ui.common.PosField
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import co.zw.nissangtr.pos.design.icons.PosIcons
import co.zw.nissangtr.pos.design.icons.Smartphone
import co.zw.nissangtr.pos.design.theme.PosTheme
import co.zw.nissangtr.pos.domain.model.CompanionStatus
import co.zw.nissangtr.pos.domain.state.CompanionIntent
import co.zw.nissangtr.pos.domain.state.PosIntent
import co.zw.nissangtr.pos.domain.state.PosState
import co.zw.nissangtr.pos.ui.R
import co.zw.nissangtr.pos.ui.common.PosIcon
import co.zw.nissangtr.pos.ui.common.PosModal
import co.zw.nissangtr.pos.ui.common.PosPrimaryButton
import co.zw.nissangtr.pos.ui.common.PosRowEnd
import co.zw.nissangtr.pos.ui.common.PosText
import co.zw.nissangtr.pos.ui.home.SoftButton
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Pairing code for the staff phone (web: `CompanionDialog`). The till never opens a camera for
 * this; the phone claims the code and scans into the open sale, which the till picks up by polling.
 */
@Composable
fun CompanionDialog(state: PosState, dispatch: (PosIntent) -> Unit, now: LocalDateTime) {
    val palette = PosTheme.palette
    val type = PosTheme.type
    val session = state.companion
    val expiresAt = session?.expiresAtIso?.let { runCatching { OffsetDateTime.parse(it).atZoneSameInstant(ZoneId.systemDefault()).toLocalDateTime() }.getOrNull() }
    // The server expires codes; the till also stops offering one whose time has passed.
    val status = session?.status?.let { if (it == CompanionStatus.Open && expiresAt != null && now.isAfter(expiresAt)) CompanionStatus.Expired else it }
    PosModal(stringResource(R.string.pos_companion_title), onDismiss = { dispatch(CompanionIntent.Close) }, width = 460.dp) {
        if (session == null) {
            PosText(stringResource(R.string.pos_loading), type.bodyPrimary, palette.textSecondary)
        } else {
            val live = status == CompanionStatus.Open || status == CompanionStatus.Claimed
            if (status == CompanionStatus.Open) {
                PosText(stringResource(R.string.pos_companion_steps), type.bodyPrimary, palette.textSecondary)
                Spacer(Modifier.height(14.dp))
                val spoken = session.pairingCode.toCharArray().joinToString(" ")
                PosText(
                    session.pairingCode.chunked(3).joinToString(" "),
                    type.numericTotal.copy(fontFamily = FontFamily.Monospace, fontSize = 44.sp, fontWeight = FontWeight.Bold, letterSpacing = 6.sp),
                    palette.textPrimary,
                    align = TextAlign.Center,
                    maxLines = 1,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(PosTheme.shape.md)
                        .background(palette.canvas)
                        .padding(vertical = 18.dp)
                        .semantics { contentDescription = spoken },
                )
                expiresAt?.let {
                    Spacer(Modifier.height(8.dp))
                    PosText(
                        stringResource(R.string.pos_companion_expires, it.format(DateTimeFormatter.ofPattern("HH:mm"))),
                        type.labelMeta,
                        palette.textMuted,
                        align = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                Spacer(Modifier.height(12.dp))
            }
            Row(
                Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PosIcon(PosIcons.Smartphone, tint = if (status == CompanionStatus.Claimed) palette.success else palette.textSecondary, size = 18.dp)
                Spacer(Modifier.width(8.dp))
                PosText(
                    stringResource(
                        when (status) {
                            CompanionStatus.Open -> R.string.pos_companion_waiting
                            CompanionStatus.Claimed -> R.string.pos_companion_claimed
                            CompanionStatus.Expired -> R.string.pos_companion_expired
                            else -> R.string.pos_companion_ended
                        },
                    ),
                    type.bodyPrimary.copy(fontWeight = FontWeight.SemiBold),
                    if (status == CompanionStatus.Claimed) palette.success else palette.textPrimary,
                )
            }
            if (status == CompanionStatus.Claimed) {
                Spacer(Modifier.height(6.dp))
                PosText(pluralStringResource(R.plurals.pos_items, state.cart.lines.size, state.cart.lines.size), type.labelMeta, palette.textMuted)
            }
            PosRowEnd {
                if (live) {
                    SoftButton(stringResource(R.string.pos_companion_end), null, enabled = true, onClick = { dispatch(CompanionIntent.End) }, modifier = Modifier.width(150.dp))
                    PosPrimaryButton(stringResource(R.string.pos_done), enabled = true, onClick = { dispatch(CompanionIntent.Close) })
                } else {
                    SoftButton(stringResource(R.string.pos_close), null, enabled = true, onClick = { dispatch(CompanionIntent.End) }, modifier = Modifier.width(120.dp))
                    PosPrimaryButton(
                        stringResource(R.string.pos_companion_pair_again),
                        enabled = state.online,
                        onClick = { dispatch(CompanionIntent.End); dispatch(CompanionIntent.Open) },
                    )
                }
            }
        }
    }
}

/**
 * Phone half of the pairing: claim the till's code, then every camera scan (host scanner bridge)
 * goes into the till's sale. Same staff account as the till; the server refuses cross-rep claims.
 */
@Composable
fun ScannerDialog(state: PosState, dispatch: (PosIntent) -> Unit, onScan: () -> Unit) {
    val palette = PosTheme.palette
    val type = PosTheme.type
    val link = state.scanner
    var code by rememberSaveable { mutableStateOf("") }
    PosModal(stringResource(R.string.pos_scanner_link_title), onDismiss = { dispatch(CompanionIntent.CloseScanner) }, width = 460.dp) {
        if (link == null) {
            PosText(stringResource(R.string.pos_scanner_link_steps), type.bodyPrimary, palette.textSecondary)
            Spacer(Modifier.height(12.dp))
            PosField(
                label = stringResource(R.string.pos_scanner_link_code),
                value = code,
                onChange = { v -> code = v.filter { it.isDigit() }.take(6) },
                keyboard = KeyboardType.NumberPassword,
                placeholder = "000000",
            )
            PosRowEnd {
                PosPrimaryButton(
                    stringResource(if (state.scannerClaiming) R.string.pos_loading else R.string.pos_scanner_link_connect),
                    enabled = code.length == 6 && !state.scannerClaiming && state.online,
                    onClick = { dispatch(CompanionIntent.Claim(code)) },
                )
            }
        } else {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }) {
                PosIcon(PosIcons.Smartphone, tint = palette.success, size = 18.dp)
                Spacer(Modifier.width(8.dp))
                PosText(stringResource(R.string.pos_scanner_link_live), type.bodyPrimary.copy(fontWeight = FontWeight.SemiBold), palette.success)
            }
            Spacer(Modifier.height(14.dp))
            PosPrimaryButton(
                stringResource(if (link.busy) R.string.pos_loading else R.string.pos_scanner_link_scan),
                enabled = !link.busy && state.online,
                onClick = onScan,
                icon = PosIcons.Scan,
                modifier = Modifier.fillMaxWidth(),
            )
            if (link.scans.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                PosText(stringResource(R.string.pos_scanner_link_recent), type.labelMeta.copy(fontWeight = FontWeight.SemiBold), palette.textMuted)
                link.scans.forEach { PosText("✓  $it", type.bodySecondary, palette.textPrimary, maxLines = 1) }
            }
            PosRowEnd {
                SoftButton(stringResource(R.string.pos_scanner_link_leave), null, enabled = true, onClick = { dispatch(CompanionIntent.LeaveScanner) }, modifier = Modifier.width(150.dp))
            }
        }
    }
}
