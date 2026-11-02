import Foundation

public enum AuthSessionError: Error, Equatable {
    case requiresSignIn
    case refreshRejected
    case sessionChanged
}

/// Owns refresh-token rotation and serializes it across concurrent RPCs.
@MainActor
public final class AuthSessionCoordinator {
    public private(set) var currentRecord: AuthTokenRecord?
    public var onChange: ((AuthTokenRecord?) -> Void)?

    private let persistence: any AuthSessionPersistence
    private let refresh: @Sendable (String) async throws -> GoTrueAuthClient.Session
    private let now: @Sendable () -> Date
    private var generation = 0
    private var inFlight: (id: UUID, task: Task<AuthTokenRecord, Error>)?

    public init(
        persistence: any AuthSessionPersistence = SecureAuthSessionPersistence(),
        now: @escaping @Sendable () -> Date = { Date() },
        refresh: @escaping @Sendable (String) async throws -> GoTrueAuthClient.Session
    ) throws {
        self.persistence = persistence
        self.refresh = refresh
        self.now = now
        self.currentRecord = try persistence.load()
    }

    public func install(_ session: GoTrueAuthClient.Session, emailOverride: String? = nil) throws {
        let record = makeRecord(session, fallbackRefresh: nil, fallbackEmail: emailOverride)
        try persistence.save(record)
        generation += 1
        inFlight?.task.cancel()
        inFlight = nil
        currentRecord = record
        onChange?(record)
    }

    /// A rejected token forces one refresh only if it is still the current token.
    /// Another request may already have replaced it while the caller was in flight.
    public func accessToken(rejectedAccessToken: String? = nil) async throws -> String? {
        guard let record = currentRecord else { return nil }
        let rejectedCurrent = rejectedAccessToken != nil && rejectedAccessToken == record.accessToken
        if !rejectedCurrent && !record.needsRefresh(at: now()) { return record.accessToken }
        if let inFlight { return try await inFlight.task.value.accessToken }
        guard let token = record.refreshToken?.trimmingCharacters(in: .whitespacesAndNewlines), !token.isEmpty else {
            try signOut()
            throw AuthSessionError.requiresSignIn
        }

        let expectedGeneration = generation
        let id = UUID()
        let task = Task<AuthTokenRecord, Error> { [weak self, refresh] in
            do {
                let session = try await refresh(token)
                guard let self, self.generation == expectedGeneration, !Task.isCancelled else {
                    throw AuthSessionError.sessionChanged
                }
                let updated = self.makeRecord(session, fallbackRefresh: token, fallbackEmail: record.email)
                do {
                    try self.persistence.save(updated)
                } catch {
                    // A rotated credential that could not be persisted must not become
                    // a seemingly healthy session backed by an invalid old refresh token.
                    self.currentRecord = nil
                    self.onChange?(nil)
                    throw error
                }
                self.currentRecord = updated
                self.onChange?(updated)
                return updated
            } catch {
                if let self, self.generation == expectedGeneration {
                    if error as? AuthSessionError == .refreshRejected {
                        try self.signOut()
                    } else {
                        // A temporary network error retains the refresh record for retry.
                        self.onChange?(self.currentRecord)
                    }
                }
                throw error
            }
        }
        inFlight = (id, task)
        defer { if inFlight?.id == id { inFlight = nil } }
        return try await task.value.accessToken
    }

    public func signOut() throws {
        generation += 1
        inFlight?.task.cancel()
        inFlight = nil
        currentRecord = nil
        onChange?(nil)
        try persistence.clear()
    }

    private func makeRecord(
        _ session: GoTrueAuthClient.Session,
        fallbackRefresh: String?,
        fallbackEmail: String?
    ) -> AuthTokenRecord {
        AuthTokenRecord(
            accessToken: session.accessToken,
            refreshToken: session.refreshToken ?? fallbackRefresh,
            email: session.email ?? fallbackEmail,
            expiresAt: session.expiresIn.map { now().addingTimeInterval(TimeInterval($0)) }
        )
    }
}
