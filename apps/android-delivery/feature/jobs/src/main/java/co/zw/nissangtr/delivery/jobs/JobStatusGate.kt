package co.zw.nissangtr.delivery.jobs

import co.zw.nissangtr.delivery.rpc.DeliveryJobStatus
import co.zw.nissangtr.delivery.rpc.DeliveryJobSummary
import co.zw.nissangtr.delivery.rpc.formatReceiptRow

/**
 * Product rules: jobs stay Active until customer signature is captured via POD.
 * Complete → Done only through [submit_delivery_pod] (signature required server-side).
 * Pure JVM helpers for unit tests — no Android / RPC.
 */
object JobStatusGate {
    const val COMPLETE_REQUIRES_SIGNATURE: String =
        "Customer signature required — use Complete job → signature pad"
    const val TERMINAL_READONLY: String = "Job is terminal — receipt and address are read-only"

    fun isActive(status: String): Boolean =
        status == DeliveryJobStatus.PENDING.rpcValue ||
            status == DeliveryJobStatus.DISPATCHED.rpcValue

    fun isTerminal(status: String): Boolean =
        status == DeliveryJobStatus.COMPLETED.rpcValue ||
            status == DeliveryJobStatus.FAILED.rpcValue

    fun canOpenCompleteFlow(job: DeliveryJobSummary): Boolean = isActive(job.status)

    fun canMarkFailed(job: DeliveryJobSummary): Boolean = isActive(job.status)

    /**
     * Client-side mirror of ERP: cannot transition to completed without a signature path.
     * Live path enforces this in `update_delivery_job_status` / `submit_delivery_pod`.
     */
    fun blockingReasonForComplete(
        status: String,
        podSignaturePath: String?,
    ): String? {
        if (isTerminal(status)) return TERMINAL_READONLY
        if (podSignaturePath.isNullOrBlank()) return COMPLETE_REQUIRES_SIGNATURE
        return null
    }

    fun canTransitionToDone(
        status: String,
        podSignaturePath: String?,
    ): Boolean = blockingReasonForComplete(status, podSignaturePath) == null

    /**
     * Receipt header lines (doc / DN / settlement totals / POD).
     * Notes and bought items are separate banners — not folded into this list.
     */
    fun receiptHeaderLines(job: DeliveryJobSummary): List<String> {
        val lines = mutableListOf<String>()
        lines += "Doc ${job.documentNumber ?: job.id.take(8)}"
        lines += "Delivery note ${job.deliveryNoteId.take(8)}…"
        job.settlement?.let { s ->
            val dueMinor = s.amountDueMinor
            val due = when {
                dueMinor != null ->
                    "${s.currency.rpcValue} ${"%.2f".format(dueMinor / 100.0)}"
                s.amountDue != null ->
                    "${s.currency.rpcValue} ${"%.2f".format(s.amountDue)}"
                else -> null
            }
            if (due != null) lines += "Amount due $due"
            val paidMinor = s.amountPaidMinor
            val paid = when {
                paidMinor != null ->
                    "${s.currency.rpcValue} ${"%.2f".format(paidMinor / 100.0)}"
                s.amountPaid != null ->
                    "${s.currency.rpcValue} ${"%.2f".format(s.amountPaid)}"
                else -> null
            }
            if (paid != null) lines += "Paid $paid"
        }
        if (job.podSignaturePath != null) lines += "Signed (POD on file)"
        return lines
    }

    /** Bought items for the items banner (qty × OEM/description · amount). */
    fun receiptItemLines(job: DeliveryJobSummary): List<String> {
        if (job.lineItems.isEmpty()) {
            return listOf("No line items on delivery note")
        }
        return job.lineItems.map { it.formatReceiptRow() }
    }

    /** Driver / dispatch notes for the white notes banner (null when blank). */
    fun receiptNotes(job: DeliveryJobSummary): String? =
        job.notes?.takeIf { it.isNotBlank() }

    /**
     * Legacy flat receipt copy (header + items + notes) — prefer banner helpers
     * for UI. Kept for callers that still expect a single list.
     */
    fun receiptCopyLines(job: DeliveryJobSummary): List<String> {
        val lines = mutableListOf<String>()
        lines += receiptHeaderLines(job)
        lines += receiptItemLines(job)
        receiptNotes(job)?.let { lines += "Notes: $it" }
        return lines
    }

    /** Human-readable delivery address from geo + notes (text address when API provides). */
    fun deliveryAddressLines(job: DeliveryJobSummary): List<String> {
        val lines = mutableListOf<String>()
        job.dropoffAddressText?.takeIf { it.isNotBlank() }?.let { lines += it }
        if (job.dropoffLat != null && job.dropoffLng != null) {
            lines += "%.5f, %.5f".format(job.dropoffLat, job.dropoffLng)
        }
        // Prefer dedicated notes banner on detail; only fall back here when no address text.
        job.notes?.takeIf { it.isNotBlank() && job.dropoffAddressText.isNullOrBlank() }?.let {
            lines += it
        }
        if (lines.isEmpty()) lines += "No dropoff address on file"
        return lines
    }
}
