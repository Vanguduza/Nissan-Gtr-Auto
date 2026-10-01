package co.zw.nissangtr.pos.ui.fakes

import co.zw.nissangtr.pos.domain.gateway.CheckoutGateway
import co.zw.nissangtr.pos.domain.gateway.CheckoutResult
import co.zw.nissangtr.pos.domain.gateway.CustomerGateway
import co.zw.nissangtr.pos.domain.gateway.EpcGateway
import co.zw.nissangtr.pos.domain.gateway.SalesGateway
import co.zw.nissangtr.pos.domain.model.ApprovalRequest
import co.zw.nissangtr.pos.domain.model.CartProjection
import co.zw.nissangtr.pos.domain.model.CurrencyCode
import co.zw.nissangtr.pos.domain.model.Customer
import co.zw.nissangtr.pos.domain.model.CustomerDraft
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
import co.zw.nissangtr.pos.domain.result.PosResult

/** Test-only fakes for the sale, customer, back-office and EPC gateways. */
object FakeSaleGateways {
    val customer = Customer("c1", "Rudo Chikwanha", co.zw.nissangtr.pos.domain.model.CustomerKind.Individual, null, "rudo@example.com", "+263771234567", "+263771234567")
    val garage = listOf(
        GarageVehicle("g1", "gt-r", "GT-R", "R35", "R35", "VR38DETT", true),
        GarageVehicle("g2", "navara", "Navara", "D40", "D40", "YD25", false),
    )

    val checkout = object : CheckoutGateway {
        override suspend fun checkout(cartId: String, tenders: List<TenderLine>, contacts: ReceiptContacts) =
            PosResult.Ok(CheckoutResult("inv-1", "INV-000123"))
        override suspend fun requestEcoCash(msisdn: String, amount: Money, reference: String) = PosResult.Ok("ECO-REF-1")
    }

    val customers = object : CustomerGateway {
        override suspend fun search(query: String) = PosResult.Ok(listOf(customer))
        override suspend fun create(draft: CustomerDraft) = PosResult.Ok(customer.copy(id = "c2", displayName = draft.displayName))
        override suspend fun update(customerId: String, draft: CustomerDraft) = PosResult.Ok(customer.copy(displayName = draft.displayName))
        override suspend fun garage(customerId: String) = PosResult.Ok(garage)
        override suspend fun attach(cartId: String, customerId: String?) = PosResult.Ok(Unit)
        override suspend fun saveToGarage(customerId: String, vehicle: VehicleSelection, isPrimary: Boolean) = PosResult.Ok(Unit)
    }

    val sales = object : SalesGateway {
        override suspend fun parked() = PosResult.Ok(listOf(ParkedSale("p1", "PARK-001", "2026-10-01T09:12:00", Money.ofMajor(54.0, CurrencyCode.USD), 2)))
        override suspend fun park(cartId: String) = PosResult.Ok(Unit)
        override suspend fun resume(cartId: String) = PosResult.Ok(PosFixtures.cart)
        override suspend fun quotations() = PosResult.Ok(listOf(Quotation("q1", "QUO-0007", "draft", "2026-10-15", Money.ofMajor(220.0, CurrencyCode.USD), 3, null)))
        override suspend fun createQuotation(cartId: String, validUntil: String?, notes: String?) = PosResult.Ok("q2")
        override suspend fun sendQuotation(quotationId: String, channel: QuoteChannel, contact: String?) = PosResult.Ok(Unit)
        override suspend fun convertQuotation(quotationId: String) = PosResult.Ok(PosFixtures.cart)
        override suspend fun recentInvoices(query: String?) = PosResult.Ok(listOf(InvoiceSummary("inv-9", "INV-000099", "Rudo Chikwanha", Money.ofMajor(96.0, CurrencyCode.USD), "2026-09-30T15:20:00", "GT-R R35 VR38DETT")))
        override suspend fun approve(credentials: ManagerCredentials, request: ApprovalRequest, cartId: String): PosResult<CartProjection?> =
            PosResult.Ok(if (request is ApprovalRequest.VoidSale) CartProjection.empty(CurrencyCode.USD) else null)
    }

    val epc = object : EpcGateway {
        override suspend fun variants(model: VehicleModel) = PosResult.Ok(listOf(EpcVariant("r35", "R35", "VR38DETT", "2007–")))
        override suspend fun sections(model: VehicleModel, variant: EpcVariant) = PosResult.Ok(listOf(EpcSection("brakes", "Brakes")))
        override suspend fun diagrams(model: VehicleModel, variant: EpcVariant, section: EpcSection) = PosResult.Ok(listOf(EpcDiagram("front-brake", "Front brake")))
        override suspend fun diagram(model: VehicleModel, variant: EpcVariant, section: EpcSection, diagram: EpcDiagram) =
            PosResult.Ok(EpcDiagramDetail(diagram, null, listOf(EpcPart("D1060-JF00A", "Front Brake Pad Set", "41060", null, null))))
    }
}
