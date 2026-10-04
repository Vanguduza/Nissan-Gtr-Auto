package co.zw.nissangtr.pos.data

import co.zw.nissangtr.management.rpc.PosPaymentStatus
import co.zw.nissangtr.management.rpc.PosTenderLine
import co.zw.nissangtr.management.rpc.RpcClient
import co.zw.nissangtr.pos.domain.gateway.CheckoutResult
import co.zw.nissangtr.pos.domain.gateway.ReserveCheckoutGateway
import co.zw.nissangtr.pos.domain.model.DigitalProvider
import co.zw.nissangtr.pos.domain.model.Money
import co.zw.nissangtr.pos.domain.model.PaymentExceptionInfo
import co.zw.nissangtr.pos.domain.model.PaymentStatus
import co.zw.nissangtr.pos.domain.model.PickupOrder
import co.zw.nissangtr.pos.domain.model.ProviderMethod
import co.zw.nissangtr.pos.domain.model.ProviderStart
import co.zw.nissangtr.pos.domain.model.ReceiptContacts
import co.zw.nissangtr.pos.domain.model.RecoveryItem
import co.zw.nissangtr.pos.domain.model.TenderLine
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

/**
 * Reserve-first checkout (Blueprint §10.6) over the `*_pos_commerce_*` RPCs and the provider
 * initiate functions. [returnUrl] is where a hosted provider page (Paynow, ContiPay) sends the
 * customer's phone afterwards; it never marks anything paid — the provider webhook does.
 */
class RpcReserveCheckoutGateway(
    private val rpc: RpcClient,
    private val returnUrl: String = "https://nissangtrauto.co.zw/checkout/return",
) : ReserveCheckoutGateway {

    override suspend fun prepare(cartId: String, requestId: String, contacts: ReceiptContacts) = call {
        rpc.preparePosCommerceCheckout(
            cartId = cartId,
            checkoutRequestId = requestId,
            receiptEmail = contacts.email?.takeIf { it.isNotBlank() },
            receiptWhatsappE164 = contacts.whatsappE164?.takeIf { it.isNotBlank() },
        )
    }

    override suspend fun status(orderId: String) = call { rpc.posPaymentStatus(orderId).toDomain() }

    override suspend fun settle(orderId: String, paymentRequestId: String, tenders: List<TenderLine>) = call {
        val invoiceId = rpc.settlePosCommerceTenders(
            orderId,
            paymentRequestId,
            tenders.map { PosTenderLine(it.tender.rpcValue, it.amount.minor / 100.0) },
        )
        CheckoutResult(invoiceId, documentNumber(invoiceId))
    }

    override suspend fun providerAvailability() = call {
        coroutineScope {
            DigitalProvider.entries
                .map { p -> p to async { rpc.posProviderAvailability(p.rpcValue) } }
                .associate { (p, reason) -> p to reason.await() }
        }
    }

    override suspend fun startProvider(orderId: String, provider: DigitalProvider, msisdn: String?, method: ProviderMethod?) = call {
        val r = rpc.startPosProviderPayment(orderId, provider.rpcValue, msisdn, method?.rpcValue, "$returnUrl?order=$orderId")
        ProviderStart(r.intentId, r.checkoutUrl, r.message)
    }

    override suspend fun cancel(orderId: String, reason: String) = call { rpc.cancelPosCommerceCheckout(orderId, reason) }

    override suspend fun onAccount(cartId: String, contacts: ReceiptContacts) = call {
        val invoiceId = rpc.checkoutPosCartOnAccount(
            cartId,
            contacts.email?.takeIf { it.isNotBlank() },
            contacts.whatsappE164?.takeIf { it.isNotBlank() },
        )
        CheckoutResult(invoiceId, documentNumber(invoiceId))
    }

    override suspend fun documentNumber(invoiceId: String): String? =
        runCatching { rpc.salesInvoiceDocumentNumber(invoiceId) }.getOrNull()

    override suspend fun recovery() = call {
        rpc.listPosPaymentRecovery().map {
            RecoveryItem(
                orderId = it.orderId,
                state = it.state,
                total = Money.ofMajor(it.total, it.currency.toDomain()),
                activeProvider = it.activeProvider ?: it.settledProvider,
                salesInvoiceId = it.salesInvoiceId,
                updatedAtIso = it.updatedAt,
                openExceptions = it.openExceptions,
            )
        }
    }

    override suspend fun pickups(query: String?) = call {
        rpc.listPosPickupOrders(query).map {
            PickupOrder(
                orderId = it.orderId,
                documentNumber = it.documentNumber,
                customerName = it.customerName,
                state = it.state,
                total = Money.ofMajor(it.total, it.currency.toDomain()),
                settledProvider = it.settledProvider,
                updatedAtIso = it.updatedAt,
            )
        }
    }

    override suspend fun collect(orderId: String) = call { rpc.collectPosCommerceOrder(orderId, null) }
}

private fun PosPaymentStatus.toDomain() = PaymentStatus(
    orderId = orderId,
    state = state,
    total = Money.ofMajor(total, currency.toDomain()),
    reservationExpiresAtIso = reservationExpiresAt,
    activeProvider = activeProvider,
    providerStatus = providerStatus,
    providerFailure = providerFailure,
    settledProvider = settledProvider,
    reference = settledProviderRef,
    salesInvoiceId = salesInvoiceId,
    paymentException = paymentException,
    exceptions = exceptions.map { PaymentExceptionInfo(it.code, it.detail, it.resolvedAt, it.resolution, it.createdAt) },
    activeIntentId = activeIntentId,
)
