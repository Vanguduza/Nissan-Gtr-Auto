package co.zw.nissangtr.pos.domain.model

/*
 * Payment resolution letters (Blueprint §10.7, §10.11, phase 8): when a payment cannot be proven
 * either way, a manager, finance or admin issues a signed letter recording what was observed. The
 * letter is a frozen copy on the server, signed with the issuer's own stored signature.
 */

/** What a letter is about (`payment_resolution_source_kind` + id). */
data class LetterSource(val kind: String, val id: String) {
    val key: String get() = "$kind:$id"

    companion object {
        fun cardTerminal(attemptId: String) = LetterSource("card_terminal", attemptId)
        fun splitRefund(refundId: String) = LetterSource("split_refund", refundId)
        /** EcoCash, Paynow or ContiPay payment in flight; null for other providers. */
        fun provider(provider: String?, intentId: String?): LetterSource? =
            if (intentId != null && provider in setOf("ecocash", "paynow", "contipay")) LetterSource(provider!!, intentId) else null
    }
}

data class PaymentLetterSummary(
    val id: String,
    val documentNumber: String?,
    val provider: String?,
    val observedStatus: String?,
    val amount: Money,
    val customerName: String?,
    val invoiceNumber: String?,
    val managerName: String?,
    val managerTitle: String?,
    val issuedAtIso: String,
)

data class BusinessProfile(
    val legalName: String,
    val tradingName: String,
    val domain: String,
    val city: String?,
    val country: String?,
    val addressLine1: String?,
    val addressLine2: String?,
    val phone: String?,
    val email: String?,
    val registrationNumber: String?,
)

/** A letter as printed: summary, its frozen detail fields, the business header and the signature image. */
data class PaymentLetter(
    val summary: PaymentLetterSummary,
    val externalReference: String?,
    val providerReference: String?,
    val terminalTransactionId: String?,
    val rrn: String?,
    val authorizationCode: String?,
    val cardLast4: String?,
    val cardScheme: String?,
    val failureDetail: String?,
    val managerEmployeeCode: String?,
    val issueNotes: String?,
    val business: BusinessProfile?,
    /** PNG/JPEG bytes; null when this user may not read the issuer's signature. */
    val signature: ByteArray?,
    val signatureSha256: String?,
) {
    override fun equals(other: Any?) = other is PaymentLetter && other.summary == summary
    override fun hashCode() = summary.hashCode()
}

data class MySignature(val fullName: String, val employeeCode: String?, val hasSignature: Boolean, val image: ByteArray?) {
    override fun equals(other: Any?) = other is MySignature && other.fullName == fullName && other.hasSignature == hasSignature && other.image.contentEqualsNullable(image)
    override fun hashCode() = fullName.hashCode() * 31 + hasSignature.hashCode()
}

private fun ByteArray?.contentEqualsNullable(o: ByteArray?) = if (this == null || o == null) this === o else contentEquals(o)
