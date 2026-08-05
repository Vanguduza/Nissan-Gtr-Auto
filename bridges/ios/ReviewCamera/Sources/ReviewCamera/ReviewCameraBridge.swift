// Bridge-First review photo capture — UIKit camera only (no WebView / HTML5).
// Contract-aligned with bridges/contracts/pod.ts PodCameraBridge (local path only).

import AVFoundation
import Foundation
import UIKit
import UniformTypeIdentifiers

public enum ReviewCameraPermission: String, Sendable {
    case granted, denied, restricted, notDetermined = "not_determined"
}

public struct ReviewCameraCapture: Sendable {
    public let localPath: String
    public let mimeType: String
    public let capturedAt: String

    public init(localPath: String, mimeType: String, capturedAt: String) {
        self.localPath = localPath
        self.mimeType = mimeType
        self.capturedAt = capturedAt
    }
}

@MainActor
public protocol ReviewCameraBridge: AnyObject {
    func getCameraPermissionStatus() -> ReviewCameraPermission
    func requestCameraPermission() async -> ReviewCameraPermission
    /// Opens native camera UI; returns a local JPEG path (no network).
    func capturePhoto() async throws -> ReviewCameraCapture
}

/// UIImagePickerController camera capture for product review photos.
@MainActor
public final class UIKitReviewCameraBridge: NSObject, ReviewCameraBridge {
    private var continuation: CheckedContinuation<ReviewCameraCapture, Error>?

    public override init() {
        super.init()
    }

    public func getCameraPermissionStatus() -> ReviewCameraPermission {
        switch AVCaptureDevice.authorizationStatus(for: .video) {
        case .authorized: return .granted
        case .denied: return .denied
        case .restricted: return .restricted
        case .notDetermined: return .notDetermined
        @unknown default: return .denied
        }
    }

    public func requestCameraPermission() async -> ReviewCameraPermission {
        let status = AVCaptureDevice.authorizationStatus(for: .video)
        if status == .authorized { return .granted }
        if status == .denied || status == .restricted { return .denied }
        let granted = await AVCaptureDevice.requestAccess(for: .video)
        return granted ? .granted : .denied
    }

    public func capturePhoto() async throws -> ReviewCameraCapture {
        let perm = await requestCameraPermission()
        guard perm == .granted else {
            throw NSError(
                domain: "ReviewCamera",
                code: 1,
                userInfo: [NSLocalizedDescriptionKey: "Camera permission denied"]
            )
        }
        guard UIImagePickerController.isSourceTypeAvailable(.camera) else {
            throw NSError(
                domain: "ReviewCamera",
                code: 2,
                userInfo: [NSLocalizedDescriptionKey: "Camera not available on this device"]
            )
        }
        return try await withCheckedThrowingContinuation { cont in
            self.continuation = cont
            let picker = UIImagePickerController()
            picker.sourceType = .camera
            picker.cameraCaptureMode = .photo
            picker.delegate = self
            picker.modalPresentationStyle = .fullScreen
            guard let root = Self.topViewController() else {
                cont.resume(
                    throwing: NSError(
                        domain: "ReviewCamera",
                        code: 3,
                        userInfo: [NSLocalizedDescriptionKey: "No presenter for camera"]
                    )
                )
                self.continuation = nil
                return
            }
            root.present(picker, animated: true)
        }
    }

    private static func topViewController(
        base: UIViewController? = UIApplication.shared.connectedScenes
            .compactMap { ($0 as? UIWindowScene)?.keyWindow }
            .first?
            .rootViewController
    ) -> UIViewController? {
        if let nav = base as? UINavigationController {
            return topViewController(base: nav.visibleViewController)
        }
        if let tab = base as? UITabBarController {
            return topViewController(base: tab.selectedViewController)
        }
        if let presented = base?.presentedViewController {
            return topViewController(base: presented)
        }
        return base
    }
}

extension UIKitReviewCameraBridge: UIImagePickerControllerDelegate, UINavigationControllerDelegate {
    public func imagePickerControllerDidCancel(_ picker: UIImagePickerController) {
        picker.dismiss(animated: true)
        continuation?.resume(
            throwing: NSError(
                domain: "ReviewCamera",
                code: 4,
                userInfo: [NSLocalizedDescriptionKey: "Camera cancelled"]
            )
        )
        continuation = nil
    }

    public func imagePickerController(
        _ picker: UIImagePickerController,
        didFinishPickingMediaWithInfo info: [UIImagePickerController.InfoKey: Any]
    ) {
        picker.dismiss(animated: true)
        defer { continuation = nil }
        guard let image = info[.originalImage] as? UIImage,
              let data = image.jpegData(compressionQuality: 0.85)
        else {
            continuation?.resume(
                throwing: NSError(
                    domain: "ReviewCamera",
                    code: 5,
                    userInfo: [NSLocalizedDescriptionKey: "Could not encode JPEG"]
                )
            )
            return
        }
        let url = FileManager.default.temporaryDirectory
            .appendingPathComponent("review-\(UUID().uuidString).jpg")
        do {
            try data.write(to: url, options: .atomic)
            let iso = ISO8601DateFormatter().string(from: Date())
            continuation?.resume(
                returning: ReviewCameraCapture(
                    localPath: url.path,
                    mimeType: "image/jpeg",
                    capturedAt: iso
                )
            )
        } catch {
            continuation?.resume(throwing: error)
        }
    }
}
