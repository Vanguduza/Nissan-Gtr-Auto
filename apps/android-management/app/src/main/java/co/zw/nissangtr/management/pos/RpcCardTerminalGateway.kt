package co.zw.nissangtr.management.pos

import android.content.SharedPreferences
import co.zw.nissangtr.bridges.terminal.CardTerminalBridge
import co.zw.nissangtr.bridges.terminal.IntentTerminalConfig
import co.zw.nissangtr.bridges.terminal.TerminalDeviceKey
import co.zw.nissangtr.bridges.terminal.TerminalEvidence
import co.zw.nissangtr.bridges.terminal.TerminalOperation
import co.zw.nissangtr.bridges.terminal.TerminalOutcome
import co.zw.nissangtr.bridges.terminal.TerminalRequest
import co.zw.nissangtr.bridges.terminal.TerminalResult
import co.zw.nissangtr.management.rpc.ManagerApproval
import co.zw.nissangtr.management.rpc.PosCardTerminalRow
import co.zw.nissangtr.management.rpc.PosTerminalAttempt
import co.zw.nissangtr.management.rpc.RpcClient
import co.zw.nissangtr.pos.domain.error.PosError
import co.zw.nissangtr.pos.domain.gateway.CardTerminalGateway
import co.zw.nissangtr.pos.domain.model.CardTerminal
import co.zw.nissangtr.pos.domain.model.CurrencyCode
import co.zw.nissangtr.pos.domain.model.ManagerCredentials
import co.zw.nissangtr.pos.domain.model.Money
import co.zw.nissangtr.pos.domain.model.TerminalAttempt
import co.zw.nissangtr.pos.domain.model.TerminalRecoveryItem
import co.zw.nissangtr.pos.domain.model.TerminalSetup
import co.zw.nissangtr.pos.domain.result.PosResult
import kotlinx.coroutines.CancellationException
import java.time.Instant
import java.util.UUID

/**
 * Card machine (ECR) gateway: Bridge-First — the terminal is reached only through
 * [CardTerminalBridge]. Each answer is signed with this tablet's Keystore key ([TerminalDeviceKey])
 * and recorded by `card-terminal-result`, which checks it against the key an admin paired.
 * The chosen machine and the pairing are remembered per device in [prefs].
 */
