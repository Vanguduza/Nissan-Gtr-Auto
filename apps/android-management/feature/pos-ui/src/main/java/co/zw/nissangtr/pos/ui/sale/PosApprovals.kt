package co.zw.nissangtr.pos.ui.sale

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import co.zw.nissangtr.pos.design.primitives.posFocusRing
import co.zw.nissangtr.pos.design.theme.PosTheme
import co.zw.nissangtr.pos.domain.model.ApprovalTarget
import co.zw.nissangtr.pos.domain.model.WaitingApproval
import co.zw.nissangtr.pos.domain.state.ApprovalsIntent
import co.zw.nissangtr.pos.domain.state.PosDestination
import co.zw.nissangtr.pos.domain.state.PosIntent
import co.zw.nissangtr.pos.domain.state.PosState
import co.zw.nissangtr.pos.ui.R
import co.zw.nissangtr.pos.ui.common.PosModal
import co.zw.nissangtr.pos.ui.common.PosRowEnd
import co.zw.nissangtr.pos.ui.common.PosText
import co.zw.nissangtr.pos.ui.common.formatMoney
import co.zw.nissangtr.pos.ui.home.SoftButton
import java.time.Duration
import java.time.Instant

/** Header pill: shown only while something waits for this person; red when any of it is urgent. */
@Composable
fun ApprovalsHeaderButton(items: List<WaitingApproval>, onClick: () -> Unit) {
    if (items.isEmpty()) return
    val palette = PosTheme.palette
    val urgent = items.count { it.urgent }
    val label = pluralStringResource(R.plurals.pos_approvals_waiting, items.size, items.size)
    PosText(
        text = label,
        style = PosTheme.type.labelMeta.copy(fontWeight = FontWeight.SemiBold),
        color = palette.surfacePrimary,
        maxLines = 1,
        modifier = Modifier
            .clip(PosTheme.shape.sm)
            .background(if (urgent > 0) palette.brandRed else palette.textPrimary)
            .posFocusRing()
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = label + if (urgent > 0) ", $urgent urgent" else "" }
            .padding(horizontal = 12.dp, vertical = 8.dp),
    )
}

/** The approvals inbox: urgent first; items the tablet decides open their screen, the rest say where on the web. */
@Composable
fun ApprovalsDialog(state: PosState, dispatch: (PosIntent) -> Unit) {
    if (!state.approvalsOpen) return
    val palette = PosTheme.palette
    PosModal(stringResource(R.string.pos_approvals_title), onDismiss = { dispatch(ApprovalsIntent.Close) }, width = 620.dp) {
        if (state.approvals.isEmpty()) {
            PosText(stringResource(R.string.pos_approvals_none), PosTheme.type.bodySecondary, palette.textMuted)
        }
        Column(Modifier.fillMaxWidth().heightIn(max = 520.dp).verticalScroll(rememberScrollState())) {
            state.approvals.forEach { a ->
                val destination = when (a.target) {
                    ApprovalTarget.Till -> PosDestination.Till
                    ApprovalTarget.Returns -> PosDestination.Returns
                    ApprovalTarget.Recovery -> PosDestination.Recovery
                    ApprovalTarget.Orders -> PosDestination.Orders
                    ApprovalTarget.Web -> null
                }
                val where = when (a.target) {
                    ApprovalTarget.Web -> stringResource(R.string.pos_approvals_on_web)
                    else -> stringResource(R.string.pos_approvals_tap_to_open)
                }
                ListRow(
                    title = (if (a.urgent) stringResource(R.string.pos_approvals_urgent) + " · " else "") + a.title,
                    subtitle = listOfNotNull(a.detail, waited(a.waitingSince), where).joinToString(" · "),
                    trailing = a.amount?.let { formatMoney(it) },
                    subtitleLines = 3,
                    onClick = destination?.let { d -> { dispatch(ApprovalsIntent.Close); dispatch(PosIntent.Navigate(d)) } },
                )
            }
        }
        PosRowEnd {
            SoftButton(stringResource(R.string.pos_close), null, enabled = true, onClick = { dispatch(ApprovalsIntent.Close) }, modifier = Modifier.widthIn(max = 140.dp))
        }
    }
}

@Composable
private fun waited(since: String?): String? {
    val t = since?.let { runCatching { Instant.parse(it) }.getOrNull() ?: runCatching { java.time.OffsetDateTime.parse(it).toInstant() }.getOrNull() } ?: return null
    val minutes = Duration.between(t, Instant.now()).toMinutes().coerceAtLeast(0)
    return when {
        minutes < 60 -> stringResource(R.string.pos_approvals_waited_min, minutes)
        minutes < 48 * 60 -> stringResource(R.string.pos_approvals_waited_h, minutes / 60)
        else -> stringResource(R.string.pos_approvals_waited_d, minutes / (24 * 60))
    }
}
