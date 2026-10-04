package co.zw.nissangtr.delivery.pod

import co.zw.nissangtr.bridges.terminal.CardTerminalBridge
import co.zw.nissangtr.bridges.terminal.IntentTerminalConfig
import co.zw.nissangtr.bridges.terminal.TerminalEvidence
import co.zw.nissangtr.bridges.terminal.TerminalOperation
import co.zw.nissangtr.bridges.terminal.TerminalRequest
import co.zw.nissangtr.bridges.terminal.TerminalResult
import co.zw.nissangtr.delivery.rpc.DeliveryBalanceApproval
import co.zw.nissangtr.delivery.rpc.DeliveryCardAttempt
import co.zw.nissangtr.delivery.rpc.DeliveryCardTerminal
import co.zw.nissangtr.delivery.rpc.DeliveryCashReceipt
import co.zw.nissangtr.delivery.rpc.DeliveryPaymentContext
import co.zw.nissangtr.delivery.rpc.RpcClient
import java.time.Instant
import java.util.Locale

/**
 * Cash / card on delivery rules shared by the stop sheet and its tests.
 *
 * The server (`submit_delivery_pod`) does not check payment, so the app holds "Complete delivery"
 * while the invoice still has a balance the driver is meant to collect.
 */
object CodGate {
    /** Card attempts that may still have charged the customer: never charge again until settled. */
    val UNRESOLVED = setOf("initiated", "approved", "unknown", "recovery_required")

    fun collects(ctx: DeliveryPaymentContext?): Boolean = ctx != null && (ctx.mayCollectCash || ctx.mayCollectCard)

    fun isDue(ctx: DeliveryPaymentContext?): Boolean = collects(ctx) && ctx!!.amountDue > 0.004

    fun isUnresolved(attempt: DeliveryCardAttempt?): Boolean =
        attempt != null && (attempt.status in UNRESOLVED || attempt.finalizationError != null) && attempt.status != "settled"

    /** The unpaid balance may stay on account: approved, and the balance has not grown since. */
    fun balanceOnAccount(ctx: DeliveryPaymentContext?, approval: DeliveryBalanceApproval?): Boolean =
        ctx != null && approval != null && approval.approved && ctx.amountDue <= approval.amount + 0.01

    /** Why delivery can't be completed yet, or null when payment allows it. */
    fun blockingReason(ctx: DeliveryPaymentContext?, attempt: DeliveryCardAttempt?, approval: DeliveryBalanceApproval? = null): String? = when {
        isUnresolved(attempt) -> "Finish the card payment above before completing."
        isDue(ctx) && balanceOnAccount(ctx, approval) -> null
        isDue(ctx) && approval?.status == "pending" -> "Waiting for dispatch to approve the balance on account."
        isDue(ctx) -> "Collect ${money(ctx!!.amountDue, ctx.currency)} before completing."
        else -> null
    }

    fun money(amount: Double, currency: String): String = String.format(Locale.US, "%s %.2f", currency.uppercase(), amount)

    /** Parses what the driver typed; null when it is not a positive amount within the balance. */
    fun parseAmount(text: String, due: Double): Double? {
        val v = text.trim().replace(",", ".").toDoubleOrNull() ?: return null
        val rounded = Math.round(v * 100) / 100.0
        return rounded.takeIf { it > 0 && it <= due + 0.01 }
    }

    fun minor(amount: Double): Long = Math.round(amount * 100)
}

/** Remembers which machine this phone uses and which machines it is paired with. */
interface CardPairingStore {
    fun selectedTerminalId(): String?
    fun selectTerminal(id: String)
    fun pairedKeySha(terminalId: String): String?
    fun markPaired(terminalId: String, keySha: String)
}

/** This phone's evidence key (Android Keystore in the app; a stub in tests). */
interface CardEvidenceSigner {
    fun publicKeySpkiBase64(): String
    fun publicKeySha256Hex(): String
    fun signBase64(message: ByteArray): String
}

/**
 * Talks to the server and the card machine (Bridge-First, `android_intent_v1`). One charge per
 * request id; an answer that could not be recorded is kept and sent again on "Ask again" so a
 * customer is never charged twice.
 */
