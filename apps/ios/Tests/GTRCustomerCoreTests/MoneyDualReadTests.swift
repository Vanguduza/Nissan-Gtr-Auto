import XCTest
@testable import GTRCustomerCore

/// H4 / B-MONEY-1 — dual-read helpers (parity with `@gtr/shared` money.ts / Android MoneyDualRead).
final class MoneyDualReadTests: XCTestCase {
    func testToAmountMinorConvertsUsdMajorsToCents() throws {
        XCTAssertEqual(try MoneyDualRead.toAmountMinor(12.34, currency: .USD), 1234)
        XCTAssertEqual(try MoneyDualRead.toAmountMinor(0, currency: .USD), 0)
        XCTAssertEqual(try MoneyDualRead.toAmountMinor(-1.5, currency: .ZIG), -150)
    }

    func testPreferAmountMinorWinsOverDivergentMajor() throws {
        let minor = try MoneyDualRead.preferAmountMinor(
            amountMinor: 1999,
            amountMajor: 99.99,
            currency: .USD
        )
        XCTAssertEqual(minor, 1999)
        let display = try MoneyDualRead.displayMajorFromDual(
            amountMinor: 1999,
            amountMajor: 1,
            currency: .USD
        )
        XCTAssertEqual(display, Decimal(string: "19.99"))
    }

    func testPreferAmountMinorFallsBackToMajorWhenMinorNil() throws {
        let minor = try MoneyDualRead.preferAmountMinor(
            amountMinor: nil,
            amountMajor: 12.34,
            currency: .USD
        )
        XCTAssertEqual(minor, 1234)
        let unit = try MoneyDualRead.displayUnitPriceMajor(
            unitPrice: 12.34,
            unitPriceMinor: nil,
            currency: .USD
        )
        XCTAssertEqual(unit, Decimal(string: "12.34"))
    }

    func testPreferAmountMinorThrowsWhenBothAbsent() {
        XCTAssertThrowsError(
            try MoneyDualRead.preferAmountMinor(
                amountMinor: nil,
                amountMajor: nil,
                currency: .USD
            )
        ) { error in
            XCTAssertEqual(error as? MoneyDualRead.MoneyError, .bothAbsent)
        }
    }

    func testDisplayLineTotalMajorDualRead() throws {
        let total = try MoneyDualRead.displayLineTotalMajor(
            lineTotal: 1,
            lineTotalMinor: 1999,
            currency: .USD
        )
        XCTAssertEqual(total, Decimal(string: "19.99"))
    }

    func testSumPreferAmountMinorMixesMinorAndMajorFallback() throws {
        let sum = try MoneyDualRead.sumPreferAmountMinor(
            rows: [
                (amountMinor: 1000, amountMajor: 9.99),
                (amountMinor: nil, amountMajor: 2.5),
            ],
            currency: .USD
        )
        XCTAssertEqual(sum, 1250)
        XCTAssertEqual(MoneyDualRead.fromAmountMinor(sum, currency: .USD), Decimal(string: "12.5"))
    }

    func testCartLineSummaryExtensionsPreferMinor() {
        let line = CartLineSummary(
            id: UUID(),
            stockItemId: UUID(),
            oemPartNumber: "OEM",
            qty: 2,
            unitPrice: 1,
            unitPriceMinor: 2550,
            lineTotal: 1,
            lineTotalMinor: 5100,
            currency: .USD
        )
        XCTAssertEqual(line.displayUnitPrice(), Decimal(string: "25.5"))
        XCTAssertEqual(line.displayLineTotal(), Decimal(string: "51"))
    }

    func testCartSummaryDisplaySubtotalPrefersLineMinors() {
        let cart = CartSummary(
            id: UUID(),
            currency: .USD,
            fulfillmentMode: .immediate,
            exchangeRateApplied: 1,
            lines: [
                CartLineSummary(
                    id: UUID(),
                    stockItemId: UUID(),
                    oemPartNumber: "A",
                    qty: 1,
                    unitPrice: 9.99,
                    unitPriceMinor: 1000,
                    lineTotal: 9.99,
                    lineTotalMinor: 1000,
                    currency: .USD
                ),
                CartLineSummary(
                    id: UUID(),
                    stockItemId: UUID(),
                    oemPartNumber: "B",
                    qty: 1,
                    unitPrice: 2.5,
                    lineTotal: 2.5,
                    currency: .USD
                ),
            ]
        )
        XCTAssertEqual(cart.displaySubtotal(), Decimal(string: "12.5"))
    }

    func testCustomerOrderDisplayPrefersHeaderMinors() {
        let order = CustomerOrder(
            invoiceId: UUID(),
            status: "posted",
            currency: .USD,
            subtotal: 1,
            total: 1,
            amountPaid: 0,
            amountOpen: 1,
            subtotalMinor: 1999,
            totalMinor: 1999,
            amountPaidMinor: 500,
            amountOpenMinor: nil
        )
        XCTAssertEqual(order.displayTotal(), Decimal(string: "19.99"))
        XCTAssertEqual(order.displayAmountPaid(), Decimal(string: "5"))
        XCTAssertEqual(order.displayAmountOpen(), Decimal(string: "14.99"))
    }
}
