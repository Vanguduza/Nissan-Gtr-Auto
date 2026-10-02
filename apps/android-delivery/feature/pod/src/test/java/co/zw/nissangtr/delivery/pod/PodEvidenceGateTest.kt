package co.zw.nissangtr.delivery.pod

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PodEvidenceGateTest {

    @Test
    fun blocksWhenPhotoMissing() {
        assertEquals(
            PodEvidenceGate.MISSING_EVIDENCE,
            PodEvidenceGate.blockingReason(
                photoLocalPath = null,
                signatureLocalPath = "fake://sig.png",
                otpCode = "123456",
                otpVerified = true,
            ),
        )
        assertFalse(
            PodEvidenceGate.canSubmit(null, "fake://sig.png", "123456", true),
        )
    }

    @Test
    fun blocksWhenSignatureMissing() {
        assertEquals(
            PodEvidenceGate.MISSING_EVIDENCE,
            PodEvidenceGate.blockingReason(
                photoLocalPath = "fake://photo.jpg",
                signatureLocalPath = null,
                otpCode = "123456",
                otpVerified = true,
            ),
        )
    }

    @Test
    fun blocksWhenOtpBlankOrUnverified() {
        assertEquals(
            PodEvidenceGate.MISSING_EVIDENCE,
            PodEvidenceGate.blockingReason("p", "s", "", true),
        )
        assertEquals(
            PodEvidenceGate.OTP_NOT_VERIFIED,
            PodEvidenceGate.blockingReason("p", "s", "123456", false),
        )
    }

    @Test
    fun allowsWhenPhotoSignatureAndVerifiedOtpPresent() {
        assertNull(
            PodEvidenceGate.blockingReason(
                photoLocalPath = "fake://photo.jpg",
                signatureLocalPath = "fake://sig.png",
                otpCode = "123456",
                otpVerified = true,
            ),
        )
        assertTrue(
            PodEvidenceGate.canSubmit(
                "fake://photo.jpg",
                "fake://sig.png",
                "123456",
                true,
            ),
        )
    }
}
