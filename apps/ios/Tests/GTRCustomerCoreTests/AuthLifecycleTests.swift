import XCTest
import Foundation
#if canImport(FoundationNetworking)
import FoundationNetworking
#endif
#if canImport(Security)
import Security
#endif
#if SWIFT_PACKAGE
@testable import GTRCustomerCore
#endif

final class MemoryCredentials: AuthCredentialStorage, @unchecked Sendable {
    var data: Data?
    var failWrite = false
    var failDelete = false
    var reads = 0
    func read() throws -> Data? { reads += 1; return data }
    func write(_ data: Data) throws {
        if failWrite { throw AuthStorageError.unavailable(-1) }
        self.data = data
    }
    func delete() throws {
        if failDelete { throw AuthStorageError.unavailable(-1) }
        data = nil
    }
}

final class MemorySessions: AuthSessionPersistence {
    var record: AuthTokenRecord?
    var saved = 0
    var failSave = false
    init(_ record: AuthTokenRecord? = nil) { self.record = record }
    func load() throws -> AuthTokenRecord? { record }
    func save(_ record: AuthTokenRecord) throws {
        if failSave { throw AuthStorageError.unavailable(-1) }
        saved += 1
        self.record = record
    }
    func clear() throws { record = nil }
}

actor RefreshGate {
    var calls = 0
    private var continuation: CheckedContinuation<GoTrueAuthClient.Session, Never>?
    private var startWaiters: [CheckedContinuation<Void, Never>] = []
    func refresh(_ token: String) async -> GoTrueAuthClient.Session {
        calls += 1
        startWaiters.forEach { $0.resume() }
        startWaiters = []
        return await withCheckedContinuation { continuation = $0 }
    }
    func waitUntilStarted() async {
        if calls > 0 { return }
        await withCheckedContinuation { startWaiters.append($0) }
    }
    func finish() {
        continuation?.resume(returning: .init(accessToken: "rotated-access", refreshToken: "rotated-refresh", email: "customer@example.test", expiresIn: 3600))
        continuation = nil
    }
}

final class AuthLifecycleTests: XCTestCase {
    private func defaults() -> UserDefaults {
        UserDefaults(suiteName: "gtr-auth-test-" + UUID().uuidString)!
    }

    func testLegacyCredentialsMigrateOnlyAfterSecureWriteSucceeds() throws {
        let defaults = defaults()
        let storage = MemoryCredentials()
        defaults.set("legacy-access", forKey: "gtr.customer.auth.access_token")
        defaults.set("legacy-refresh", forKey: "gtr.customer.auth.refresh_token")
        let store = SecureAuthSessionPersistence(storage: storage, defaults: defaults)
        storage.failWrite = true
        XCTAssertThrowsError(try store.load())
        XCTAssertEqual(defaults.string(forKey: "gtr.customer.auth.refresh_token"), "legacy-refresh")
        storage.failWrite = false
        let migrated = try store.load()
        XCTAssertEqual(migrated?.refreshToken, "legacy-refresh")
        XCTAssertNil(defaults.string(forKey: "gtr.customer.auth.access_token"))
        XCTAssertNil(defaults.string(forKey: "gtr.customer.auth.refresh_token"))
        XCTAssertEqual(try store.load(), migrated)
        try store.clear()
        XCTAssertNil(try store.load())
    }

    func testFailedKeychainDeletionCannotRestoreSignedOutSession() throws {
        let defaults = defaults()
        let storage = MemoryCredentials()
        let store = SecureAuthSessionPersistence(storage: storage, defaults: defaults)
        try store.save(.init(accessToken: "saved", refreshToken: "refresh"))
        storage.failDelete = true
        XCTAssertThrowsError(try store.clear())
        XCTAssertThrowsError(try store.load())
        XCTAssertEqual(storage.reads, 0, "A pending logout must not read old credentials")
        storage.failDelete = false
        XCTAssertNil(try store.load())
        XCTAssertNil(storage.data)
    }

    func testExpiryIsConservativeAndCanReadJWTExpiryHint() throws {
        let exp: TimeInterval = 1_900_000_000
        let payload = try JSONSerialization.data(withJSONObject: ["exp": exp]).base64EncodedString()
        let record = AuthTokenRecord(accessToken: "header.\(payload).signature")
        XCTAssertFalse(record.needsRefresh(at: Date(timeIntervalSince1970: exp - 120)))
        XCTAssertTrue(record.needsRefresh(at: Date(timeIntervalSince1970: exp - 30)))
        XCTAssertTrue(AuthTokenRecord(accessToken: "unknown").needsRefresh())
    }

