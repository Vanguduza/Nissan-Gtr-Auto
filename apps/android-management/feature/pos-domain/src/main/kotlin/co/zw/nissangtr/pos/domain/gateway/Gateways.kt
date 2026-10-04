package co.zw.nissangtr.pos.domain.gateway

import co.zw.nissangtr.pos.domain.error.PosError
import co.zw.nissangtr.pos.domain.model.OfflineSyncStatus
import co.zw.nissangtr.pos.domain.model.OfflineQueued
import co.zw.nissangtr.pos.domain.model.ApprovalRequest
import co.zw.nissangtr.pos.domain.model.CartProjection
import co.zw.nissangtr.pos.domain.model.Customer
import co.zw.nissangtr.pos.domain.model.CustomerDraft
import co.zw.nissangtr.pos.domain.model.EpcDiagram
import co.zw.nissangtr.pos.domain.model.EpcDiagramDetail
import co.zw.nissangtr.pos.domain.model.EpcSection
import co.zw.nissangtr.pos.domain.model.EpcVariant
import co.zw.nissangtr.pos.domain.model.GarageVehicle
import co.zw.nissangtr.pos.domain.model.InvoiceSummary
import co.zw.nissangtr.pos.domain.model.ManagerCredentials
import co.zw.nissangtr.pos.domain.model.Money
import co.zw.nissangtr.pos.domain.model.ParkedSale
import co.zw.nissangtr.pos.domain.model.QuoteChannel
import co.zw.nissangtr.pos.domain.model.Quotation
import co.zw.nissangtr.pos.domain.model.ReceiptContacts
import co.zw.nissangtr.pos.domain.model.ScannerLink
import co.zw.nissangtr.pos.domain.model.TenderLine
import co.zw.nissangtr.pos.domain.model.CatalogPart
import co.zw.nissangtr.pos.domain.model.CompanionSession
import co.zw.nissangtr.pos.domain.model.CompanionStatus
import co.zw.nissangtr.pos.domain.model.CurrencyCode
import co.zw.nissangtr.pos.domain.model.Operator
import co.zw.nissangtr.pos.domain.model.PopularPin
import co.zw.nissangtr.pos.domain.model.VehicleGeneration
import co.zw.nissangtr.pos.domain.model.VehicleModel
import co.zw.nissangtr.pos.domain.model.VehicleSelection
import co.zw.nissangtr.pos.domain.model.DenominationCount
import co.zw.nissangtr.pos.domain.model.HandoverOperator
import co.zw.nissangtr.pos.domain.model.ReasonCode
import co.zw.nissangtr.pos.domain.model.ApprovalPolicy
import co.zw.nissangtr.pos.domain.model.DigitalProvider
import co.zw.nissangtr.pos.domain.model.PaymentStatus
import co.zw.nissangtr.pos.domain.model.PickupOrder
import co.zw.nissangtr.pos.domain.model.ProviderMethod
import co.zw.nissangtr.pos.domain.model.RefundFeePolicy
import co.zw.nissangtr.pos.domain.model.BusinessProfile
import co.zw.nissangtr.pos.domain.model.MySignature
import co.zw.nissangtr.pos.domain.model.PaymentLetter
import co.zw.nissangtr.pos.domain.model.PaymentLetterSummary
import co.zw.nissangtr.pos.domain.model.LetterSource
import co.zw.nissangtr.pos.domain.model.FulfillmentStep
import co.zw.nissangtr.pos.domain.model.FulfillmentRequest
import co.zw.nissangtr.pos.domain.model.FulfillmentDraft
import co.zw.nissangtr.pos.domain.model.BranchStock
import co.zw.nissangtr.pos.domain.model.WarrantyClaim
import co.zw.nissangtr.pos.domain.model.WarrantySerial
import co.zw.nissangtr.pos.domain.model.ReturnDraft
import co.zw.nissangtr.pos.domain.model.InvoiceDetail
import co.zw.nissangtr.pos.domain.model.SplitRecoveryItem
import co.zw.nissangtr.pos.domain.model.SplitSession
import co.zw.nissangtr.pos.domain.model.SplitTender
import co.zw.nissangtr.pos.domain.model.TerminalAttempt
import co.zw.nissangtr.pos.domain.model.TerminalRecoveryItem
import co.zw.nissangtr.pos.domain.model.TerminalSetup
import co.zw.nissangtr.pos.domain.model.ProviderStart
import co.zw.nissangtr.pos.domain.model.RecoveryItem
import co.zw.nissangtr.pos.domain.model.TillCloseResult
import co.zw.nissangtr.pos.domain.model.TillSession
import co.zw.nissangtr.pos.domain.result.PosResult

