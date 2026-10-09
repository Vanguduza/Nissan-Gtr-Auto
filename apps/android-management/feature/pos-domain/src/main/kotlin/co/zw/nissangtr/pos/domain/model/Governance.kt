package co.zw.nissangtr.pos.domain.model

/**
 * `pos_approval_policies` row: when an action needs a manager and whether it needs a reason.
 * [thresholdValue] is in the action's own unit (percent for discount and price override).
 */
data class ApprovalPolicy(
    val action: String,
    val thresholdValue: Double,
    val alwaysRequireManager: Boolean,
    val reasonRequired: Boolean,
)

/** The policy / reason-code action a request is governed by (`pos_approval_reason_codes.action`). */
val ApprovalRequest.policyAction: String
    get() = when (this) {
        is ApprovalRequest.Discount -> "discount_percent"
        is ApprovalRequest.PriceOverride -> "price_override_delta_percent"
        ApprovalRequest.VoidSale -> "void_cart"
        is ApprovalRequest.Refund -> "refund_full_invoice"
        is ApprovalRequest.CashOut -> ReasonAction.CASH_OUT
        is ApprovalRequest.TillVariance -> ReasonAction.TILL_VARIANCE
        is ApprovalRequest.Handover -> "till_handover"
        is ApprovalRequest.RepairPaidOrder -> "repair_paid_order"
        is ApprovalRequest.SplitRefund -> "split_refund"
        is ApprovalRequest.ReturnPost -> "return_post"
        is ApprovalRequest.CoreReturn -> "core_return"
        is ApprovalRequest.WarrantyDecide -> "warranty_decision"
        is ApprovalRequest.CardRefund, is ApprovalRequest.CardRefundFinish -> "refund_full_invoice"
    }

/** Governed sale actions pick a configured reason; drawer and recovery approvals do not. */
val ApprovalRequest.choosesReason: Boolean
    get() = this !is ApprovalRequest.TillAction && this !is ApprovalRequest.RepairPaidOrder && this !is ApprovalRequest.SplitRefund && !returnsFlow

/** Returns, cores, warranty and card refunds chose their reason (if any) before asking an approver. */
val ApprovalRequest.returnsFlow: Boolean
    get() = this is ApprovalRequest.ReturnPost || this is ApprovalRequest.CoreReturn || this is ApprovalRequest.WarrantyDecide ||
        this is ApprovalRequest.CardRefund || this is ApprovalRequest.CardRefundFinish

/** Drawer actions always need a manager on the server, whatever the policy table says. */
val ApprovalPolicy.managerFixed: Boolean
    get() = action == ReasonAction.CASH_OUT || action == ReasonAction.TILL_VARIANCE

/** Whether the threshold means anything for this action (it carries a percent). */
val ApprovalPolicy.hasThreshold: Boolean
    get() = action == "discount_percent" || action == "price_override_delta_percent"

/** Actions that change the open sale: refused while it is reserved for payment. */
val ApprovalRequest.editsSale: Boolean
    get() = this is ApprovalRequest.Discount || this is ApprovalRequest.PriceOverride || this == ApprovalRequest.VoidSale
