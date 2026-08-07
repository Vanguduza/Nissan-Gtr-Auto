import CryptoKit
import Foundation
import Security

/// Raw + SHA-256 (hex) nonces for Apple / Google ID-token and OAuth PKCE.
/// Matches Android Credential Manager: hashed nonce → provider, raw → GoTrue.
public enum AuthNonce {
    /// Cryptographically random URL-safe string (~32 bytes).
    public static func randomRaw(length: Int = 32) -> String {
        var bytes = [UInt8](repeating: 0, count: max(length, 1))
        let status = SecRandomCopyBytes(kSecRandomDefault, bytes.count, &bytes)
        if status != errSecSuccess {
            bytes = (0 ..< bytes.count).map { _ in UInt8.random(in: 0 ... 255) }
        }
        let alphabet = Array("0123456789ABCDEFGHIJKLMNOPQRSTUVXYZabcdefghijklmnopqrstuvwxyz-._")
        return String(bytes.map { alphabet[Int($0) % alphabet.count] })
    }

    /// Lowercase hex SHA-256 of `raw` — pass this to `ASAuthorizationAppleIDRequest.nonce`.
    public static func sha256Hex(_ raw: String) -> String {
        let digest = SHA256.hash(data: Data(raw.utf8))
        return digest.map { String(format: "%02x", $0) }.joined()
    }

    /// Base64url (no padding) SHA-256 — OAuth PKCE `code_challenge`.
    public static func sha256Base64URL(_ raw: String) -> String {
        let digest = SHA256.hash(data: Data(raw.utf8))
        return Data(digest).base64EncodedString()
            .replacingOccurrences(of: "+", with: "-")
            .replacingOccurrences(of: "/", with: "_")
            .replacingOccurrences(of: "=", with: "")
    }
}