class RpcCardTerminalGateway(
    private val rpc: RpcClient,
    private val bridge: CardTerminalBridge,
    private val key: () -> TerminalDeviceKey,
    private val deviceId: String,
    private val prefs: SharedPreferences,
) : CardTerminalGateway {
    private var terminals: List<PosCardTerminalRow> = emptyList()
    /** The machine's last answer per attempt: re-sent (newly signed) if recording it was interrupted. */
    private val lastAnswer = mutableMapOf<String, TerminalResult>()
    /** Purchase transaction a reversal must name on the machine. */
    private val reversalOf = mutableMapOf<String, String?>()

    private suspend fun <T> call(block: suspend () -> T): PosResult<T> = try {
        PosResult.Ok(block())
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        val msg = e.message.orEmpty()
        PosResult.Err(
            if (msg.contains("timeout", true) || msg.contains("unable to resolve", true) || e is java.io.IOException) PosError.Transient(true, msg)
            else PosError.BusinessRule("terminal", msg),
        )
    }

    private fun PosCardTerminalRow.toDomain() = CardTerminal(id, label, acquirerName, adapterConfig)

    private fun currentSetup(): TerminalSetup {
        val selectedId = prefs.getString(KEY_SELECTED, null)
            ?: terminals.singleOrNull()?.id
            ?: terminals.firstOrNull { it.deviceId == deviceId }?.id
        val selected = terminals.firstOrNull { it.id == selectedId }
        val config = selected?.let { IntentTerminalConfig.from(it.adapterConfig) }
        val paired = selected != null && prefs.getString(KEY_PAIRED_PREFIX + selected.id, null) != null
        return TerminalSetup(terminals.map { it.toDomain() }, selected?.toDomain(), appInstalled = config != null && bridge.isAvailable(config), paired = paired)
    }

    override suspend fun setup() = call {
        terminals = rpc.listPosCardTerminals(null, deviceId).filter { it.adapterKey == "android_intent_v1" }
        currentSetup()
    }

    override suspend fun select(terminalId: String) = call {
        prefs.edit().putString(KEY_SELECTED, terminalId).apply()
        currentSetup()
    }

    override suspend fun pair(terminalId: String, admin: ManagerCredentials?) = call {
        val k = key()
        val spki = k.publicKeySpkiBase64()
        val sha = k.publicKeySha256Hex()
        val register: suspend () -> String = { rpc.registerPosCardTerminalDeviceKey(terminalId, deviceId, spki, sha) }
        val live = rpc as? ManagerApproval
        if (admin != null && live != null) live.withManagerApproval(admin.identifier.trim(), admin.password, register) else register()
        prefs.edit().putString(KEY_PAIRED_PREFIX + terminalId, sha).apply()
        currentSetup()
    }

    override suspend fun beginPurchase(orderId: String, terminalId: String, requestId: String) = call {
        rpc.beginPosCardTerminalPurchase(orderId, terminalId, requestId).toDomain()
    }

    override suspend fun beginSplitPart(sessionId: String, amount: Money, terminalId: String, requestId: String) = call {
        val session = rpc.addPosSplitPaymentLeg(sessionId, "card_terminal", amount.minor / 100.0, requestId, null)
        val leg = session.legs.lastOrNull { it.tender == "card_terminal" && it.status == "planned" }
            ?: session.legs.lastOrNull { it.tender == "card_terminal" && it.status == "pending" }
            ?: error("The card part was not added.")
        // A second key derived from the part's key: retrying returns the same machine attempt.
        val attemptKey = UUID.nameUUIDFromBytes("$requestId:card-terminal".toByteArray()).toString()
        rpc.beginPosSplitCardTerminalLeg(leg.id, terminalId, attemptKey).toDomain()
    }

    override suspend fun run(attempt: TerminalAttempt, statusOnly: Boolean) = call {
        val raw = rpc.getPosCardTerminalAttempt(attempt.attemptId)
        val config = IntentTerminalConfig.from(raw.adapterConfig) ?: error("This card machine is not set up for this tablet.")
        val cached = lastAnswer[attempt.attemptId]
        val result = when {
            // The machine answered but recording it failed: send that answer again, newly signed.
            statusOnly && cached != null -> cached
            statusOnly -> {
                if (config.statusAction == null) error("This card machine cannot be asked again. Check its last-transaction screen and ask a manager to resolve it.")
                bridge.run(TerminalRequest(config, TerminalOperation.Status, raw.amountMinor(), raw.currency.rpcValue.uppercase(), raw.externalRef ?: raw.attemptId, raw.transactionId))
            }
            raw.operation == "reversal" -> bridge.run(
                TerminalRequest(config, TerminalOperation.Reversal, raw.amountMinor(), raw.currency.rpcValue.uppercase(), raw.externalRef ?: raw.attemptId, reversalOf[raw.attemptId]),
            )
            else -> bridge.run(TerminalRequest(config, TerminalOperation.Purchase, raw.amountMinor(), raw.currency.rpcValue.uppercase(), raw.externalRef ?: raw.attemptId))
        }
        lastAnswer[attempt.attemptId] = result
        val evidence = TerminalEvidence(attempt.attemptId, deviceId, Instant.now().toString(), result)
        val payload = evidence.canonicalJson()
        val recorded = rpc.submitCardTerminalEvidence(payload, key().signBase64(payload.toByteArray(Charsets.UTF_8)))
        lastAnswer.remove(attempt.attemptId)
        recorded.toDomain()
    }

    override suspend fun finalize(attemptId: String) = call { rpc.finalizePosCardTerminalPurchase(attemptId).toDomain() }

    override suspend fun beginReversal(purchaseAttemptId: String, requestId: String) = call {
        val purchase = rpc.getPosCardTerminalAttempt(purchaseAttemptId)
        val reversal = rpc.beginPosCardTerminalReversal(purchaseAttemptId, requestId)
        reversalOf[reversal.attemptId] = purchase.transactionId
        reversal.toDomain()
    }

    override suspend fun attempt(attemptId: String) = call { rpc.getPosCardTerminalAttempt(attemptId).toDomain() }

    override suspend fun recovery() = call {
        rpc.listPosCardTerminalRecovery().map {
            TerminalRecoveryItem(
                it.attemptId, it.operation, it.status, it.terminalLabel, it.orderId,
                Money.ofMajor(it.amount, CurrencyCode(it.currency.rpcValue.uppercase())), it.transactionId, it.cardLast4,
                it.finalizationError ?: it.responseMessage, it.updatedAt,
            )
        }
    }

    private fun PosTerminalAttempt.amountMinor(): Long = Math.round(amount * 100)

    private fun PosTerminalAttempt.toDomain() = TerminalAttempt(
        attemptId = attemptId,
        operation = operation,
        status = status,
        amount = Money.ofMajor(amount, CurrencyCode(currency.rpcValue.uppercase())),
        terminalLabel = terminalLabel,
        cardLast4 = cardLast4,
        cardScheme = cardScheme,
        transactionId = transactionId,
        responseMessage = responseMessage,
        orderId = orderId,
        splitLegId = splitLegId,
        invoiceId = invoiceId,
        finalizationError = finalizationError,
    )

    private companion object {
        const val KEY_SELECTED = "pos.card_terminal.selected"
        const val KEY_PAIRED_PREFIX = "pos.card_terminal.paired."
    }
}
