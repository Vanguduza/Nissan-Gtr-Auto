package co.zw.nissangtr.pos.pay

import co.zw.nissangtr.pos.api.CheckoutReceiptContacts
import co.zw.nissangtr.pos.api.CheckoutResult
import co.zw.nissangtr.pos.api.LiveRailException
import co.zw.nissangtr.pos.api.PosClient
import co.zw.nissangtr.pos.api.TenderMode

/**
 * Intent per live-rail slice → settle → then single checkout.
 * Any rail failure aborts before checkout.
 */
object LiveRailSettler {

    suspend fun settleRequiredRails(
        client: PosClient,
        drafts: List<TenderDraft>,
        currency: String,
        online: Boolean,
    ): List<TenderDraft> {
        return drafts.map { draft ->
            if (!draft.mode.isLiveRail || draft.tenderedCents <= 0L) {
                draft.copy(railSettled = true)
            } else {
                if (!online) {
                    throw LiveRailException("needs connection")
                }
                val intent = client.createLiveRailIntent(
                    mode = draft.mode,
                    amountCents = draft.tenderedCents,
                    currency = currency,
                    externalRef = "pos-${draft.mode.rpcValue}-${System.currentTimeMillis()}",
                )
                val settled = client.settleLiveRailIntent(intent.intentId)
                if (!settled.settled) {
                    throw LiveRailException("rail not settled: ${intent.intentId}")
                }
                draft.copy(railSettled = true)
            }
        }
    }

    suspend fun confirmCheckout(
        client: PosClient,
        cartId: String,
        dueCents: Long,
        drafts: List<TenderDraft>,
        currency: String,
        online: Boolean,
        receipt: CheckoutReceiptContacts,
    ): CheckoutResult {
        val settledDrafts = settleRequiredRails(client, drafts, currency, online)
        val snap = TenderAllocator.allocate(dueCents, settledDrafts)
        require(snap.confirmEnabled) {
            "confirm disabled (remaining=${snap.remainingCents}, rails, or empty)"
        }
        val tenders = TenderAllocator.toRpcLines(snap, currency)
        // Applied sum must equal due; change never in payload.
        require(tenders.isNotEmpty() || dueCents == 0L)
        return client.checkoutWithTenders(cartId, tenders, receipt)
    }
}

/** Offline: only cash mode is enabled on the Pay sheet. */
fun TenderMode.enabledWhen(online: Boolean): Boolean =
    online || this == TenderMode.CASH