/**
 * Narrow, feature-owned gateways (Blueprint §10.2, ARCH-04). Declared here, implemented in
 * `pos-data`, faked in tests. Business outcomes come back as [PosResult], never as exceptions.
 */
interface CatalogGateway {
    /** Fitment-aware search (`search_pos_vehicle_spares`); [vehicle] narrows results when set. */
    suspend fun search(query: String, vehicle: VehicleSelection?): PosResult<List<CatalogPart>>

    /** Server-ranked best sellers (`list_pos_popular_spares`). */
    suspend fun bestSellers(): PosResult<List<CatalogPart>>
}

interface FitmentGateway {
    suspend fun models(): PosResult<List<VehicleModel>>
    suspend fun generations(model: VehicleModel): PosResult<List<VehicleGeneration>>
    suspend fun engines(model: VehicleModel, generation: VehicleGeneration): PosResult<List<String>>
}

interface CartGateway {
    suspend fun open(currency: CurrencyCode): PosResult<CartProjection>
    suspend fun addLine(cartId: String, part: CatalogPart, qty: Double): PosResult<CartProjection>
    suspend fun setQuantity(cartId: String, lineId: String, qty: Double): PosResult<CartProjection>
    suspend fun removeLine(cartId: String, lineId: String): PosResult<CartProjection>
    suspend fun setVehicle(cartId: String, vehicle: VehicleSelection?): PosResult<Unit>
}

/** Operator pins and the D1 best-seller hide list (owner-only RLS on the server). */
interface PinGateway {
    suspend fun pins(): PosResult<List<PopularPin>>
    suspend fun pin(pin: PopularPin): PosResult<Unit>
    suspend fun unpin(pin: PopularPin): PosResult<Unit>
    suspend fun hiddenBestSellers(): PosResult<Set<String>>
    suspend fun hideBestSeller(stockItemId: String): PosResult<Unit>
    suspend fun unhideBestSeller(stockItemId: String): PosResult<Unit>
}

interface SessionGateway {
    suspend fun operator(): PosResult<Operator>
}

/** Outcome of a posted sale; the receipt is assembled from it and the cart snapshot. */
data class CheckoutResult(val invoiceId: String, val documentNumber: String?)

interface CheckoutGateway {
    /** `checkout_pos_cart_with_tenders`: tenders must equal the balance exactly. */
    suspend fun checkout(cartId: String, tenders: List<TenderLine>, contacts: ReceiptContacts): PosResult<CheckoutResult>

    /** EcoCash push to the customer's phone; returns the payment reference. */
    suspend fun requestEcoCash(msisdn: String, amount: Money, reference: String): PosResult<String>
}

interface CustomerGateway {
    suspend fun search(query: String): PosResult<List<Customer>>
    suspend fun create(draft: CustomerDraft): PosResult<Customer>
    suspend fun update(customerId: String, draft: CustomerDraft): PosResult<Customer>
    suspend fun garage(customerId: String): PosResult<List<GarageVehicle>>
    suspend fun attach(cartId: String, customerId: String?): PosResult<Unit>
    suspend fun saveToGarage(customerId: String, vehicle: VehicleSelection, isPrimary: Boolean): PosResult<Unit>
}

interface SalesGateway {
    suspend fun parked(): PosResult<List<ParkedSale>>
    suspend fun park(cartId: String): PosResult<Unit>
    suspend fun resume(cartId: String): PosResult<CartProjection>
    suspend fun quotations(): PosResult<List<Quotation>>
    suspend fun createQuotation(cartId: String, validUntil: String?, notes: String?): PosResult<String>
    suspend fun sendQuotation(quotationId: String, channel: QuoteChannel, contact: String?): PosResult<Unit>
    suspend fun convertQuotation(quotationId: String): PosResult<CartProjection>
    suspend fun recentInvoices(query: String?): PosResult<List<InvoiceSummary>>

