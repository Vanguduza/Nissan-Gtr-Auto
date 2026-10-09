package co.zw.nissangtr.pos.ui.fakes

import co.zw.nissangtr.pos.domain.model.CartLine
import co.zw.nissangtr.pos.domain.model.CartProjection
import co.zw.nissangtr.pos.domain.model.CatalogPart
import co.zw.nissangtr.pos.domain.model.CurrencyCode
import co.zw.nissangtr.pos.domain.model.Money
import co.zw.nissangtr.pos.domain.model.Operator
import co.zw.nissangtr.pos.domain.model.VehicleCascade
import co.zw.nissangtr.pos.domain.model.VehicleModel
import co.zw.nissangtr.pos.domain.state.PosState

/** Test-only fixtures (Blueprint §0.1.2: sample data never ships outside src/test). */
object PosFixtures {
    private fun usd(v: Double) = Money.ofMajor(v, CurrencyCode.USD)

    val oilFilter = CatalogPart("si-1", "15208-65F0A", "Oil Filter", usd(12.50), 42.0, null)
    val brakePads = CatalogPart("si-2", "D1060-JF00A", "Front Brake Pad Set", usd(96.00), 8.0, null)
    val airFilter = CatalogPart("si-3", "16546-JF00A", "Air Filter", usd(24.00), 15.0, null)
    val sparkPlug = CatalogPart("si-4", "22401-JF01B", "Spark Plug (Iridium)", usd(19.50), 60.0, null)
    val bestSellers = listOf(oilFilter, brakePads, airFilter, sparkPlug)

    val operator = Operator("Tendai Moyo", "Sales")
    val models = listOf(VehicleModel("gt-r", "GT-R"), VehicleModel("navara", "Navara"), VehicleModel("x-trail", "X-Trail"))

    fun line(id: String, part: CatalogPart, qty: Double) = CartLine(
        lineId = id,
        stockItemId = part.stockItemId!!,
        oemPartNumber = part.oemPartNumber,
        name = part.name,
        qty = qty,
        unitPrice = part.price!!,
        lineTotal = Money(part.price!!.minor * qty.toLong(), CurrencyCode.USD),
        isCoreCharge = false,
        imageUrl = null,
    )

    val cart = CartProjection(
        cartId = "cart-1",
        currency = CurrencyCode.USD,
        lines = listOf(line("l1", oilFilter, 1.0), line("l2", brakePads, 1.0), line("l3", sparkPlug, 2.0)),
        subtotal = usd(147.50),
        discount = usd(0.0),
        total = usd(147.50),
    )

    val homeWithSale = PosState(
        operator = operator,
        cascade = VehicleCascade(models = models),
        bestSellers = bestSellers,
        cart = cart,
    )

    val homeEmpty = PosState(operator = operator, cascade = VehicleCascade(models = models))
}