class DeliveryPaymentFlow(
    private val rpc: RpcClient,
    private val bridge: CardTerminalBridge,
    val deviceId: String,
    private val signer: CardEvidenceSigner,
    private val pairing: CardPairingStore,
    private val now: () -> Instant = Instant::now,
) {
    private val lastAnswer = mutableMapOf<String, TerminalResult>()

    suspend fun context(jobId: String): DeliveryPaymentContext? = rpc.getDeliveryPaymentContext(jobId)

    suspend fun recovery(jobId: String): DeliveryCardAttempt? = rpc.getDeliveryCardRecovery(jobId)

    suspend fun balanceApproval(jobId: String): DeliveryBalanceApproval? = rpc.getDeliveryBalanceApproval(jobId)

    suspend fun requestBalanceOnAccount(jobId: String, reason: String): DeliveryBalanceApproval =
        rpc.requestDeliveryBalanceOnAccount(jobId, reason.trim())

    suspend fun collectCash(jobId: String, amount: Double, requestId: String, notes: String?): DeliveryCashReceipt =
        rpc.collectDeliveryCash(jobId, amount, requestId, notes?.trim()?.takeIf { it.isNotEmpty() })

    /** Machines a manager assigned for delivery use on this phone (or the job's warehouse). */
    suspend fun terminals(warehouseId: String?): List<DeliveryCardTerminal> =
        rpc.listDeliveryCardTerminals(warehouseId, deviceId).filter { it.adapterKey == null || it.adapterKey == "android_intent_v1" }

    fun selected(terminals: List<DeliveryCardTerminal>): DeliveryCardTerminal? {
        val id = pairing.selectedTerminalId()
        return terminals.firstOrNull { it.id == id }
            ?: terminals.firstOrNull { it.deviceId == deviceId }
            ?: terminals.singleOrNull()
    }

    fun select(terminalId: String) = pairing.selectTerminal(terminalId)

    fun isPaired(terminalId: String): Boolean = pairing.pairedKeySha(terminalId) != null

    fun appInstalled(terminal: DeliveryCardTerminal): Boolean =
        IntentTerminalConfig.from(terminal.adapterConfig)?.let(bridge::isAvailable) ?: false

    suspend fun pair(terminalId: String) {
        val sha = signer.publicKeySha256Hex()
        rpc.registerDeliveryCardDeviceKey(terminalId, deviceId, signer.publicKeySpkiBase64(), sha)
        pairing.markPaired(terminalId, sha)
    }

    /** Starts the charge and runs the machine; approved answers are posted straight away. */
    suspend fun charge(jobId: String, terminalId: String, amount: Double, requestId: String): DeliveryCardAttempt {
        val attempt = rpc.beginDeliveryCardPayment(jobId, terminalId, deviceId, amount, requestId)
        // Same request id again (e.g. after a crash): the machine already ran for this attempt.
        if (attempt.status != "initiated") return settleIfApproved(attempt)
        return runMachine(attempt, statusOnly = false)
    }

    /** Asks the machine for the result of a charge whose answer we did not get or could not record. */
    suspend fun askAgain(attempt: DeliveryCardAttempt): DeliveryCardAttempt =
        if (attempt.status == "approved") settleIfApproved(attempt) else runMachine(attempt, statusOnly = true)

    suspend fun finish(attemptId: String): DeliveryCardAttempt = rpc.finalizeDeliveryCardPayment(attemptId)

    private suspend fun runMachine(attempt: DeliveryCardAttempt, statusOnly: Boolean): DeliveryCardAttempt {
        val raw = rpc.getDeliveryCardAttempt(attempt.attemptId)
        val config = IntentTerminalConfig.from(raw.adapterConfig) ?: error("This card machine is not set up for this phone.")
        val ref = raw.externalRef ?: raw.attemptId
        val amountMinor = CodGate.minor(raw.amount)
        val currency = raw.currency.uppercase()
        val cached = lastAnswer[raw.attemptId]
        val result = when {
            statusOnly && cached != null -> cached
            statusOnly -> {
                if (config.statusAction == null) {
                    error("This card machine cannot be asked again. Check its last-transaction screen and call dispatch.")
                }
                bridge.run(TerminalRequest(config, TerminalOperation.Status, amountMinor, currency, ref, raw.transactionId))
            }
            else -> bridge.run(TerminalRequest(config, TerminalOperation.Purchase, amountMinor, currency, ref))
        }
        lastAnswer[raw.attemptId] = result
        val payload = TerminalEvidence(raw.attemptId, deviceId, now().toString(), result).canonicalJson()
        val recorded = rpc.submitCardTerminalEvidence(payload, signer.signBase64(payload.toByteArray(Charsets.UTF_8)))
        lastAnswer.remove(raw.attemptId)
        return settleIfApproved(recorded)
    }

    /** Approved on the machine → post it against the invoice; a posting error stays visible for "Finish". */
    private suspend fun settleIfApproved(attempt: DeliveryCardAttempt): DeliveryCardAttempt {
        if (attempt.status != "approved") return attempt
        return try {
            rpc.finalizeDeliveryCardPayment(attempt.attemptId)
        } catch (e: Exception) {
            attempt.copy(finalizationError = e.message ?: "The card payment was not posted.")
        }
    }
}