    /**
     * Runs [request] with a manager's own sign-in for this one action; the cashier's session is
     * never replaced. Returns the refreshed cart when the action changed it.
     */
    suspend fun approve(
        credentials: ManagerCredentials?,
        request: ApprovalRequest,
        cartId: String,
        reason: ReasonCode? = null,
        notes: String? = null,
        /** A scanned manager badge (`GTRMGR1:…`) instead of [credentials]: one server call approves and audits. */
        badge: String? = null,
    ): PosResult<CartProjection?>
}

/**
 * Reads a manager's ID badge QR. On the tablet this is the front camera through the QR bridge
 * (Bridge-First); [scan] returns null when the operator cancels.
 */
fun interface BadgeScanner {
    suspend fun scan(): PosResult<String?>

    companion object {
        /** No camera (tests, previews): badge approval is unavailable, password still works. */
        val None = BadgeScanner { PosResult.Err(PosError.HardwareUnavailable("camera")) }
    }
}

/**
 * Approval policies and reason codes (`pos_approval_policies`, `pos_approval_reason_codes`). The
 * policy decides whether a governed sale action needs a manager; a configured reason is always
 * recorded with it.
 */
interface GovernanceGateway {
    /** `pos_action_requires_manager`; [value] is the action's measure (percent). Fails closed. */
    suspend fun requiresManager(action: String, value: Double): PosResult<Boolean>
    suspend fun reasons(action: String): PosResult<List<ReasonCode>>
    suspend fun policies(): PosResult<List<ApprovalPolicy>>
    /** Admin only (server-enforced). */
    suspend fun setPolicy(policy: ApprovalPolicy): PosResult<Unit>
    /** The signed-in user is a POS manager: their own approvals need no prompt. */
    suspend fun selfApprover(): PosResult<Boolean> = PosResult.Ok(false)

    /** No policy backend (previews, tests): every action asks a manager, no reason list. */
    object None : GovernanceGateway {
        override suspend fun requiresManager(action: String, value: Double): PosResult<Boolean> = PosResult.Ok(true)
        override suspend fun reasons(action: String): PosResult<List<ReasonCode>> = PosResult.Ok(emptyList())
        override suspend fun policies(): PosResult<List<ApprovalPolicy>> = PosResult.Ok(emptyList())
        override suspend fun setPolicy(policy: ApprovalPolicy): PosResult<Unit> =
            PosResult.Err(PosError.BusinessRule("policies_unavailable", ""))
    }
}

interface EpcGateway {
    suspend fun variants(model: VehicleModel): PosResult<List<EpcVariant>>
    suspend fun sections(model: VehicleModel, variant: EpcVariant): PosResult<List<EpcSection>>
    suspend fun diagrams(model: VehicleModel, variant: EpcVariant, section: EpcSection): PosResult<List<EpcDiagram>>
    suspend fun diagram(model: VehicleModel, variant: EpcVariant, section: EpcSection, diagram: EpcDiagram): PosResult<EpcDiagramDetail>

    /** Diagram image bytes (public Storage object); decoded on the UI side. */
    suspend fun image(url: String): PosResult<ByteArray>
}

/**
 * Encrypted offline outbox (Blueprint §10.12): cash, walk-in sales queued under a client id and
 * replayed idempotently; refusals come back as conflicts for review, never as invented ledger rows.
 */
interface OfflineSaleGateway {
    /**
     * Search the last catalogue snapshot (prices and stock as of the last sync). With [vehicle] and
     * the offline catalogue on the device, the search is limited to parts that fit it.
     */
    suspend fun searchLocal(query: String, vehicle: VehicleSelection? = null): PosResult<List<CatalogPart>>
    suspend fun queueCashSale(cart: CartProjection, vehicle: VehicleSelection?, contacts: ReceiptContacts): PosResult<OfflineQueued>
    /** Replay the outbox and refresh the snapshot; call only while online. */
    suspend fun sync(): PosResult<OfflineSyncStatus>
    suspend fun status(): PosResult<OfflineSyncStatus>

