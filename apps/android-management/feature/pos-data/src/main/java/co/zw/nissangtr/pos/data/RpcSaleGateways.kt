package co.zw.nissangtr.pos.data

import co.zw.nissangtr.management.rpc.CustomerGarageVehicle
import co.zw.nissangtr.management.rpc.CustomerOption
import co.zw.nissangtr.management.rpc.PosCustomerKind
import co.zw.nissangtr.management.rpc.PosInvoiceSummary
import co.zw.nissangtr.management.rpc.PosTenderLine
import co.zw.nissangtr.management.rpc.RpcClient
import co.zw.nissangtr.management.rpc.SupabaseRpcClient
import co.zw.nissangtr.pos.domain.error.PosError
import co.zw.nissangtr.pos.domain.gateway.CheckoutGateway
import co.zw.nissangtr.pos.domain.gateway.CheckoutResult
import co.zw.nissangtr.pos.domain.gateway.CustomerGateway
import co.zw.nissangtr.pos.domain.gateway.EpcGateway
import co.zw.nissangtr.pos.domain.gateway.SalesGateway
import co.zw.nissangtr.pos.domain.model.ApprovalRequest
import co.zw.nissangtr.pos.domain.model.CartProjection
import co.zw.nissangtr.pos.domain.model.Customer
import co.zw.nissangtr.pos.domain.model.CustomerDraft
import co.zw.nissangtr.pos.domain.model.CustomerKind
import co.zw.nissangtr.pos.domain.model.CurrencyCode
import co.zw.nissangtr.pos.domain.model.EpcDiagram
import co.zw.nissangtr.pos.domain.model.EpcDiagramDetail
import co.zw.nissangtr.pos.domain.model.EpcPart
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
import co.zw.nissangtr.pos.domain.model.TenderLine
import co.zw.nissangtr.pos.domain.model.VehicleModel
import co.zw.nissangtr.pos.domain.model.VehicleSelection

private const val EPC_MAKER = "nissan"

/** Checkout, customers, back office and EPC over the typed RPC client (Phase 5). */
class RpcSaleGateways(private val rpc: RpcClient) {

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

        override suspend fun approve(credentials: ManagerCredentials, request: ApprovalRequest, cartId: String) = call {
            val notes = credentials.notes.blankToNull()
            val action: suspend () -> CartProjection? = {
                when (request) {
                    is ApprovalRequest.Discount -> {
                        rpc.applyPosCartDiscount(cartId, request.percent, notes)
                        projectionOf(cartId)
                    }
                    is ApprovalRequest.PriceOverride -> {
                        rpc.applyPosLinePriceOverride(request.lineId, request.unitPrice, notes)
                        projectionOf(cartId)
                    }
                    ApprovalRequest.VoidSale -> {
                        rpc.voidPosCart(cartId, notes)
                        CartProjection.empty(CurrencyCode.USD)
                    }
                    is ApprovalRequest.Refund -> {
                        rpc.postPosRefund(request.invoice.id, notes)
                        null
                    }
                }
            }
            val live = rpc as? SupabaseRpcClient
            try {
                if (live != null) live.withManagerApproval(credentials.identifier.trim(), credentials.password, action) else action()
            } catch (e: Exception) {
                // A failed manager sign-in must read as a sign-in problem, not a server fault.
                if (e.message.orEmpty().contains("invalid", ignoreCase = true) || e.message.orEmpty().contains("credentials", ignoreCase = true)) {
                    throw PosFailure(PosError.BusinessRule("manager_sign_in", "Manager sign-in failed."))
                }
                throw e
            }
        }
    }

    val epc: EpcGateway = object : EpcGateway {
        override suspend fun variants(model: VehicleModel) = call {
            rpc.listCatalogVariants(EPC_MAKER, model.slug).map { EpcVariant(it.slug, it.chassisCode, it.engineCode, it.yearLabel) }
        }

        override suspend fun sections(model: VehicleModel, variant: EpcVariant) = call {
            rpc.listCatalogSections(EPC_MAKER, model.slug, variant.slug).sortedBy { it.sortOrder }.map { EpcSection(it.slug, it.name) }
        }

        override suspend fun diagrams(model: VehicleModel, variant: EpcVariant, section: EpcSection) = call {
            rpc.listCatalogDiagrams(EPC_MAKER, model.slug, variant.slug, section.slug).map { EpcDiagram(it.slug, it.title) }
        }

        override suspend fun diagram(model: VehicleModel, variant: EpcVariant, section: EpcSection, diagram: EpcDiagram) = call {
            val r = rpc.getCatalogDiagramBySlug(EPC_MAKER, model.slug, variant.slug, section.slug, diagram.slug)
            val refByOem = r.hotspots.associate { it.oem.trim().uppercase() to it.pncCode }
            EpcDiagramDetail(
                diagram = EpcDiagram(r.diagramSlug ?: diagram.slug, r.diagramTitle ?: diagram.title),
                imageUrl = r.imageUrl,
                parts = r.parts.distinctBy { it.oemPartNumber.trim().uppercase() }.map { p ->
                    EpcPart(
                        oemPartNumber = p.oemPartNumber,
                        name = p.stockDescription ?: p.subcategoryName ?: p.categoryName ?: p.oemPartNumber,
                        pncCode = p.pncCode ?: refByOem[p.oemPartNumber.trim().uppercase()],
                        refNo = null,
                        qtyRequired = null,
                    )
                },
            )
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
