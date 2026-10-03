package co.zw.nissangtr.bridges.terminal

import kotlinx.coroutines.delay

/**
 * Demo card machine for the fake-backend build only (never wired to the live backend): approves,
 * except amounts ending in .99 (declined) and .98 (no answer → Unknown, to exercise recovery).
 */
class SimulatedCardTerminalBridge(private val delayMs: Long = 1_500) : CardTerminalBridge {
    override fun isAvailable(config: IntentTerminalConfig) = true

    override suspend fun run(request: TerminalRequest): TerminalResult {
        delay(delayMs)
        val cents = request.amountMinor % 100
        val tx = "SIM-${request.reference.takeLast(8)}"
        return when {
            request.operation == TerminalOperation.Status ->
                TerminalResult(TerminalOutcome.Approved, tx, rrn = "000123456789", authorizationCode = "A1B2C3", cardLast4 = "4242", cardScheme = "VISA", responseMessage = "Approved (status)")
            cents == 99L && request.operation == TerminalOperation.Purchase -> TerminalResult(TerminalOutcome.Declined, responseCode = "51", responseMessage = "Insufficient funds")
            cents == 98L && request.operation == TerminalOperation.Purchase -> TerminalResult(TerminalOutcome.Unknown)
            else -> TerminalResult(TerminalOutcome.Approved, tx, rrn = "000123456789", authorizationCode = "A1B2C3", cardLast4 = "4242", cardScheme = "VISA", responseCode = "00", responseMessage = "Approved")
        }
    }
}
