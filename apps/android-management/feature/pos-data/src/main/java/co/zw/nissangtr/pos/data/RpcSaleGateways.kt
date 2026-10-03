package co.zw.nissangtr.pos.data

import co.zw.nissangtr.management.rpc.CustomerGarageVehicle
import co.zw.nissangtr.management.rpc.CustomerOption
import co.zw.nissangtr.management.rpc.PosCustomerKind
import co.zw.nissangtr.management.rpc.PosInvoiceSummary
import co.zw.nissangtr.management.rpc.PosTenderLine
import co.zw.nissangtr.management.rpc.RpcClient
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.JsonObject
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.async
import co.zw.nissangtr.pos.domain.model.EpcMissing
import co.zw.nissangtr.management.rpc.CatalogLiveException
import co.zw.nissangtr.management.rpc.ManagerApproval
import co.zw.nissangtr.pos.domain.error.PosError
import co.zw.nissangtr.pos.domain.gateway.CheckoutGateway
import co.zw.nissangtr.pos.domain.gateway.CheckoutResult
import co.zw.nissangtr.pos.domain.gateway.CompanionGateway
import co.zw.nissangtr.pos.domain.gateway.CustomerGateway
import co.zw.nissangtr.pos.domain.gateway.EpcGateway
import co.zw.nissangtr.pos.domain.gateway.SalesGateway
import co.zw.nissangtr.pos.domain.model.ApprovalRequest
import co.zw.nissangtr.pos.domain.model.CartProjection
import co.zw.nissangtr.pos.domain.model.CompanionSession
import co.zw.nissangtr.pos.domain.model.CompanionStatus
import co.zw.nissangtr.pos.domain.model.Customer
import co.zw.nissangtr.pos.domain.model.CustomerDraft
import co.zw.nissangtr.pos.domain.model.CustomerKind
import co.zw.nissangtr.pos.domain.model.CurrencyCode
import co.zw.nissangtr.pos.domain.model.EpcDiagram
import co.zw.nissangtr.pos.domain.model.EpcDiagramDetail
import co.zw.nissangtr.pos.domain.model.EpcHotspot
import co.zw.nissangtr.pos.domain.model.EpcPart
import co.zw.nissangtr.pos.domain.model.EpcSection
import co.zw.nissangtr.pos.domain.model.EpcVariant
import co.zw.nissangtr.pos.domain.model.GarageVehicle
import co.zw.nissangtr.pos.domain.model.InvoiceSummary
import co.zw.nissangtr.pos.domain.model.ManagerCredentials
import co.zw.nissangtr.pos.domain.model.ReasonCode
import co.zw.nissangtr.pos.domain.model.Money
import co.zw.nissangtr.pos.domain.model.ParkedSale
import co.zw.nissangtr.pos.domain.model.QuoteChannel
import co.zw.nissangtr.pos.domain.model.Quotation
import co.zw.nissangtr.pos.domain.model.ReceiptContacts
import co.zw.nissangtr.pos.domain.model.ScannerLink
import co.zw.nissangtr.pos.domain.model.TenderLine
import co.zw.nissangtr.pos.domain.model.VehicleModel
import co.zw.nissangtr.pos.domain.model.VehicleSelection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.net.URL


