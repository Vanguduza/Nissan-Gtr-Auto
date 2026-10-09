package co.zw.nissangtr.delivery.pod

import android.annotation.SuppressLint
import android.content.Context
import android.provider.Settings
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import co.zw.nissangtr.bridges.terminal.CardTerminalBridge
import co.zw.nissangtr.bridges.terminal.TerminalDeviceKey
import co.zw.nissangtr.delivery.rpc.DeliveryCardAttempt
import co.zw.nissangtr.delivery.rpc.DeliveryCardTerminal
import co.zw.nissangtr.delivery.rpc.DeliveryPaymentContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

enum class CodMethod { Cash, Card }

data class DeliveryPaymentUiState(
    val jobId: String = "",
    val loading: Boolean = false,
    /** Null: no invoice to collect on this stop (or not loaded yet). */
    val context: DeliveryPaymentContext? = null,
    val contextError: String? = null,
    val method: CodMethod = CodMethod.Cash,
    val amount: String = "",
    val cashNotes: String = "",
    val deviceId: String = "",
    val terminals: List<DeliveryCardTerminal> = emptyList(),
    val terminalsLoaded: Boolean = false,
    val selectedTerminalId: String? = null,
    val paired: Boolean = false,
    val appInstalled: Boolean = false,
    /** The card attempt in progress or awaiting recovery. */
    val attempt: DeliveryCardAttempt? = null,
    /** The latest request to leave the unpaid balance on account. */
    val approval: co.zw.nissangtr.delivery.rpc.DeliveryBalanceApproval? = null,
    val onAccountOpen: Boolean = false,
    val onAccountReason: String = "",
    val busyLabel: String? = null,
    val message: String? = null,
    val error: String? = null,
) {
    val busy: Boolean get() = loading || busyLabel != null
    val blockingReason: String? get() = CodGate.blockingReason(context, attempt, approval)
    val balanceOnAccount: Boolean get() = CodGate.balanceOnAccount(context, approval)
    val selectedTerminal: DeliveryCardTerminal? get() = terminals.firstOrNull { it.id == selectedTerminalId }
}

/** Cash / card on delivery for one stop. Card machine via the card-terminal bridge only. */
class DeliveryPaymentViewModel(private val flow: DeliveryPaymentFlow) : ViewModel() {
    private val _state = MutableStateFlow(DeliveryPaymentUiState(deviceId = flow.deviceId))
    val state: StateFlow<DeliveryPaymentUiState> = _state.asStateFlow()

    // Kept until the server confirms, so a retry after a dropped connection never pays twice.
    private var cashRequestId = UUID.randomUUID().toString()
    private var cardRequestId = UUID.randomUUID().toString()

    fun bindJob(jobId: String) {
        if (_state.value.jobId == jobId && _state.value.context != null) return
        _state.value = DeliveryPaymentUiState(jobId = jobId, deviceId = flow.deviceId)
        refresh()
    }

    fun refresh() {
        val jobId = _state.value.jobId.ifBlank { return }
        viewModelScope.launch {
            _state.update { it.copy(loading = true, contextError = null) }
            try {
                val ctx = flow.context(jobId)
                val recovery = if (CodGate.collects(ctx)) runCatching { flow.recovery(jobId) }.getOrNull() else null
                val approval = if (CodGate.collects(ctx)) runCatching { flow.balanceApproval(jobId) }.getOrNull() else null
                _state.update {
                    it.copy(
                        loading = false,
                        context = ctx,
                        attempt = recovery ?: it.attempt?.takeIf(CodGate::isUnresolved),
                        approval = approval,
                        method = when {
                            ctx?.mayCollectCash == false && ctx.mayCollectCard -> CodMethod.Card
                            ctx?.mayCollectCard == false -> CodMethod.Cash
                            else -> it.method
                        },
                        amount = ctx?.takeIf { c -> c.amountDue > 0 }?.let { c -> "%.2f".format(java.util.Locale.US, c.amountDue) }.orEmpty(),
                    )
                }
                if (_state.value.method == CodMethod.Card) loadTerminals()
                if (approval?.status == "pending") waitForDecision()
            } catch (e: Exception) {
                _state.update { it.copy(loading = false, contextError = e.message ?: "Could not check payment for this stop.") }
            }
        }
    }

    fun selectMethod(method: CodMethod) {
        _state.update { it.copy(method = method, error = null, message = null) }
        if (method == CodMethod.Card && !_state.value.terminalsLoaded) loadTerminals()
    }

