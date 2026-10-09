package co.zw.nissangtr.pos.domain.model

/**
 * A decision waiting for the signed-in person (`list_my_approvals`, the same list as the web
 * Approvals page). [target] is where the tablet decides it; [ApprovalTarget.Web] is decided on the
 * web back office (e.g. a requisition or a driver's delivery balance).
 */
data class WaitingApproval(
    val kind: String,
    val ref: String,
    val title: String,
    val detail: String?,
    val urgent: Boolean,
    /** ISO time the item started waiting. */
    val waitingSince: String?,
    val amount: Money?,
) {
    val target: ApprovalTarget
        get() = when (kind) {
            "till_variance" -> ApprovalTarget.Till
            "return" -> ApprovalTarget.Returns
            "card_unresolved", "split_refund" -> ApprovalTarget.Recovery
            "transfer_send" -> ApprovalTarget.Orders
            else -> ApprovalTarget.Web
        }
}

enum class ApprovalTarget { Till, Returns, Recovery, Orders, Web }