/** Checkout, customers, back office and EPC over the typed RPC client (Phase 5). */
class RpcSaleGateways(
    private val rpc: RpcClient,
    /** This counter device, recorded on badge approvals in the audit trail. */
    private val deviceId: String? = null,
) {

    val checkout: CheckoutGateway = object : CheckoutGateway {
        override suspend fun checkout(cartId: String, tenders: List<TenderLine>, contacts: ReceiptContacts) = call {
            val result = rpc.checkoutPosCartWithTenders(
                cartId = cartId,
                tenders = tenders.map { PosTenderLine(it.tender.rpcValue, it.amount.minor / 100.0, it.amount.currency.code) },
                receiptEmail = contacts.email?.takeIf { it.isNotBlank() },
                receiptWhatsappE164 = contacts.whatsappE164?.takeIf { it.isNotBlank() },
                receiptPhoneE164 = contacts.whatsappE164?.takeIf { it.isNotBlank() },
            )
            CheckoutResult(result.invoiceId, runCatching { rpc.salesInvoiceDocumentNumber(result.invoiceId) }.getOrNull())
        }

        override suspend fun requestEcoCash(msisdn: String, amount: Money, reference: String) = call {
            rpc.createEcocashIntent(
                externalRef = reference,
                payerMsisdn = msisdn,
                amount = amount.minor / 100.0,
                currency = amount.currency.toRpc(),
            )
        }
    }

    val customers: CustomerGateway = object : CustomerGateway {
        override suspend fun search(query: String) = call { rpc.searchCustomers(query).map { it.toDomain() } }

        override suspend fun create(draft: CustomerDraft) = call {
            val id = rpc.createPosCustomer(
                kind = draft.kind.toRpc(),
                displayName = draft.displayName.trim(),
                businessName = draft.businessName.blankToNull(),
                email = draft.email.blankToNull(),
                phoneE164 = draft.phoneE164.blankToNull(),
                whatsappE164 = draft.whatsappE164.blankToNull(),
            )
            draft.toCustomer(id)
        }

        override suspend fun update(customerId: String, draft: CustomerDraft) = call {
            rpc.updatePosCustomer(
                customerId = customerId,
                kind = draft.kind.toRpc(),
                displayName = draft.displayName.trim(),
                businessName = draft.businessName.blankToNull(),
                email = draft.email.blankToNull(),
                phoneE164 = draft.phoneE164.blankToNull(),
                whatsappE164 = draft.whatsappE164.blankToNull(),
            )
            draft.toCustomer(customerId)
        }

        override suspend fun garage(customerId: String) = call { rpc.listPosCustomerGarage(customerId).map { it.toDomain() } }

        override suspend fun attach(cartId: String, customerId: String?) = call { rpc.setPosCartCustomer(cartId, customerId) }

        override suspend fun saveToGarage(customerId: String, vehicle: VehicleSelection, isPrimary: Boolean) = call {
            rpc.upsertPosCustomerGarageVehicle(
                customerId = customerId,
                modelSlug = vehicle.modelSlug,
                make = "Nissan",
                model = vehicle.modelName,
                generation = vehicle.generation,
                chassisCode = vehicle.chassisCode,
                engine = vehicle.engineCode,
                isPrimary = isPrimary,
            )
            Unit
        }
    }

    val sales: SalesGateway = object : SalesGateway {
        override suspend fun parked() = call {
            rpc.listPosParkedCarts().map {
                ParkedSale(it.id, it.documentNumber, it.updatedAt, Money.ofMajor(it.total, it.currency.toDomain()), it.lineCount)
            }
        }

        override suspend fun park(cartId: String) = call {
            rpc.parkPosCart(cartId)
            Unit
        }

        override suspend fun resume(cartId: String) = call {
            val id = rpc.resumePosCart(cartId)
            projectionOf(id)
        }

        override suspend fun quotations() = call {
            rpc.listPosQuotations().map {
                Quotation(
                    id = it.id,
                    documentNumber = it.documentNumber,
                    status = it.status,
                    validUntil = it.validUntil,
                    total = Money.ofMajor(it.total, it.currency.toDomain()),
                    lineCount = it.lineCount,
                    sentChannel = it.sentChannel,
                )
            }
        }

        override suspend fun createQuotation(cartId: String, validUntil: String?, notes: String?) = call {
            rpc.createPosQuotationFromCart(cartId, validUntil.blankToNull(), notes.blankToNull())
        }

        override suspend fun sendQuotation(quotationId: String, channel: QuoteChannel, contact: String?) = call {
            rpc.sendPosQuotation(quotationId, channel.rpcValue, contact.blankToNull())
            Unit
        }

        override suspend fun convertQuotation(quotationId: String) = call {
            val cartId = rpc.convertPosQuotationToCart(quotationId)
            projectionOf(cartId)
        }

        override suspend fun recentInvoices(query: String?) = call {
            rpc.listPosRecentInvoices(query.blankToNull(), 50).map { it.toDomain() }
        }

        override suspend fun approve(
            credentials: ManagerCredentials?,
            request: ApprovalRequest,
            cartId: String,
            reason: ReasonCode?,
            notes: String?,
            badge: String?,
        ) = call {
            val notes = (notes ?: credentials?.notes).blankToNull()
            if (!badge.isNullOrBlank()) return@call approveWithBadge(badge, request, cartId, reason, notes)
            // Governed sale actions (`*_governed`) record the configured reason; the server refuses a missing one.
            val code = reason?.code.orEmpty()
            val action: suspend () -> CartProjection? = {
                when (request) {
                    is ApprovalRequest.Discount -> {
                        rpc.applyPosCartDiscountGoverned(cartId, request.percent, code, notes)
                        projectionOf(cartId)
                    }
                    is ApprovalRequest.PriceOverride -> {
                        rpc.applyPosLinePriceOverrideGoverned(request.lineId, request.unitPrice, code, notes)
                        projectionOf(cartId)
                    }
                    ApprovalRequest.VoidSale -> {
                        rpc.voidPosCartGoverned(cartId, code, notes)
                        CartProjection.empty(CurrencyCode.USD)
                    }
                    is ApprovalRequest.Refund -> {
                        rpc.postPosRefundGoverned(request.invoice.id, code, notes)
                        null
                    }
                    is ApprovalRequest.CashOut -> {
                        rpc.recordPosTillCashMovement(
                            request.sessionId,
                            request.kind.rpcValue,
                            request.amount.minor / 100.0,
                            request.reason.code,
                            listOfNotNull(request.notes, notes).joinToString(" · ").ifBlank { null },
                        )
                        null
                    }
                    is ApprovalRequest.TillVariance -> {
                        rpc.approvePosTillVariance(request.sessionId, request.reason.code, notes)
                        null
                    }
                    is ApprovalRequest.Handover -> {
                        rpc.handoverPosTillSession(request.sessionId, request.to.userId, notes)
                        null
                    }
                }
            }
            // The tablet wraps the live client (offline catalogue); approval must still reach it.
            val live = rpc as? ManagerApproval
            try {
                when {
                    // Policy allows the cashier: runs on their own session.
                    credentials == null -> action()
                    live != null -> live.withManagerApproval(credentials.identifier.trim(), credentials.password, action)
                    else -> action()
                }
            } catch (e: Exception) {
                // A failed manager sign-in must read as a sign-in problem, not a server fault.
                if (credentials != null && (e.message.orEmpty().contains("invalid login", ignoreCase = true) || e.message.orEmpty().contains("credentials", ignoreCase = true))) {
                    throw PosFailure(PosError.BusinessRule("manager_sign_in", "Manager sign-in failed."))
                }
                throw e
            }
        }
    }

    /** One server call validates the badge, runs the action as approved by its holder, and audits it. */
    private suspend fun approveWithBadge(badge: String, request: ApprovalRequest, cartId: String, reason: ReasonCode?, notes: String?): CartProjection? {
        val code = reason?.code
        val (action, args) = when (request) {
            is ApprovalRequest.Discount -> "discount" to mapOf("cart_id" to cartId, "percent" to request.percent, "reason_code" to code, "notes" to notes)
            is ApprovalRequest.PriceOverride -> "price_override" to mapOf("line_id" to request.lineId, "unit_price" to request.unitPrice, "reason_code" to code, "notes" to notes)
            ApprovalRequest.VoidSale -> "void_sale" to mapOf("cart_id" to cartId, "reason_code" to code, "notes" to notes)
            is ApprovalRequest.Refund -> "refund" to mapOf("invoice_id" to request.invoice.id, "reason_code" to code, "notes" to notes)
            is ApprovalRequest.CashOut -> "cash_out" to mapOf(
                "session_id" to request.sessionId,
                "kind" to request.kind.rpcValue,
                "amount" to request.amount.minor / 100.0,
                "reason_code" to request.reason.code,
                "notes" to listOfNotNull(request.notes, notes).joinToString(" · ").ifBlank { null },
            )
            is ApprovalRequest.TillVariance -> "till_variance" to mapOf("session_id" to request.sessionId, "reason_code" to request.reason.code, "notes" to notes)
            is ApprovalRequest.Handover -> "till_handover" to mapOf("session_id" to request.sessionId, "new_operator_user_id" to request.to.userId, "notes" to notes)
        }
        val outcome = rpc.posBadgeApprove(badge, action, args, deviceId)
        if (!outcome.ok) throw PosFailure(PosError.BusinessRule("badge", outcome.error ?: "Badge approval refused."))
        return when (request) {
            is ApprovalRequest.Discount, is ApprovalRequest.PriceOverride -> projectionOf(cartId)
            ApprovalRequest.VoidSale -> CartProjection.empty(CurrencyCode.USD)
            else -> null
        }
    }

    val epc: EpcGateway = object : EpcGateway {
        // Variants are published vehicles (slug = vehicle-master id). Sections, diagram lists, parts
        // and images come from the full catalogue (`catalog-live-r2`: Supabase routing + R2 shards),
        // never from fixture rows; unavailable content fails closed. Section slug = section id.
        override suspend fun variants(model: VehicleModel) = call {
            rpc.listVehicleMaster()
                .filter { it.familySlug == model.slug }
                .map { EpcVariant(it.id, it.chassisCode, it.engineCode, it.yearLabel) }
        }

        override suspend fun sections(model: VehicleModel, variant: EpcVariant) = call {
            live {
                rpc.catalogLive("staff-sections", mapOf("variant_id" to variant.slug))["sections"]
                    ?.jsonArray.orEmpty()
                    .mapNotNull { it as? JsonObject }
                    .mapNotNull { s ->
                        val id = s.str("section_id") ?: return@mapNotNull null
                        EpcSection(id, s.str("display_name") ?: s.str("section_slug") ?: id)
                    }
            }
        }

        override suspend fun diagrams(model: VehicleModel, variant: EpcVariant, section: EpcSection) = call {
            live {
                val out = mutableListOf<EpcDiagram>()
                var offset = 0
                while (true) {
                    val page = rpc.catalogLive(
                        "staff-diagrams",
                        mapOf(
                            "variant_id" to variant.slug,
                            "section_id" to section.slug,
                            "limit" to "200",
                            "offset" to offset.toString(),
                        ),
                    )["diagrams"]?.jsonArray.orEmpty().mapNotNull { it as? JsonObject }
                    page.forEach { d ->
                        val id = d.str("diagram_id") ?: return@forEach
                        out += EpcDiagram(slug = id, title = d.str("title") ?: d.str("name_en") ?: id, id = id)
                    }
                    if (page.size < 200) break
                    offset += page.size
                }
                out
            }
        }

        override suspend fun diagram(model: VehicleModel, variant: EpcVariant, section: EpcSection, diagram: EpcDiagram) = call {
            val id = diagram.id ?: throw PosFailure(PosError.BusinessRule("catalog_unavailable", ""))
            coroutineScope {
                val image = async { runCatching { rpc.catalogLive("diagram-image", mapOf("diagram_id" to id)) } }
                val parts = async { runCatching { rpc.catalogLive("staff-diagram-parts", mapOf("diagram_id" to id)) } }
                val img = image.await()
                val prt = parts.await()
                if (img.isFailure && prt.isFailure) throw PosFailure(liveError(prt.exceptionOrNull()))
                val seen = HashSet<String>()
                val rows = prt.getOrNull()?.get("parts")?.jsonArray.orEmpty().mapNotNull { it as? JsonObject }
                EpcDiagramDetail(
                    diagram = diagram,
                    imageUrl = img.getOrNull()?.str("signed_url"),
                    parts = rows.mapNotNull { p ->
                        val oem = (p.str("display_oem_number") ?: p.str("normalized_oem_number"))?.trim() ?: return@mapNotNull null
                        if (!seen.add(oem.uppercase().filter { it.isLetterOrDigit() })) return@mapNotNull null
                        EpcPart(
                            oemPartNumber = oem,
                            name = p.str("name") ?: p.str("description") ?: p.str("subcategory_name") ?: oem,
                            pncCode = p.str("pnc_code"),
                            // The reference number printed on the diagram artwork.
                            refNo = p.str("callout_ref"),
                            qtyRequired = p.str("quantity"),
                        )
                    },
                    // Callout boxes travel on the part rows as fractions of the image; a part may
                    // appear at several callouts.
                    hotspots = rows.mapNotNull { p ->
                        val oem = (p.str("display_oem_number") ?: p.str("normalized_oem_number"))?.trim() ?: return@mapNotNull null
                        val x = p.num("bbox_x") ?: return@mapNotNull null
                        val y = p.num("bbox_y") ?: return@mapNotNull null
                        val w = p.num("bbox_width") ?: return@mapNotNull null
                        val h = p.num("bbox_height") ?: return@mapNotNull null
                        EpcHotspot(oem, p.str("pnc_code"), x, y, w, h)
                    },
                    missing = (img.exceptionOrNull() ?: prt.exceptionOrNull())?.let(::liveMissing),
                )
            }
        }

        override suspend fun image(url: String) = call { downloadDiagram(url) }
    }

    val companion: CompanionGateway = object : CompanionGateway {
        override suspend fun create(cartId: String) = call {
            val created = rpc.createPosScanSession(cartId)
            CompanionSession(created.sessionId, cartId, created.pairingCode, created.expiresAt, CompanionStatus.Open)
        }

        override suspend fun revoke(sessionId: String) = call { rpc.revokePosScanSession(sessionId); Unit }

        override suspend fun status(sessionId: String) = call {
            when (rpc.getPosScanSessionStatus(sessionId)?.lowercase()) {
                "open" -> CompanionStatus.Open
                "claimed" -> CompanionStatus.Claimed
                "expired" -> CompanionStatus.Expired
                else -> CompanionStatus.Revoked
            }
        }

        override suspend fun cart(cartId: String) = call { projectionOf(cartId) }

        override suspend fun claim(pairingCode: String) = call {
            val sessionId = rpc.claimPosScanSession(pairingCode)
            val cartId = rpc.getPosScanSessionCartId(sessionId)
                ?: throw PosFailure(PosError.BusinessRule("companion_cart", "This pairing has no open sale."))
            ScannerLink(sessionId, cartId)
        }

        override suspend fun addFromQr(cartId: String, payload: String) = call {
            rpc.addCartLineFromQr(cartId, payload, 1.0)
            // Show the part number the inventory label carries (gtr://part/{oem}?batch=…), not the raw payload.
            Regex("^gtr://part/([^?]+)").find(payload.trim())?.groupValues?.get(1)
                ?.let { java.net.URLDecoder.decode(it, "UTF-8") } ?: payload
        }
    }

    /** Server cart in its own currency (resume / quote conversion can bring back a ZiG cart). */
    private suspend fun projectionOf(cartId: String): CartProjection {
        val currency = rpc.posCartCurrency(cartId)?.toDomain() ?: CurrencyCode.USD
        return cartProjection(cartId, rpc.listPosCartLines(cartId), currency)
    }
}