    fun onAmountChange(v: String) {
        val clean = v.filter { it.isDigit() || it == '.' || it == ',' }.take(12)
        if (clean != _state.value.amount) {
            cashRequestId = UUID.randomUUID().toString()
            if (!CodGate.isUnresolved(_state.value.attempt)) cardRequestId = UUID.randomUUID().toString()
        }
        _state.update { it.copy(amount = clean, error = null) }
    }

    fun onCashNotesChange(v: String) = _state.update { it.copy(cashNotes = v.take(200)) }

    fun openOnAccount(open: Boolean) = _state.update { it.copy(onAccountOpen = open, error = null) }

    fun onOnAccountReasonChange(v: String) = _state.update { it.copy(onAccountReason = v.take(300)) }

    private var waiting: kotlinx.coroutines.Job? = null

    /** The customer cannot pay the rest: approved at once on a trade account with room, else dispatch decides. */
    fun requestOnAccount() {
        val s = _state.value
        if (s.onAccountReason.isBlank()) return _state.update { it.copy(error = "Say why the customer cannot pay the rest.") }
        run("Asking to leave the balance on account…") {
            val a = flow.requestBalanceOnAccount(s.jobId, s.onAccountReason)
            _state.update {
                it.copy(
                    approval = a,
                    onAccountOpen = false,
                    message = when (a.status) {
                        "auto_approved" -> "Approved on the customer's account. Finish the proof to complete."
                        "pending" -> "Sent to dispatch. Wait here; this updates when they decide."
                        else -> null
                    },
                )
            }
            if (a.status == "pending") waitForDecision()
        }
    }

    /** Checks every 10 seconds while dispatch decides (stops when this stop closes). */
    private fun waitForDecision() {
        waiting?.cancel()
        waiting = viewModelScope.launch {
            while (true) {
                kotlinx.coroutines.delay(10_000)
                val a = runCatching { flow.balanceApproval(_state.value.jobId) }.getOrNull() ?: continue
                if (a.status == "pending") continue
                _state.update {
                    it.copy(
                        approval = a,
                        message = if (a.approved) "Dispatch approved leaving ${CodGate.money(a.amount, a.currency)} on account." else null,
                        error = if (a.status == "refused") "Dispatch refused: ${a.decisionNote ?: "no reason given"}. Collect the balance or report an issue." else null,
                    )
                }
                break
            }
        }
    }

    /** Ask now instead of waiting for the next check. */
    fun checkDecision() {
        run("Checking with dispatch…") {
            val a = flow.balanceApproval(_state.value.jobId)
            _state.update { it.copy(approval = a) }
            if (a?.status == "refused") _state.update { it.copy(error = "Dispatch refused: ${a.decisionNote ?: "no reason given"}.") }
        }
    }

    fun collectCash() {
        val s = _state.value
        val ctx = s.context ?: return
        val amount = CodGate.parseAmount(s.amount, ctx.amountDue)
            ?: return _state.update { it.copy(error = "Enter an amount up to ${CodGate.money(ctx.amountDue, ctx.currency)}.") }
        run("Recording cash…") {
            val r = flow.collectCash(s.jobId, amount, cashRequestId, s.cashNotes)
            cashRequestId = UUID.randomUUID().toString()
            _state.update { it.copy(cashNotes = "", message = "Cash ${CodGate.money(r.amount, r.currency)} recorded.") }
            reloadContext()
        }
    }

    fun loadTerminals() {
        val s = _state.value
        run("Finding card machines…") {
            val list = flow.terminals(s.context?.warehouseId)
            val selected = flow.selected(list)
            _state.update {
                it.copy(
                    terminals = list,
                    terminalsLoaded = true,
                    selectedTerminalId = selected?.id,
                    paired = selected?.let { t -> flow.isPaired(t.id) } ?: false,
                    appInstalled = selected?.let(flow::appInstalled) ?: false,
                )
            }
        }
    }

    fun selectTerminal(id: String) {
        flow.select(id)
        val t = _state.value.terminals.firstOrNull { it.id == id } ?: return
        cardRequestId = UUID.randomUUID().toString()
        _state.update { it.copy(selectedTerminalId = id, paired = flow.isPaired(id), appInstalled = flow.appInstalled(t), error = null) }
    }

