import AuthenticationServices
import Foundation

#if canImport(UIKit)
import UIKit
#endif

/// Google via Supabase OAuth + PKCE in `ASWebAuthenticationSession` — no GoogleSignIn SPM.
/// Callback: `gtrcustomer://auth/callback` (allow-listed in `docs/CUSTOMER_OAUTH_SETUP.md`).
@MainActor
public enum GoogleOAuthBrowser {
    public static func signIn(using goTrue: GoTrueAuthClient) async throws -> GoTrueAuthClient.Session {
        let codeVerifier = AuthNonce.randomRaw(length: 64)
        let challenge = AuthNonce.sha256Base64URL(codeVerifier)
        let authorizeURL = try goTrue.oauthAuthorizeURL(
            provider: .google,
            codeChallenge: challenge
        )

        let callbackURL = try await WebAuthSession.start(
            url: authorizeURL,
            callbackScheme: GoTrueAuthClient.preferredCallbackScheme
        )

        guard let payload = GoTrueAuthClient.parseAuthCallback(callbackURL) else {
            throw StorefrontError.message("Google OAuth callback was empty.")
        }
        switch payload {
        case .error(let message):
            throw StorefrontError.message(message)
        case .session(let session):
            return session
        case .pkce(let code):
            return try await goTrue.exchangePKCE(authCode: code, codeVerifier: codeVerifier)
        }
    }
}

// MARK: - ASWebAuthenticationSession wrapper

@MainActor
private final class WebAuthSession: NSObject, ASWebAuthenticationPresentationContextProviding {
    private var session: ASWebAuthenticationSession?

    static func start(url: URL, callbackScheme: String) async throws -> URL {
        let holder = WebAuthSession()
        return try await holder.run(url: url, callbackScheme: callbackScheme)
    }

    private func run(url: URL, callbackScheme: String) async throws -> URL {
        try await withCheckedThrowingContinuation { continuation in
            var resumed = false
            let finish: (Result<URL, Error>) -> Void = { result in
                guard !resumed else { return }
                resumed = true
                self.session = nil
                continuation.resume(with: result)
            }

            let authSession = ASWebAuthenticationSession(
                url: url,
                callbackURLScheme: callbackScheme
            ) { callbackURL, error in
                if let error {
                    let ns = error as NSError
                    if ns.domain == ASWebAuthenticationSessionErrorDomain,
                       ns.code == ASWebAuthenticationSessionError.canceledLogin.rawValue
                    {
                        finish(.failure(StorefrontError.message("Google sign-in cancelled")))
                    } else {
                        finish(.failure(StorefrontError.message(error.localizedDescription)))
                    }
                    return
                }
                guard let callbackURL else {
                    finish(.failure(StorefrontError.message("Google OAuth returned no URL.")))
                    return
                }
                finish(.success(callbackURL))
            }
            authSession.prefersEphemeralWebBrowserSession = true
            authSession.presentationContextProvider = self
            self.session = authSession
            guard authSession.start() else {
                finish(.failure(StorefrontError.message("Could not start Google sign-in browser.")))
                return
            }
        }
    }

    func presentationAnchor(for session: ASWebAuthenticationSession) -> ASPresentationAnchor {
        #if canImport(UIKit)
        let scenes = UIApplication.shared.connectedScenes.compactMap { $0 as? UIWindowScene }
        if let key = scenes.flatMap(\.windows).first(where: \.isKeyWindow) {
            return key
        }
        if let any = scenes.flatMap(\.windows).first {
            return any
        }
        return ASPresentationAnchor()
        #else
        return ASPresentationAnchor()
        #endif
    }
}
