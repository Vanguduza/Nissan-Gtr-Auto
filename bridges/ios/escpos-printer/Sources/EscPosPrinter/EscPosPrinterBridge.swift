// iOS stub — CoreBluetooth ESC/POS (Batch 5: Android first).
// Mirrors bridges/contracts/qr-inventory.ts EscPosPrinterBridge.

import Foundation

public enum BluetoothPermissionStatus: String, Sendable {
    case granted, denied, restricted, notDetermined = "not_determined"
}

public enum InventoryQrValuation: String, Sendable {
    case FIFO, AVG
}

public struct EscPosPrintJob: Sendable {
    public let qrPayload: String
    public let oemPartNumber: String
    public let batchCode: String
    public let valuation: InventoryQrValuation
    public let labelDate: String?

    public init(
        qrPayload: String,
        oemPartNumber: String,
        batchCode: String,
        valuation: InventoryQrValuation,
        labelDate: String? = nil
    ) {
        self.qrPayload = qrPayload
        self.oemPartNumber = oemPartNumber
        self.batchCode = batchCode
        self.valuation = valuation
        self.labelDate = labelDate
    }
}

public struct EscPosReceiptLine: Sendable {
    public let text: String
    public let emphasis: Bool

    public init(text: String, emphasis: Bool = false) {
        self.text = text
        self.emphasis = emphasis
    }
}

public protocol EscPosPrinterBridge: Sendable {
    func configurePrinterAddress(_ address: String) async
    func getBluetoothPermissionStatus() async -> BluetoothPermissionStatus
    func requestBluetoothPermission() async -> BluetoothPermissionStatus
    func connect() async throws
    func disconnect() async
    func isConnected() async -> Bool
    func printInventoryLabel(_ job: EscPosPrintJob) async throws
    func printReceiptLines(_ lines: [EscPosReceiptLine]) async throws
    func printRaw(_ bytes: Data) async throws
}

/// Placeholder — implement with CoreBluetooth (no Web Bluetooth).
public final class StubEscPosPrinterBridge: EscPosPrinterBridge {
    public init() {}

    public func configurePrinterAddress(_ address: String) async {}

    public func getBluetoothPermissionStatus() async -> BluetoothPermissionStatus {
        .notDetermined
    }

    public func requestBluetoothPermission() async -> BluetoothPermissionStatus {
        .denied
    }

    public func connect() async throws {
        throw NSError(
            domain: "EscPosPrinter",
            code: 1,
            userInfo: [NSLocalizedDescriptionKey: "iOS ESC/POS not implemented yet"],
        )
    }

    public func disconnect() async {}

    public func isConnected() async -> Bool { false }

    public func printInventoryLabel(_ job: EscPosPrintJob) async throws {
        try await connect()
    }

    public func printReceiptLines(_ lines: [EscPosReceiptLine]) async throws {
        try await connect()
    }

    public func printRaw(_ bytes: Data) async throws {
        try await connect()
    }
}