private fun String?.blankToNull(): String? = this?.trim()?.takeIf { it.isNotEmpty() }

private fun CustomerKind.toRpc() = if (this == CustomerKind.Business) PosCustomerKind.BUSINESS else PosCustomerKind.INDIVIDUAL

private fun CustomerOption.toDomain() = Customer(
    id = id,
    displayName = displayName,
    kind = if (kind == PosCustomerKind.BUSINESS) CustomerKind.Business else CustomerKind.Individual,
    businessName = businessName,
    email = email,
    phoneE164 = phoneE164,
    whatsappE164 = whatsappE164,
)

private fun CustomerDraft.toCustomer(id: String) = Customer(
    id = id,
    displayName = displayName.trim(),
    kind = kind,
    businessName = businessName.blankToNull(),
    email = email.blankToNull(),
    phoneE164 = phoneE164.blankToNull(),
    whatsappE164 = whatsappE164.blankToNull(),
)

private fun CustomerGarageVehicle.toDomain() = GarageVehicle(
    id = id,
    modelSlug = modelSlug,
    model = model,
    generation = generation,
    chassisCode = chassisCode,
    engine = engine,
    isPrimary = isPrimary,
)

private fun PosInvoiceSummary.toDomain() = InvoiceSummary(
    id = id,
    documentNumber = documentNumber,
    customerName = customerName,
    total = Money.ofMajor(total, currency.toDomain()),
    postedAt = postedAt,
    vehicleLabel = listOfNotNull(vehicleModelName, vehicleChassisCode, vehicleEngineCode).joinToString(" ").ifBlank { null },
)

