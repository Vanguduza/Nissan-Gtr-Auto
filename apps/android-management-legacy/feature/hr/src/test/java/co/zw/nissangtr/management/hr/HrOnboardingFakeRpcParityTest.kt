package co.zw.nissangtr.management.hr

import co.zw.nissangtr.management.rpc.FakeRpcClient
import co.zw.nissangtr.management.rpc.HrOnboardingStage
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HrOnboardingFakeRpcParityTest {

    @Test
    fun saveAdvanceCompleteAndCreateAuth_fakePath() = runBlocking {
        val rpc = FakeRpcClient()
        val drafts = rpc.listHrOnboardingDrafts()
        assertTrue(drafts.any { it.id == FakeRpcClient.FAKE_HR_DRAFT_ID })

        val grades = rpc.listHrGrades()
        assertTrue(grades.any { it.id == FakeRpcClient.FAKE_HR_GRADE_ID })

        val draftId = rpc.saveHrOnboardingStage(
            draftId = null,
            stage = HrOnboardingStage.BANKING_HEALTH,
            payload = mapOf(
                "full_name" to "Ada Onboard",
                "email" to "ada@example.com",
                "phone_e164" to "+263771111222",
                "grade_id" to FakeRpcClient.FAKE_HR_GRADE_ID,
            ),
            bankingJson = mapOf(
                "bank_name" to "CBZ",
                "account_number" to "123456",
                "branch" to "Harare",
            ),
            healthJson = mapOf("medical_aid" to "Premier"),
        )
        assertTrue(draftId.isNotBlank())

        rpc.saveHrOnboardingStage(
            draftId = draftId,
            stage = HrOnboardingStage.CREDENTIALS,
            payload = mapOf(
                "full_name" to "Ada Onboard",
                "email" to "ada@example.com",
                "phone_e164" to "+263771111222",
                "grade_id" to FakeRpcClient.FAKE_HR_GRADE_ID,
                "contract_signed" to "true",
                "signature_name" to "Ada Onboard",
                "photo_local_path" to "/tmp/hr.jpg",
            ),
            bankingJson = mapOf(
                "bank_name" to "CBZ",
                "account_number" to "123456",
            ),
            healthJson = mapOf("medical_aid" to "Premier"),
        )

        val complete = rpc.completeHrOnboarding(draftId)
        assertNotNull(complete.employeeId)
        assertTrue(complete.employeeCode!!.startsWith("GTR"))
        assertNull(complete.userId)

        val auth = rpc.createHrOnboardingAuthUser(complete.employeeId!!)
        assertTrue(auth.created)
        assertTrue(auth.mustChangePassword)
        assertTrue(auth.channels.isNotEmpty())
        assertEquals(complete.employeeId, auth.employeeId)
    }
}
