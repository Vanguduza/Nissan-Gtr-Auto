// iOS stub — AVFoundation QRScanner (Batch 5: Android first).
// Mirrors bridges/contracts/qr-inventory.ts QrScannerBridge.

import Foundation

public enum CameraPermissionStatus: String, Sendable {
    case granted, denied, restricted, notDetermined = "not_determined"
}

public struct QrScanResult: Sendable {
    public let rawValue: String
    public let scannedAt: String // ISO-8601
}

public protocol QrScannerBridge: Sendable {
    func getCameraPermissionStatus() async -> CameraPermissionStatus
    func requestCameraPermission() async -> CameraPermissionStatus
    func scanOnce() async throws -> QrScanResult
    func cancel() async
}

/// Placeholder — implement with AVFoundation (no WebView / HTML5 QR).
public final class StubQrScannerBridge: QrScannerBridge {
    public init() {}

    public func getCameraPermissionStatus() async -> CameraPermissionStatus {
        .notDetermined
    }

    public func requestCameraPermission() async -> CameraPermissionStatus {
        .denied
    }

    public func scanOnce() async throws -> QrScanResult {
        throw NSError(
            domain: "QRScanner",
            code: 1,
            userInfo: [NSLocalizedDescriptionKey: "iOS QRScanner not implemented yet"],
        )
    }

    public func cancel() async {}
}