/** Exploded diagrams are small line art; anything past this is refused rather than decoded. */
private const val MAX_DIAGRAM_BYTES = 8 * 1024 * 1024

private suspend fun downloadDiagram(url: String): ByteArray = withContext(Dispatchers.IO) {
    val connection = URL(url).openConnection().apply {
        connectTimeout = 10_000
        readTimeout = 20_000
    }
    connection.getInputStream().use { input ->
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(16 * 1024)
        while (true) {
            val n = input.read(buffer)
            if (n < 0) break
            require(out.size() + n <= MAX_DIAGRAM_BYTES) { "diagram image too large" }
            out.write(buffer, 0, n)
        }
        out.toByteArray()
    }
}

private fun JsonObject.str(key: String): String? =
    (this[key] as? JsonPrimitive)?.contentOrNull?.trim()?.takeIf { it.isNotEmpty() }

private fun JsonObject.num(key: String): Double? = (this[key] as? JsonPrimitive)?.doubleOrNull

private fun liveMissing(e: Throwable): EpcMissing = when {
    e is CatalogLiveException && e.publishing -> EpcMissing.Publishing
    e is CatalogLiveException && e.notConnected -> EpcMissing.NotConnected
    else -> EpcMissing.Unavailable
}

private fun liveError(e: Throwable?): PosError = when (e?.let(::liveMissing)) {
    EpcMissing.Publishing -> PosError.BusinessRule("catalog_publishing", "")
    EpcMissing.NotConnected -> PosError.BusinessRule("catalog_not_connected", "")
    else -> PosError.BusinessRule("catalog_unavailable", "")
}

/** Runs a live-catalogue read, turning its fail-closed refusals into operator errors. */
private inline fun <T> live(block: () -> T): T = try {
    block()
} catch (e: CatalogLiveException) {
    throw PosFailure(liveError(e))
}
