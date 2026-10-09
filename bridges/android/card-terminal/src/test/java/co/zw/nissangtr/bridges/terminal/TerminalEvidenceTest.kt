package co.zw.nissangtr.bridges.terminal

import org.junit.Assert.assertEquals
import org.junit.Test

class TerminalEvidenceTest {
    private val config = IntentTerminalConfig.from(mapOf("package_name" to "com.bank.pos", "purchase_action" to "com.bank.pos.PURCHASE"))!!

    @Test
    fun `canonical json matches the server's JSON stringify byte for byte`() {
        val e = TerminalEvidence(
            " att-1 ", "dev-9", "2026-10-03T10:00:00Z",
            TerminalResult(TerminalOutcome.Approved, " TX\"1 ", "  ", "A1\\B", "4242", "VISA", null, "Approved\nok"),
        )
        // Produced with node: JSON.stringify of the edge function's normalized payload.
        val expected = "{\"version\":\"gtr-card-terminal-evidence-v1\",\"attempt_id\":\"att-1\",\"device_id\":\"dev-9\",\"observed_at\":\"2026-10-03T10:00:00Z\",\"outcome\":\"approved\",\"terminal_transaction_id\":\"TX\\\"1\",\"rrn\":null,\"authorization_code\":\"A1\\\\B\",\"card_last4\":\"4242\",\"card_scheme\":\"VISA\",\"response_code\":null,\"response_message\":\"Approved\\nok\"}"
        assertEquals(expected, e.canonicalJson())
    }

    @Test
    fun `only a readable approval is approved`() {
        val ok = TerminalResultMapper.map(true, mapOf("result_status" to "APPROVED", "transaction_id" to "T1", "rrn" to "R1", "card_last4" to "**** 4242"), config)
        assertEquals(TerminalOutcome.Approved, ok.outcome)
        assertEquals("4242", ok.cardLast4)
        // Approved without references cannot be proven: Unknown.
        assertEquals(TerminalOutcome.Unknown, TerminalResultMapper.map(true, mapOf("result_status" to "approved"), config).outcome)
        // No answer at all: Unknown, never declined.
        assertEquals(TerminalOutcome.Unknown, TerminalResultMapper.map(true, emptyMap(), config).outcome)
        assertEquals(TerminalOutcome.Declined, TerminalResultMapper.map(true, mapOf("result_status" to "declined"), config).outcome)
        // The app never opened: nothing was charged.
        assertEquals(TerminalOutcome.Failed, TerminalResultMapper.map(false, emptyMap(), config).outcome)
    }

    @Test
    fun `custom extra names come from the terminal config`() {
        val c = IntentTerminalConfig.from(
            mapOf("package_name" to "p", "purchase_action" to "a", "result_status_key" to "STATUS", "result_transaction_id_key" to "TXN", "result_auth_code_key" to "AUTH"),
        )!!
        val r = TerminalResultMapper.map(true, mapOf("STATUS" to "00", "TXN" to "9", "AUTH" to "X"), c)
        assertEquals(TerminalOutcome.Approved, r.outcome)
        assertEquals("9", r.transactionId)
    }
}
