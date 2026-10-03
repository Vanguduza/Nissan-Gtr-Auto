package co.zw.nissangtr.pos.ui.sale

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import co.zw.nissangtr.pos.design.theme.PosTheme
import co.zw.nissangtr.pos.domain.model.ApprovalPolicy
import co.zw.nissangtr.pos.domain.model.hasThreshold
import co.zw.nissangtr.pos.domain.model.managerFixed
import co.zw.nissangtr.pos.domain.state.GovernanceIntent
import co.zw.nissangtr.pos.domain.state.PosIntent
import co.zw.nissangtr.pos.domain.state.PosState
import co.zw.nissangtr.pos.ui.R
import co.zw.nissangtr.pos.ui.common.PosField
import co.zw.nissangtr.pos.ui.common.PosModal
import co.zw.nissangtr.pos.ui.common.PosPrimaryButton
import co.zw.nissangtr.pos.ui.common.PosRowEnd
import co.zw.nissangtr.pos.ui.common.PosSegmented
import co.zw.nissangtr.pos.ui.common.PosText
import co.zw.nissangtr.pos.ui.common.feedbackText
import co.zw.nissangtr.pos.ui.common.formatQty
import co.zw.nissangtr.pos.ui.home.EmptyCard
import co.zw.nissangtr.pos.ui.home.SoftButton

@Composable
private fun policyLabel(action: String): String = when (action) {
    "discount_percent" -> stringResource(R.string.pos_policy_action_discount_percent)
    "price_override_delta_percent" -> stringResource(R.string.pos_policy_action_price_override_delta_percent)
    "void_cart" -> stringResource(R.string.pos_policy_action_void_cart)
    "refund_full_invoice" -> stringResource(R.string.pos_policy_action_refund_full_invoice)
    "return_post" -> stringResource(R.string.pos_policy_action_return_post)
    "core_return" -> stringResource(R.string.pos_policy_action_core_return)
    "warranty_decision" -> stringResource(R.string.pos_policy_action_warranty_decision)
    "cash_out" -> stringResource(R.string.pos_policy_action_cash_out)
    "till_variance" -> stringResource(R.string.pos_policy_action_till_variance)
    else -> action
}

@Composable
private fun policySummary(p: ApprovalPolicy): String {
    val manager = when {
        p.alwaysRequireManager || p.managerFixed -> stringResource(R.string.pos_policy_always_manager)
        p.hasThreshold -> stringResource(R.string.pos_policy_manager_above, formatQty(p.thresholdValue))
        else -> stringResource(R.string.pos_policy_no_manager)
    }
    return manager + " · " + stringResource(if (p.reasonRequired) R.string.pos_policy_reason_required else R.string.pos_policy_reason_optional)
}

/**
 * Settings → Approval policies (`list_pos_approval_policies` / `set_pos_approval_policy`). Everyone
 * sees the rules the counter works under; only an admin can save (server-enforced).
 */
@Composable
fun ApprovalPoliciesRow(state: PosState, dispatch: (PosIntent) -> Unit) {
    var open by rememberSaveable { mutableStateOf(false) }
    var editing by rememberSaveable { mutableStateOf<String?>(null) }
    ListRow(stringResource(R.string.pos_policies_title), stringResource(R.string.pos_policies_hint)) {
        SoftButton(
            stringResource(R.string.pos_open),
            null,
            enabled = state.online,
            onClick = {
                open = true
                dispatch(GovernanceIntent.LoadPolicies)
            },
            modifier = Modifier.width(160.dp),
        )
    }
    if (!open) return
    val policies = state.policies
    val current = policies?.firstOrNull { it.action == editing }
    if (current != null) {
        PolicyEditor(state, current, onDone = { editing = null }, dispatch = dispatch)
        return
    }
    PosModal(stringResource(R.string.pos_policies_title), onDismiss = { open = false }) {
        Column(Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState())) {
            when {
                policies == null -> EmptyCard(stringResource(R.string.pos_loading))
                else -> policies.forEach { p ->
                    ListRow(policyLabel(p.action), policySummary(p)) {
                        SoftButton(stringResource(R.string.pos_edit), null, enabled = state.online, onClick = { editing = p.action }, modifier = Modifier.width(110.dp))
                    }
                }
            }
        }
        state.feedback?.let { PosText(feedbackText(it), PosTheme.type.bodyPrimary, PosTheme.palette.error, modifier = Modifier.padding(top = 10.dp)) }
    }
}

@Composable
private fun PolicyEditor(state: PosState, policy: ApprovalPolicy, onDone: () -> Unit, dispatch: (PosIntent) -> Unit) {
    var always by rememberSaveable(policy.action) { mutableStateOf(policy.alwaysRequireManager) }
    var threshold by rememberSaveable(policy.action) { mutableStateOf(formatQty(policy.thresholdValue)) }
    var reason by rememberSaveable(policy.action) { mutableStateOf(policy.reasonRequired) }
    val value = threshold.trim().toDoubleOrNull()
    PosModal(policyLabel(policy.action), onDismiss = onDone) {
        PosText(stringResource(R.string.pos_policy_always_manager), PosTheme.type.labelMeta, PosTheme.palette.textMuted)
        PosSegmented(
            listOf(true, false),
            always || policy.managerFixed,
            label = { stringResource(if (it) R.string.pos_on else R.string.pos_off) },
            onSelect = { if (!policy.managerFixed) always = it },
        )
        if (policy.managerFixed) {
            PosText(stringResource(R.string.pos_policy_fixed), PosTheme.type.bodySecondary, PosTheme.palette.textMuted, modifier = Modifier.padding(top = 6.dp))
        }
        if (policy.hasThreshold && !always) {
            Spacer(Modifier.height(10.dp))
            PosField(stringResource(R.string.pos_policy_threshold), threshold, { threshold = it }, keyboard = KeyboardType.Decimal)
        }
        Spacer(Modifier.height(10.dp))
        PosText(stringResource(R.string.pos_policy_reason_toggle), PosTheme.type.labelMeta, PosTheme.palette.textMuted)
        PosSegmented(
            listOf(true, false),
            reason,
            label = { stringResource(if (it) R.string.pos_on else R.string.pos_off) },
            onSelect = { reason = it },
        )
        state.feedback?.let { PosText(feedbackText(it), PosTheme.type.bodyPrimary, PosTheme.palette.error, modifier = Modifier.padding(top = 10.dp)) }
        PosRowEnd {
            SoftButton(stringResource(R.string.pos_cancel), null, enabled = true, onClick = onDone, modifier = Modifier.widthIn(max = 140.dp))
            PosPrimaryButton(
                stringResource(R.string.pos_save),
                enabled = state.online && value != null && value >= 0,
                onClick = {
                    dispatch(GovernanceIntent.SavePolicy(policy.copy(thresholdValue = value ?: 0.0, alwaysRequireManager = always || policy.managerFixed, reasonRequired = reason)))
                    onDone()
                },
            )
        }
    }
}
