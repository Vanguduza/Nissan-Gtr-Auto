package co.zw.nissangtr.bridges.terminal

import android.app.Activity
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.lang.ref.WeakReference
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/** Hands terminal answers from [CardTerminalProxyActivity] back to the waiting call. */
internal object TerminalResultRelay {
    data class Raw(val launched: Boolean, val resultOk: Boolean, val extras: Map<String, String?>)

    private val next = AtomicLong(1)
    private val waiting = ConcurrentHashMap<Long, CompletableDeferred<Raw>>()

    fun open(): Pair<Long, CompletableDeferred<Raw>> {
        val token = next.getAndIncrement()
        val d = CompletableDeferred<Raw>()
        waiting[token] = d
        return token to d
    }

    fun deliver(token: Long, launched: Boolean, resultOk: Boolean, extras: Map<String, String?>) {
        waiting.remove(token)?.complete(Raw(launched, resultOk, extras))
    }

    fun abandon(token: Long) {
        waiting.remove(token)
    }
}

/**
 * [CardTerminalBridge] over the acquirer's Android app (`android_intent_v1`). Attach the host
 * activity so the terminal app opens in the same task (needed in kiosk / lock-task mode, where the
 * terminal app's package must also be allow-listed).
 */
class IntentCardTerminalBridge(context: Context) : CardTerminalBridge {
    private val appContext = context.applicationContext
    private var activityRef: WeakReference<Activity>? = null

    fun attachActivity(activity: Activity) {
        activityRef = WeakReference(activity)
    }

    fun detachActivity() {
        activityRef = null
    }

    override fun isAvailable(config: IntentTerminalConfig): Boolean =
        Intent(config.purchaseAction).setPackage(config.packageName).resolveActivity(appContext.packageManager) != null

    override suspend fun run(request: TerminalRequest): TerminalResult {
        val action = request.config.actionFor(request.operation)
            ?: return TerminalResult(TerminalOutcome.Failed, responseMessage = "This card machine does not support ${request.operation.wire}.")
        val target = Intent(action).setPackage(request.config.packageName).apply {
            putExtra(request.config.amountMinorKey, request.amountMinor)
            putExtra(request.config.currencyKey, request.currency)
            putExtra(request.config.referenceKey, request.reference)
            putExtra(request.config.operationKey, request.operation.wire)
            request.originalTransactionId?.let { putExtra(request.config.originalTransactionIdKey, it) }
        }
        val (token, answer) = TerminalResultRelay.open()
        withContext(Dispatchers.Main) {
            val host = activityRef?.get()
            val proxy = Intent(host ?: appContext, CardTerminalProxyActivity::class.java)
                .putExtra(CardTerminalProxyActivity.EXTRA_TOKEN, token)
                .putExtra(CardTerminalProxyActivity.EXTRA_TARGET, target)
            if (host == null) proxy.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            (host ?: appContext).startActivity(proxy)
        }
        val raw = try {
            answer.await()
        } catch (e: kotlinx.coroutines.CancellationException) {
            TerminalResultRelay.abandon(token)
            throw e
        }
        return TerminalResultMapper.map(raw.launched, raw.extras, request.config)
    }
}
