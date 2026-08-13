import XCTest
@testable import GTRCustomerCore

/// D-57 — checkout display parity with `@gtr/payments` buildCheckoutDisplay.
final class CheckoutDisplayTests: XCTestCase {
    func testPaynowKeepsUsdWithIndicativeZig() throws {
        let d = try CheckoutDisplayBuilder.build(
            usdMinor: 10_000,
            payMethod: .paynow,
            zigRatePerUsd: Decimal(string: "27.5")
        )
        XCTAssertEqual(d.payCurrency, .USD)
        XCTAssertEqual(d.payable.amountMinor, 10_000)
        XCTAssertEqual(d.browseCurrency, .USD)
        XCTAssertEqual(d.indicativeZigMinor, 275_000)
        XCTAssertNil(d.fxRateId)
    }

    func testEcocashConvertsToZigWithFxRateId() throws {
        let d = try CheckoutDisplayBuilder.build(
            usdMinor: 10_000,
            payMethod: .ecocash,
            zigRatePerUsd: 25,
            fxRateId: "rate-1"
        )
        XCTAssertEqual(d.payCurrency, .ZIG)
        XCTAssertEqual(d.payable.amountMinor, 250_000)
        XCTAssertEqual(d.fxRateId, "rate-1")
        XCTAssertEqual(d.payable.fxRateId, "rate-1")
    }

    func testEcocashRequiresPositiveZigRate() {
        XCTAssertThrowsError(
            try CheckoutDisplayBuilder.build(
                usdMinor: 10_000,
                payMethod: .ecocash,
                zigRatePerUsd: nil
            )
        ) { error in
            XCTAssertEqual(error as? CheckoutDisplayError, .dailyZigRateRequired)
        }
        XCTAssertThrowsError(
            try CheckoutDisplayBuilder.buildZigSettlement(
                usdMinor: 10_000,
                zigRatePerUsd: 0,
                fxRateId: nil
            )
        ) { error in
            XCTAssertEqual(error as? CheckoutDisplayError, .dailyZigRateRequired)
        }
    }

    func testZigSettlementFromMoneyMinorNotFloatInvent() throws {
        // 42.00 USD → 4200 minor × 26.5 = 111300 ZiG minor
        let d = try CheckoutDisplayBuilder.buildZigSettlement(
            usdMinor: 4_200,
            zigRatePerUsd: Decimal(string: "26.5"),
            fxRateId: "seed-rate"
        )
        XCTAssertEqual(d.payable.amountMinor, 111_300)
        XCTAssertEqual(d.payable.currency, .ZIG)
        XCTAssertEqual(d.fxRateId, "seed-rate")
    }
}
