import XCTest
import Foundation
#if canImport(FoundationNetworking)
import FoundationNetworking
#endif
#if SWIFT_PACKAGE
@testable import GTRCustomerCore
#endif

final class AuthURLProtocol: URLProtocol, @unchecked Sendable {
    enum RequestBodyError: Error, Equatable { case missing, streamReadFailed }

    static var handler: ((URLRequest) throws -> (Int, Data))?

    /// Darwin URLSession may move a POST body into a stream before interception.
    /// Read the actual bytes in either representation so transport assertions
    /// exercise the same payload on macOS, iOS and FoundationNetworking hosts.
    static func bodyData(from request: URLRequest) throws -> Data {
        if let body = request.httpBody { return body }
        guard let stream = request.httpBodyStream else { throw RequestBodyError.missing }
        stream.open()
        defer { stream.close() }
        var body = Data()
        var buffer = [UInt8](repeating: 0, count: 1_024)
        while true {
            let count = stream.read(&buffer, maxLength: buffer.count)
            if count < 0 { throw stream.streamError ?? RequestBodyError.streamReadFailed }
            if count == 0 { return body }
            body.append(contentsOf: buffer.prefix(count))
        }
    }

    override class func canInit(with request: URLRequest) -> Bool { true }
    override class func canonicalRequest(for request: URLRequest) -> URLRequest { request }
    override func startLoading() {
        do {
            guard let handler = Self.handler else { throw URLError(.badServerResponse) }
            let (status, data) = try handler(request)
            let response = HTTPURLResponse(url: request.url!, statusCode: status, httpVersion: nil, headerFields: ["Content-Type": "application/json"])!
            client?.urlProtocol(self, didReceive: response, cacheStoragePolicy: .notAllowed)
            client?.urlProtocol(self, didLoad: data)
            client?.urlProtocolDidFinishLoading(self)
        } catch { client?.urlProtocol(self, didFailWithError: error) }
    }
    override func stopLoading() {}
}

final class AuthTransportTests: XCTestCase {
    private func session() -> URLSession {
        let config = URLSessionConfiguration.ephemeral
        config.protocolClasses = [AuthURLProtocol.self]
        return URLSession(configuration: config)
    }

    override func tearDown() { AuthURLProtocol.handler = nil; super.tearDown() }

    func testInterceptedRequestBodySupportsDataAndFoundationStreams() throws {
        let payload = try JSONSerialization.data(withJSONObject: ["refresh_token": String(repeating: "r", count: 4_096)])
        var dataRequest = URLRequest(url: URL(string: "https://example.test/auth/v1/token")!)
        dataRequest.httpBody = payload
        XCTAssertEqual(try AuthURLProtocol.bodyData(from: dataRequest), payload)

        let stream = InputStream(data: payload)
        var streamRequest = URLRequest(url: dataRequest.url!)
        streamRequest.httpBodyStream = stream
        XCTAssertNil(streamRequest.httpBody)
        XCTAssertEqual(try AuthURLProtocol.bodyData(from: streamRequest), payload)
        XCTAssertEqual(stream.streamStatus, .closed)
    }

    func testInterceptedRequestWithMissingBodyFailsExplicitly() {
        let request = URLRequest(url: URL(string: "https://example.test/auth/v1/token")!)
        XCTAssertThrowsError(try AuthURLProtocol.bodyData(from: request)) { error in
            XCTAssertEqual(error as? AuthURLProtocol.RequestBodyError, .missing)
        }
    }