    @MainActor
    func testConcurrentExpiredRequestsRotateOnlyOnceAndPersistBeforePublishing() async throws {
        let gate = RefreshGate()
        let store = MemorySessions(.init(accessToken: "old", refreshToken: "refresh", expiresAt: .distantPast))
        let coordinator = try AuthSessionCoordinator(persistence: store) { await gate.refresh($0) }
        var published = 0
        coordinator.onChange = { record in
            XCTAssertEqual(store.record, record)
            published += 1
        }
        let first = Task { try await coordinator.accessToken() }
        let second = Task { try await coordinator.accessToken() }
        await gate.waitUntilStarted()
        await Task.yield()
        let count = await gate.calls
        XCTAssertEqual(count, 1)
        await gate.finish()
        let a = try await first.value
        let b = try await second.value
        XCTAssertEqual(a, "rotated-access")
        XCTAssertEqual(b, "rotated-access")
        XCTAssertEqual(store.saved, 1)
        XCTAssertEqual(published, 1)
        XCTAssertEqual(store.record?.refreshToken, "rotated-refresh")
        XCTAssertNotNil(store.record?.expiresAt)
        // A delayed 401 for the previous token must not rotate the new token again.
        let current = try await coordinator.accessToken(rejectedAccessToken: "old")
        XCTAssertEqual(current, "rotated-access")
        let countAfter = await gate.calls
        XCTAssertEqual(countAfter, 1)
    }

    @MainActor
    func testSignOutDuringRefreshCannotResurrectCredentials() async throws {
        let gate = RefreshGate()
        let store = MemorySessions(.init(accessToken: "old", refreshToken: "refresh", expiresAt: .distantPast))
        let coordinator = try AuthSessionCoordinator(persistence: store) { await gate.refresh($0) }
        let task = Task { try await coordinator.accessToken() }
        await gate.waitUntilStarted()
        try coordinator.signOut()
        await gate.finish()
        do { _ = try await task.value; XCTFail("Signed-out refresh must be discarded") }
        catch { XCTAssertEqual(error as? AuthSessionError, .sessionChanged) }
        XCTAssertNil(coordinator.currentRecord)
        XCTAssertNil(store.record)
        XCTAssertEqual(store.saved, 0)
    }

    @MainActor
    func testRejectedRefreshClearsSessionButTransientNetworkErrorRetainsRetryRecord() async throws {
        let expired = AuthTokenRecord(accessToken: "old", refreshToken: "refresh", expiresAt: .distantPast)
        let rejectedStore = MemorySessions(expired)
        let rejected = try AuthSessionCoordinator(persistence: rejectedStore) { _ in throw AuthSessionError.refreshRejected }
        do { _ = try await rejected.accessToken(); XCTFail() } catch {}
        XCTAssertNil(rejected.currentRecord)
        XCTAssertNil(rejectedStore.record)

        let offlineStore = MemorySessions(expired)
        let offline = try AuthSessionCoordinator(persistence: offlineStore) { _ in throw URLError(.notConnectedToInternet) }
        do { _ = try await offline.accessToken(); XCTFail() } catch {}
        XCTAssertEqual(offline.currentRecord, expired)
        XCTAssertTrue(offline.currentRecord!.needsRefresh())
    }

    @MainActor
    func testFailedRotationPersistenceDoesNotPublishNewCredentials() async throws {
        let store = MemorySessions(.init(accessToken: "old", refreshToken: "refresh", expiresAt: .distantPast))
        store.failSave = true
        let coordinator = try AuthSessionCoordinator(persistence: store) { _ in
            .init(accessToken: "new", refreshToken: "rotated", email: nil, expiresIn: 3600)
        }
        do { _ = try await coordinator.accessToken(); XCTFail() } catch {}
        XCTAssertNil(coordinator.currentRecord)
        XCTAssertEqual(store.saved, 0)
    }