    /** No outbox on this device (tests, previews): offline selling stays unavailable. */
    object None : OfflineSaleGateway {
        private val refused = PosResult.Err(PosError.OfflineRestricted(setOf("no_outbox")))
        override suspend fun searchLocal(query: String, vehicle: VehicleSelection?) = refused
        override suspend fun queueCashSale(cart: CartProjection, vehicle: VehicleSelection?, contacts: ReceiptContacts) = refused
        override suspend fun sync(): PosResult<OfflineSyncStatus> = PosResult.Ok(OfflineSyncStatus(0, 0))
        override suspend fun status(): PosResult<OfflineSyncStatus> = PosResult.Ok(OfflineSyncStatus(0, 0))
    }
}

/** Companion phone pairing (`create_pos_scan_session` / `revoke_pos_scan_session`). */
interface CompanionGateway {
    suspend fun create(cartId: String): PosResult<CompanionSession>
    suspend fun revoke(sessionId: String): PosResult<Unit>
    suspend fun status(sessionId: String): PosResult<CompanionStatus>
    /** The cart as it stands now, phone scans included. */
    suspend fun cart(cartId: String): PosResult<CartProjection>

    /** Phone side: claim a till's code (same staff account as the till) → session and cart. */
    suspend fun claim(pairingCode: String): PosResult<ScannerLink>

    /** Phone side: add the scanned inventory QR to the till's cart; returns what was added. */
    suspend fun addFromQr(cartId: String, payload: String): PosResult<String>

    object None : CompanionGateway {
        private val refused = PosResult.Err(PosError.BusinessRule("companion_unavailable", ""))
        override suspend fun create(cartId: String) = refused
        override suspend fun revoke(sessionId: String): PosResult<Unit> = PosResult.Ok(Unit)
        override suspend fun status(sessionId: String) = refused
        override suspend fun cart(cartId: String) = refused
        override suspend fun claim(pairingCode: String) = refused
        override suspend fun addFromQr(cartId: String, payload: String) = refused
    }
}

/**
 * Cash drawer sessions (Blueprint §10.2 Till gateway). Selling needs an open till: the cart is
 * attached to it so the invoice's cash lands in the drawer's expected total. Cash out, variance
 * and handover need a manager and run through [SalesGateway.approve]. This device's id is bound
 * in the implementation.
 */
interface TillGateway {
    /** The till this operator (or this device) has open or waiting on a variance; null when none. */
    suspend fun current(): PosResult<TillSession?>
    suspend fun open(openingFloat: Money): PosResult<TillSession>
    suspend fun attachCart(cartId: String, sessionId: String): PosResult<Unit>
    suspend fun reasons(action: String): PosResult<List<ReasonCode>>
    /** Cash in only; cash leaving the drawer goes through manager approval. */
    suspend fun cashIn(sessionId: String, amount: Money, reasonCode: String, notes: String?): PosResult<Unit>
    /** Blind denominated count; the server returns expected, counted and variance. */
    suspend fun close(sessionId: String, counts: List<DenominationCount>, varianceReasonCode: String?, notes: String?): PosResult<TillCloseResult>
    suspend fun handoverOperators(): PosResult<List<HandoverOperator>>
    suspend fun recent(): PosResult<List<TillSession>>

    /** No till backend (previews, tests): selling stays ungated. */
    object None : TillGateway {
        private val refused = PosResult.Err(PosError.BusinessRule("till_unavailable", ""))
        override suspend fun current(): PosResult<TillSession?> = PosResult.Ok(null)
        override suspend fun open(openingFloat: Money) = refused
        override suspend fun attachCart(cartId: String, sessionId: String): PosResult<Unit> = PosResult.Ok(Unit)
        override suspend fun reasons(action: String): PosResult<List<ReasonCode>> = PosResult.Ok(emptyList())
        override suspend fun cashIn(sessionId: String, amount: Money, reasonCode: String, notes: String?) = refused
        override suspend fun close(sessionId: String, counts: List<DenominationCount>, varianceReasonCode: String?, notes: String?) = refused
        override suspend fun handoverOperators(): PosResult<List<HandoverOperator>> = PosResult.Ok(emptyList())
        override suspend fun recent(): PosResult<List<TillSession>> = PosResult.Ok(emptyList())
    }
}

