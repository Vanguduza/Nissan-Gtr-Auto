package co.zw.nissangtr.delivery.pod

/**
 * Product rules for POD confirm: photo evidence + customer signature + verified OTP.
 * Pure gate for JVM unit tests — no Android / RPC.
 */
object PodEvidenceGate {
    const val MISSING_EVIDENCE: String = "Photo, signature, and OTP required"
    const val OTP_NOT_VERIFIED: String = "Verify OTP before completing POD"

    fun blockingReason(
        photoLocalPath: String?,
        signatureLocalPath: String?,
        otpCode: String,
        otpVerified: Boolean,
    ): String? {
        if (photoLocalPath.isNullOrBlank() ||
            signatureLocalPath.isNullOrBlank() ||
            otpCode.isBlank()
        ) {
            return MISSING_EVIDENCE
        }
        if (!otpVerified) return OTP_NOT_VERIFIED
        return null
    }

    fun canSubmit(
        photoLocalPath: String?,
        signatureLocalPath: String?,
        otpCode: String,
        otpVerified: Boolean,
    ): Boolean = blockingReason(
        photoLocalPath,
        signatureLocalPath,
        otpCode,
        otpVerified,
    ) == null
}