    func testRefreshUsesNativeGrantAndReturnsRotatedSession() async throws {
        AuthURLProtocol.handler = { request in
            XCTAssertEqual(request.url?.path, "/auth/v1/token")
            XCTAssertEqual(URLComponents(url: request.url!, resolvingAgainstBaseURL: false)?.queryItems?.first?.value, "refresh_token")
            let payload = try AuthURLProtocol.bodyData(from: request)
            let body = try XCTUnwrap(try JSONSerialization.jsonObject(with: payload) as? [String: String])
            XCTAssertEqual(body["refresh_token"], "old-refresh")
            XCTAssertEqual(request.value(forHTTPHeaderField: "apikey"), "public-test-key")
            return (200, Data(#"{"access_token":"new-access","refresh_token":"new-refresh","expires_in":3600,"user":{"email":"customer@example.test"}}"#.utf8))
        }
        let client = try GoTrueAuthClient(supabaseURL: "https://example.test", anonKey: "public-test-key", session: session())
        let refreshed = try await client.refreshSession(refreshToken: "old-refresh")
        XCTAssertEqual(refreshed.refreshToken, "new-refresh")
        XCTAssertEqual(refreshed.expiresIn, 3600)
        XCTAssertEqual(refreshed.email, "customer@example.test")
    }

    func testRejectedNativeRefreshHasTypedRevocationError() async throws {
        AuthURLProtocol.handler = { _ in (400, Data(#"{"error":"invalid_grant"}"#.utf8)) }
        let client = try GoTrueAuthClient(supabaseURL: "https://example.test", anonKey: "public-test-key", session: session())
        do { _ = try await client.refreshSession(refreshToken: "revoked"); XCTFail() }
        catch { XCTAssertEqual(error as? AuthSessionError, .refreshRejected) }
    }

    @MainActor
    func testRPC401RetriesOnceUsingUpdatedBearer() async throws {
        var headers: [String?] = []
        AuthURLProtocol.handler = { request in
            headers.append(request.value(forHTTPHeaderField: "Authorization"))
            return headers.count == 1 ? (401, Data()) : (200, Data("[]".utf8))
        }
        let client = try PostgrestClient(supabaseURL: "https://example.test", anonKey: "anon", session: session())
        var refreshes = 0
        client.tokenProvider = { rejected in
            if rejected != nil { refreshes += 1; return "new-access" }
            return "old-access"
        }
        _ = try await client.rpc("protected_rpc")
        XCTAssertEqual(headers, ["Bearer old-access", "Bearer new-access"])
        XCTAssertEqual(refreshes, 1)
    }

    @MainActor
    func testOpaqueProductLinkResolvesStockRowAndUsesProductPhotography() async throws {
        let id = UUID()
        let uom = UUID()
        var stockLookup = false
        AuthURLProtocol.handler = { request in
            if request.url?.path == "/rest/v1/stock_items" {
                let query = URLComponents(url: request.url!, resolvingAgainstBaseURL: false)?.queryItems ?? []
                XCTAssertEqual(query.first { $0.name == "id" }?.value?.lowercased(), "eq." + id.uuidString.lowercased())
                XCTAssertNil(query.first { $0.name == "oem_part_number" })
                stockLookup = true
                return (200, try JSONSerialization.data(withJSONObject: [[
                    "id": id.uuidString, "oem_part_number": "INTERNAL-CODE", "description": "Oil filter",
                    "base_uom_id": uom.uuidString, "reorder_point": 0,
                ]]))
            }
            if request.url?.path == "/rest/v1/stock_item_images" {
                return (200, Data(#"[{"storage_path":"photos/filter.jpg"}]"#.utf8))
            }
            return (200, Data("[]".utf8))
        }
        let client = try PostgrestClient(supabaseURL: "https://example.test", anonKey: "anon", session: session())
        let api = LiveStorefrontApi(client: client)
        let product = try await api.loadCatalogProduct(oem: id.uuidString)
        XCTAssertTrue(stockLookup)
        XCTAssertEqual(product.stockItemId, id)
        XCTAssertEqual(product.oem, "INTERNAL-CODE")
        XCTAssertNil(product.diagramUrl)
        XCTAssertEqual(product.imageUrls, ["https://example.test/storage/v1/object/public/product-images/photos/filter.jpg"])
    }

    @MainActor
    func testStockIdentityRPCDoesNotSendOpaqueUUIDAsPartNumber() async throws {
        let id = UUID()
        AuthURLProtocol.handler = { request in
            XCTAssertEqual(request.url?.path, "/rest/v1/rpc/get_product_review_stats")
            let payload = try AuthURLProtocol.bodyData(from: request)
            let body = try XCTUnwrap(try JSONSerialization.jsonObject(with: payload) as? [String: Any])
            XCTAssertEqual(body["p_stock_item_id"] as? String, id.uuidString.lowercased())
            XCTAssertTrue(body["p_oem_part_number"] is NSNull)
            return (200, Data("[]".utf8))
        }
        let client = try PostgrestClient(supabaseURL: "https://example.test", anonKey: "anon", session: session())
        let api = LiveStorefrontApi(client: client)
        let result = try await api.getProductReviewStats(stockItemId: id, oem: id.uuidString)
        XCTAssertNil(result)
    }

    @MainActor
    func testRepeated401InvalidatesBut403DoesNotRefreshOrSignOut() async throws {
        let client = try PostgrestClient(supabaseURL: "https://example.test", anonKey: "anon", session: session())
        var refreshes = 0
        var failures = 0
        client.tokenProvider = { rejected in
            if rejected != nil { refreshes += 1 }
            return rejected == nil ? "old-access" : "new-access"
        }
        client.onAuthenticationFailure = { failures += 1 }
        AuthURLProtocol.handler = { _ in (403, Data(#"{"message":"role denied"}"#.utf8)) }
        do { _ = try await client.rpc("staff_only"); XCTFail() } catch {}
        XCTAssertEqual(refreshes, 0)
        XCTAssertEqual(failures, 0)

        var attempts = 0
        AuthURLProtocol.handler = { _ in attempts += 1; return (401, Data()) }
        do { _ = try await client.rpc("expired"); XCTFail() }
        catch { XCTAssertEqual(error as? StorefrontError, .notAuthenticated) }
        XCTAssertEqual(attempts, 2)
        XCTAssertEqual(refreshes, 1)
        XCTAssertEqual(failures, 1)
    }
}