/**
 * Reserve-first checkout (Blueprint §10.6): reserve stock and lock the sale, then take money against
 * the reserved order. [requestId] and [paymentRequestId] are idempotency keys: a retry returns the
 * same order or the same settlement.
 */
interface ReserveCheckoutGateway {
    /** False on devices without the backend (tests, previews): the counter keeps the one-step checkout. */
    val enabled: Boolean get() = true
    suspend fun prepare(cartId: String, requestId: String, contacts: ReceiptContacts): PosResult<String>
    suspend fun status(orderId: String): PosResult<PaymentStatus>
    /** Cash, card/bank, store credit; must equal the order total. Idempotent on [paymentRequestId]. */
    suspend fun settle(orderId: String, paymentRequestId: String, tenders: List<TenderLine>): PosResult<CheckoutResult>
    /** Why each provider cannot be offered now (null = available). */
    suspend fun providerAvailability(): PosResult<Map<DigitalProvider, String?>>
    suspend fun startProvider(orderId: String, provider: DigitalProvider, msisdn: String?, method: ProviderMethod?): PosResult<ProviderStart>
    /** Releases the reservation and unlocks the sale; refused while money is in flight. */
    suspend fun cancel(orderId: String, reason: String): PosResult<Unit>
    /** On account for a registered customer with credit; the server checks limit, hold and currency. */
    suspend fun onAccount(cartId: String, contacts: ReceiptContacts): PosResult<CheckoutResult>
    /** Invoice number for a sale the provider settled (receipt). */
    suspend fun documentNumber(invoiceId: String): String?
    suspend fun recovery(): PosResult<List<RecoveryItem>>
    suspend fun pickups(query: String?): PosResult<List<PickupOrder>>
    suspend fun collect(orderId: String): PosResult<Unit>

    object None : ReserveCheckoutGateway {
        override val enabled = false
        private val refused = PosResult.Err(PosError.BusinessRule("reserve_unavailable", ""))
        override suspend fun prepare(cartId: String, requestId: String, contacts: ReceiptContacts) = refused
        override suspend fun status(orderId: String) = refused
        override suspend fun settle(orderId: String, paymentRequestId: String, tenders: List<TenderLine>) = refused
        override suspend fun providerAvailability(): PosResult<Map<DigitalProvider, String?>> = PosResult.Ok(emptyMap())
        override suspend fun startProvider(orderId: String, provider: DigitalProvider, msisdn: String?, method: ProviderMethod?) = refused
        override suspend fun cancel(orderId: String, reason: String): PosResult<Unit> = PosResult.Ok(Unit)
        override suspend fun onAccount(cartId: String, contacts: ReceiptContacts) = refused
        override suspend fun documentNumber(invoiceId: String): String? = null
        override suspend fun recovery(): PosResult<List<RecoveryItem>> = PosResult.Ok(emptyList())
        override suspend fun pickups(query: String?): PosResult<List<PickupOrder>> = PosResult.Ok(emptyList())
        override suspend fun collect(orderId: String) = refused
    }
}

/** Part payments (staged split) on a reserved order. Refund steps go through `SalesGateway.approve`. */
interface SplitPaymentGateway {
    val enabled: Boolean get() = true
    suspend fun find(orderId: String): PosResult<SplitSession?>
    suspend fun start(orderId: String): PosResult<SplitSession>
    suspend fun addPart(sessionId: String, tender: SplitTender, amount: Money, requestId: String, reference: String?): PosResult<SplitSession>
    /** The customer keeps only [items] (cart line id → qty); the server computes the total and posts. */
    suspend fun reduceBasket(sessionId: String, items: List<Pair<String, Double>>, notes: String?): PosResult<SplitSession>
    suspend fun cancel(sessionId: String, reason: String, feePolicy: RefundFeePolicy): PosResult<SplitSession>
    suspend fun retryFinalization(sessionId: String): PosResult<SplitSession>
    suspend fun recovery(): PosResult<List<SplitRecoveryItem>>
    /** The sale as posted (a reduced basket changes its lines on the server). */
    suspend fun cart(cartId: String): PosResult<CartProjection>