    fun pair() {
        val id = _state.value.selectedTerminalId ?: return
        run("Pairing…") {
            flow.pair(id)
            _state.update { it.copy(paired = true, message = "This phone is paired with the card machine.") }
        }
    }

    fun chargeCard() {
        val s = _state.value
        val ctx = s.context ?: return
        val terminal = s.selectedTerminalId ?: return _state.update { it.copy(error = "Choose a card machine.") }
        val amount = CodGate.parseAmount(s.amount, ctx.amountDue)
            ?: return _state.update { it.copy(error = "Enter an amount up to ${CodGate.money(ctx.amountDue, ctx.currency)}.") }
        run("Waiting for the card machine…") {
            val a = flow.charge(s.jobId, terminal, amount, cardRequestId)
            afterCard(a)
        }
    }

    fun askAgain() {
        val a = _state.value.attempt ?: return
        run("Asking the card machine…") { afterCard(flow.askAgain(a)) }
    }

    fun finishCard() {
        val a = _state.value.attempt ?: return
        run("Posting the card payment…") { afterCard(flow.finish(a.attemptId)) }
    }

    private suspend fun afterCard(a: DeliveryCardAttempt) {
        val resolved = !CodGate.isUnresolved(a)
        if (resolved) cardRequestId = UUID.randomUUID().toString()
        val money = CodGate.money(a.amount, a.currency)
        _state.update {
            it.copy(
                attempt = a.takeIf { _ -> !resolved },
                message = when (a.status) {
                    "settled" -> "Card $money paid${a.cardLast4?.let { l -> " (•••• $l)" } ?: ""}."
                    "declined" -> null
                    else -> it.message
                },
                error = when {
                    a.finalizationError != null -> "Charged on the machine but not posted yet: ${a.finalizationError}"
                    a.status == "declined" -> "Card declined${a.responseMessage?.let { m -> ": $m" } ?: "."} No money was taken."
                    a.status == "cancelled" -> "The card payment was cancelled on the machine. No money was taken."
                    a.status == "failed" -> a.responseMessage ?: "The card machine could not take the payment."
                    else -> null
                },
            )
        }
        if (a.status == "settled") reloadContext()
    }

    private suspend fun reloadContext() {
        val ctx = flow.context(_state.value.jobId)
        _state.update {
            it.copy(
                context = ctx,
                amount = ctx?.takeIf { c -> c.amountDue > 0 }?.let { c -> "%.2f".format(java.util.Locale.US, c.amountDue) }.orEmpty(),
            )
        }
    }

    private fun run(label: String, block: suspend () -> Unit) {
        if (_state.value.busy) return
        viewModelScope.launch {
            _state.update { it.copy(busyLabel = label, error = null, message = null) }
            try {
                block()
            } catch (e: Exception) {
                _state.update { it.copy(error = e.message ?: "Something went wrong — try again.") }
                // A charge whose outcome we don't know shows up as recovery on the next load.
                runCatching { flow.recovery(_state.value.jobId) }.getOrNull()?.let { r -> _state.update { it.copy(attempt = r) } }
            } finally {
                _state.update { it.copy(busyLabel = null) }
            }
        }
    }

    companion object {
        @SuppressLint("HardwareIds")
        fun deviceId(context: Context): String =
            Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID) ?: "phone"

        fun factory(context: Context, rpc: co.zw.nissangtr.delivery.rpc.RpcClient, bridge: CardTerminalBridge): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    val app = context.applicationContext
                    val prefs = app.getSharedPreferences("delivery_card_terminal", Context.MODE_PRIVATE)
                    val store = object : CardPairingStore {
                        override fun selectedTerminalId() = prefs.getString("selected", null)
                        override fun selectTerminal(id: String) = prefs.edit().putString("selected", id).apply()
                        override fun pairedKeySha(terminalId: String) = prefs.getString("paired.$terminalId", null)
                        override fun markPaired(terminalId: String, keySha: String) = prefs.edit().putString("paired.$terminalId", keySha).apply()
                    }
                    val signer = object : CardEvidenceSigner {
                        private val key by lazy { TerminalDeviceKey() }
                        override fun publicKeySpkiBase64() = key.publicKeySpkiBase64()
                        override fun publicKeySha256Hex() = key.publicKeySha256Hex()
                        override fun signBase64(message: ByteArray) = key.signBase64(message)
                    }
                    return DeliveryPaymentViewModel(DeliveryPaymentFlow(rpc, bridge, deviceId(app), signer, store)) as T
                }
            }
    }
}