    func testReleaseConfigurationRejectsFakeRetiredInsecureAndServerCredentials() throws {
        let url = "https://" + AppEnv.productionSupabaseHost
        let publicKey = "sb_publishable_unit_test_fixture_0000000000"
        XCTAssertNoThrow(try AppEnv.validateReleaseConfiguration(url: url, anonKey: publicKey, forceFake: false))
        for invalid in ["http://" + AppEnv.productionSupabaseHost, "https://gylrgwqyuiwkyykardwc.supabase.co", url + "/unexpected", url + "?override=1"] {
            XCTAssertThrowsError(try AppEnv.validateReleaseConfiguration(url: invalid, anonKey: publicKey, forceFake: false))
        }
        XCTAssertThrowsError(try AppEnv.validateReleaseConfiguration(url: url, anonKey: publicKey, forceFake: true))
        XCTAssertThrowsError(try AppEnv.validateReleaseConfiguration(url: url, anonKey: "", forceFake: false))
        XCTAssertThrowsError(try AppEnv.validateReleaseConfiguration(url: url, anonKey: "sb_secret_fixture", forceFake: false))
        for malformed in ["sb_publishable_x", "sb_publishable_", "sb_publishable_aaaaaaaaaaaaaaaaaaaa!", "sb_publishable_aaaaaaaaaaaaaaaaaaaa a"] {
            XCTAssertThrowsError(try AppEnv.validateReleaseConfiguration(url: url, anonKey: malformed, forceFake: false))
        }
        let serverPayload = try JSONSerialization.data(withJSONObject: ["role": "service_role"]).base64EncodedString()
        XCTAssertThrowsError(try AppEnv.validateReleaseConfiguration(url: url, anonKey: "header.\(serverPayload).signature", forceFake: false))
        let invalidClaims: [[String: Any]] = [
            ["role": "anon", "ref": "gylrgwqyuiwkyykardwc", "exp": Date().timeIntervalSince1970 + 3600],
            ["role": "anon", "ref": "bicyjghgdnzlnjqxzoud", "exp": 1],
            ["role": "anon", "ref": "bicyjghgdnzlnjqxzoud"],
        ]
        for claims in invalidClaims {
            let payload = try JSONSerialization.data(withJSONObject: claims).base64EncodedString()
            XCTAssertThrowsError(try AppEnv.validateReleaseConfiguration(url: url, anonKey: "header.\(payload).signature", forceFake: false))
        }
        let validClaims: [String: Any] = ["role": "anon", "ref": "bicyjghgdnzlnjqxzoud", "exp": Date().timeIntervalSince1970 + 3600]
        let validPayload = try JSONSerialization.data(withJSONObject: validClaims).base64EncodedString()
        XCTAssertNoThrow(try AppEnv.validateReleaseConfiguration(url: url, anonKey: "header.\(validPayload).signature", forceFake: false))
    }

    func testCustomerPresentationHidesTechnicalIdentityAndRawFitment() throws {
        XCTAssertEqual(CustomerCatalogPresentation.productName("15208-65F0C", identifier: "15208-65F0C"), "Spare part")
        XCTAssertEqual(CustomerCatalogPresentation.productName("15208-65F0C · Oil filter", identifier: "15208-65F0C"), "Oil filter")
        XCTAssertEqual(CustomerCatalogPresentation.fitmentMessage(lines: ["T31 · MR20"], generation: "T31", engine: "MR20"), "Fits your selected vehicle.")
        XCTAssertEqual(CustomerCatalogPresentation.fitmentMessage(lines: ["T310 · MR200"], generation: "T31", engine: "MR20"), "Fitment is not yet confirmed for your vehicle.")
        let product = CatalogProduct(stockItemId: UUID(), baseUomId: UUID(), oem: "secret-part", name: "Oil filter", usd: 5, stock: .inStock,
            fitmentLines: ["raw-fitment"], specs: ["PNC-123"], replaces: ["cross-reference"])
        XCTAssertFalse(product.descriptionText.contains("PNC-123"))
        XCTAssertFalse(product.descriptionText.contains("cross-reference"))
    }

    @MainActor
    func testProductSearchAndOpaqueProductNavigationRemainFunctionalInPreview() async throws {
        let api = FakeStorefrontApi()
        let products = try await api.listCatalogBrowse(category: nil, limit: 100).items
        guard let product = products.first else { return XCTFail("Preview product fixtures required") }
        let loaded = try await api.loadCatalogProduct(oem: product.stockItemId.uuidString)
        XCTAssertEqual(loaded.stockItemId, product.stockItemId)
        let matches = try await api.searchProducts(query: String(product.name.prefix(5)), limit: 20)
        XCTAssertTrue(matches.contains { $0.stockItemId == product.stockItemId })
    }

    #if canImport(Security)
    func testNativeKeychainRoundTripUsesDeviceOnlyUnlockedProtection() throws {
        let service = "co.zw.nissangtr.auth.test." + UUID().uuidString
        let storage = KeychainAuthCredentialStorage(service: service)
        defer { try? storage.delete() }
        try storage.write(Data("synthetic-test-credential".utf8))
        XCTAssertEqual(try storage.read(), Data("synthetic-test-credential".utf8))
        var result: CFTypeRef?
        let status = SecItemCopyMatching([
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecReturnAttributes as String: true,
        ] as CFDictionary, &result)
        XCTAssertEqual(status, errSecSuccess)
        let attributes = result as? [String: Any]
        #if os(iOS)
        XCTAssertEqual(attributes?[kSecAttrAccessible as String] as? String, kSecAttrAccessibleWhenUnlockedThisDeviceOnly as String)
        #endif
        try storage.delete()
        XCTAssertNil(try storage.read())
    }
    #endif
}