    object None : SplitPaymentGateway {
        override val enabled = false
        private val refused = PosResult.Err(PosError.BusinessRule("split_unavailable", ""))
        override suspend fun find(orderId: String): PosResult<SplitSession?> = PosResult.Ok(null)
        override suspend fun start(orderId: String) = refused
        override suspend fun addPart(sessionId: String, tender: SplitTender, amount: Money, requestId: String, reference: String?) = refused
        override suspend fun reduceBasket(sessionId: String, items: List<Pair<String, Double>>, notes: String?) = refused
        override suspend fun cancel(sessionId: String, reason: String, feePolicy: RefundFeePolicy) = refused
        override suspend fun retryFinalization(sessionId: String) = refused
        override suspend fun recovery(): PosResult<List<SplitRecoveryItem>> = PosResult.Ok(emptyList())
        override suspend fun cart(cartId: String): PosResult<CartProjection> = refused
    }
}

/**
 * Card machines (ECR). The data layer runs the terminal through its bridge, signs the answer with
 * this device's paired key and records it; the domain only sees attempts and outcomes.
 */
interface CardTerminalGateway {
    val enabled: Boolean get() = true
    suspend fun setup(): PosResult<TerminalSetup>
    suspend fun select(terminalId: String): PosResult<TerminalSetup>
    /** Admin pairs this device with the selected machine (password sign-in for this one action). */
    suspend fun pair(terminalId: String, admin: ManagerCredentials?): PosResult<TerminalSetup>
    suspend fun beginPurchase(orderId: String, terminalId: String, requestId: String): PosResult<TerminalAttempt>
    /** Adds a card part to the split payment and starts it on the machine. */
    suspend fun beginSplitPart(sessionId: String, amount: Money, terminalId: String, requestId: String): PosResult<TerminalAttempt>
    /** Runs [attempt] on the machine (or asks the machine for its status) and records the signed answer. */
    suspend fun run(attempt: TerminalAttempt, statusOnly: Boolean = false): PosResult<TerminalAttempt>
    suspend fun finalize(attemptId: String): PosResult<TerminalAttempt>
    suspend fun beginReversal(purchaseAttemptId: String, requestId: String): PosResult<TerminalAttempt>
    /**
     * Card refund of a whole sale paid in full on a card machine: an approver starts it (own sign-in,
     * [credentials] for this one call, or a scanned [badge]); the machine then gives the money back.
     */
    suspend fun beginRefund(invoiceId: String, terminalId: String, requestId: String, credentials: ManagerCredentials?, badge: String?): PosResult<TerminalAttempt> =
        PosResult.Err(PosError.BusinessRule("terminal_unavailable", ""))
    suspend fun attempt(attemptId: String): PosResult<TerminalAttempt>
    suspend fun recovery(): PosResult<List<TerminalRecoveryItem>>

    object None : CardTerminalGateway {
        override val enabled = false
        private val refused = PosResult.Err(PosError.BusinessRule("terminal_unavailable", ""))
        override suspend fun setup(): PosResult<TerminalSetup> = PosResult.Ok(TerminalSetup(emptyList(), null, appInstalled = false, paired = false))
        override suspend fun select(terminalId: String) = refused
        override suspend fun pair(terminalId: String, admin: ManagerCredentials?) = refused
        override suspend fun beginPurchase(orderId: String, terminalId: String, requestId: String) = refused
        override suspend fun beginSplitPart(sessionId: String, amount: Money, terminalId: String, requestId: String) = refused
        override suspend fun run(attempt: TerminalAttempt, statusOnly: Boolean) = refused
        override suspend fun finalize(attemptId: String) = refused
        override suspend fun beginReversal(purchaseAttemptId: String, requestId: String) = refused
        override suspend fun attempt(attemptId: String) = refused
        override suspend fun recovery(): PosResult<List<TerminalRecoveryItem>> = PosResult.Ok(emptyList())
    }
}

/**
 * Returns, old cores, warranty claims and stock by branch (phase 6). Posting a return or core and
 * deciding a claim go through `SalesGateway.approve`; everything here runs as the signed-in user.
 */
interface ReturnsGateway {
    suspend fun invoice(invoiceId: String): PosResult<InvoiceDetail>
    /** Drafts the case (sales staff); returns its id for the approver to post. */
    suspend fun draft(draft: ReturnDraft, replacement: Boolean): PosResult<String>
    suspend fun openClaim(invoiceId: String, invoiceLineId: String, serialId: String?, notes: String?): PosResult<String>
    suspend fun findSerial(serial: String): PosResult<List<WarrantySerial>>
    suspend fun claims(query: String?, status: String?): PosResult<List<WarrantyClaim>>
    suspend fun closeClaim(claimId: String): PosResult<Unit>
    suspend fun stock(stockItemId: String): PosResult<List<BranchStock>>

    object None : ReturnsGateway {
        private val refused = PosResult.Err(PosError.BusinessRule("returns_unavailable", ""))
        override suspend fun invoice(invoiceId: String): PosResult<InvoiceDetail> = refused
        override suspend fun draft(draft: ReturnDraft, replacement: Boolean): PosResult<String> = refused
        override suspend fun openClaim(invoiceId: String, invoiceLineId: String, serialId: String?, notes: String?): PosResult<String> = refused
        override suspend fun findSerial(serial: String): PosResult<List<WarrantySerial>> = PosResult.Ok(emptyList())
        override suspend fun claims(query: String?, status: String?): PosResult<List<WarrantyClaim>> = PosResult.Ok(emptyList())
        override suspend fun closeClaim(claimId: String): PosResult<Unit> = refused
        override suspend fun stock(stockItemId: String): PosResult<List<BranchStock>> = PosResult.Ok(emptyList())
    }
}

/** Holds, other-branch pickup, branch transfers and back-orders (phase 7). */
interface FulfillmentGateway {
    suspend fun create(draft: FulfillmentDraft): PosResult<String>
    suspend fun list(query: String?, status: String?): PosResult<List<FulfillmentRequest>>
    suspend fun step(requestId: String, step: FulfillmentStep): PosResult<Unit>

    object None : FulfillmentGateway {
        private val refused = PosResult.Err(PosError.BusinessRule("fulfillment_unavailable", ""))
        override suspend fun create(draft: FulfillmentDraft): PosResult<String> = refused
        override suspend fun list(query: String?, status: String?): PosResult<List<FulfillmentRequest>> = PosResult.Ok(emptyList())
        override suspend fun step(requestId: String, step: FulfillmentStep): PosResult<Unit> = refused
    }
}

/** Payment letters, the signed-in manager's signature and the business details printed on documents. */
interface LettersGateway {
    suspend fun list(source: LetterSource): PosResult<List<PaymentLetterSummary>>
    suspend fun issue(source: LetterSource, notes: String?): PosResult<String>
    suspend fun letter(letterId: String): PosResult<PaymentLetter>
    suspend fun mySignature(): PosResult<MySignature>
    /** PNG bytes drawn on the tablet. */
    suspend fun saveSignature(png: ByteArray): PosResult<MySignature>
    suspend fun profile(): PosResult<BusinessProfile>
    suspend fun saveProfile(profile: BusinessProfile): PosResult<BusinessProfile>

    object None : LettersGateway {
        private val refused = PosResult.Err(PosError.BusinessRule("letters_unavailable", ""))
        override suspend fun list(source: LetterSource): PosResult<List<PaymentLetterSummary>> = PosResult.Ok(emptyList())
        override suspend fun issue(source: LetterSource, notes: String?): PosResult<String> = refused
        override suspend fun letter(letterId: String): PosResult<PaymentLetter> = refused
        override suspend fun mySignature(): PosResult<MySignature> = refused
        override suspend fun saveSignature(png: ByteArray): PosResult<MySignature> = refused
        override suspend fun profile(): PosResult<BusinessProfile> = refused
        override suspend fun saveProfile(profile: BusinessProfile): PosResult<BusinessProfile> = refused
    }
}
