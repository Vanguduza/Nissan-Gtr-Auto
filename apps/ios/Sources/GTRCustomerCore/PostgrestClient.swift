import Foundation

/// Minimal PostgREST / Edge HTTP client for storefront AuthZ.
///
/// Uses `URLSession` so the scaffold builds without resolving supabase-swift on Windows.
/// Headers match Supabase JS: `apikey` + `Authorization: Bearer {anon|session}`.
/// No PSP secrets or HMAC — payment initiate is RPC / edge only.
public final class PostgrestClient: @unchecked Sendable {
    public let baseURL: URL
    public let anonKey: String
    /// Bearer used for `Authorization`. Defaults to anon; set a customer JWT for AuthZ RPCs.
    public var accessToken: String

    private let session: URLSession
    private let decoder: JSONDecoder

    public init(
        supabaseURL: String,
        anonKey: String,
        accessToken: String? = nil,
        session: URLSession = .shared
    ) throws {
        let trimmed = supabaseURL.trimmingCharacters(in: .whitespacesAndNewlines)
            .trimmingCharacters(in: CharacterSet(charactersIn: "/"))
        guard let url = URL(string: trimmed), !anonKey.isEmpty else {
            throw StorefrontError.notConfigured
        }
        self.baseURL = url
        self.anonKey = anonKey
        self.accessToken = (accessToken?.isEmpty == false) ? accessToken! : anonKey
        self.session = session
        self.decoder = JSONDecoder()
        self.decoder.dateDecodingStrategy = .custom { decoder in
            let container = try decoder.singleValueContainer()
            let raw = try container.decode(String.self)
            if let d = ISO8601DateFormatter().date(from: raw) { return d }
            let f = ISO8601DateFormatter()
            f.formatOptions = [.withInternetDateTime, .withFractionalSeconds]
            if let d = f.date(from: raw) { return d }
            throw DecodingError.dataCorruptedError(
                in: container,
                debugDescription: "Invalid date: \(raw)"
            )
        }
    }

    // MARK: - RPC

    /// `POST {url}/rest/v1/rpc/{name}` with JSON body.
    public func rpc(_ name: String, body: [String: Any] = [:]) async throws -> Data {
        let url = baseURL.appendingPathComponent("rest/v1/rpc/\(name)")
        var request = URLRequest(url: url)
        request.httpMethod = "POST"
        applyAuthHeaders(&request)
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        request.setValue("application/json", forHTTPHeaderField: "Accept")
        request.httpBody = try JSONSerialization.data(withJSONObject: body)
        return try await perform(request)
    }

    public func rpcDecode<T: Decodable>(_ name: String, body: [String: Any] = [:]) async throws -> T {
        let data = try await rpc(name, body: body)
        if data.isEmpty || data == Data("null".utf8) {
            throw StorefrontError.message("RPC \(name) returned empty.")
        }
        do {
            return try decoder.decode(T.self, from: data)
        } catch {
            throw StorefrontError.message("RPC \(name) decode failed: \(error.localizedDescription)")
        }
    }

    /// Like `rpcDecode` but treats empty / null / `[]` as an empty array (inactive track, etc.).
    public func rpcDecodeArrayAllowEmpty<T: Decodable>(
        _ name: String,
        body: [String: Any] = [:]
    ) async throws -> [T] {
        let data = try await rpc(name, body: body)
        if data.isEmpty || data == Data("null".utf8) || data == Data("[]".utf8) {
            return []
        }
        do {
            return try decoder.decode([T].self, from: data)
        } catch {
            throw StorefrontError.message("RPC \(name) decode failed: \(error.localizedDescription)")
        }
    }

    public func rpcUUID(_ name: String, body: [String: Any] = [:]) async throws -> UUID {
        let raw: String = try await rpcDecode(name, body: body)
        guard let id = UUID(uuidString: raw) else {
            throw StorefrontError.message("RPC \(name) returned invalid UUID.")
        }
        return id
    }

    // MARK: - Table select

    /// `GET {url}/rest/v1/{table}?{query}`
    public func select(
        table: String,
        query: String,
        prefer: String? = "return=representation"
    ) async throws -> Data {
        var components = URLComponents(
            url: baseURL.appendingPathComponent("rest/v1/\(table)"),
            resolvingAgainstBaseURL: false
        )!
        components.percentEncodedQuery = query
        guard let url = components.url else {
            throw StorefrontError.message("Invalid PostgREST query.")
        }
        var request = URLRequest(url: url)
        request.httpMethod = "GET"
        applyAuthHeaders(&request)
        request.setValue("application/json", forHTTPHeaderField: "Accept")
        if let prefer {
            request.setValue(prefer, forHTTPHeaderField: "Prefer")
        }
        return try await perform(request)
    }

    public func selectDecode<T: Decodable>(
        table: String,
        query: String
    ) async throws -> T {
        let data = try await select(table: table, query: query)
        do {
            return try decoder.decode(T.self, from: data)
        } catch {
            throw StorefrontError.message("Select \(table) decode failed: \(error.localizedDescription)")
        }
    }

    // MARK: - Edge functions

    /// `POST {url}/functions/v1/{name}` — preferred for pay-initiate when edge returns checkout_url.
    public func invokeFunction(_ name: String, body: [String: Any]) async throws -> Data {
        let url = baseURL.appendingPathComponent("functions/v1/\(name)")
        var request = URLRequest(url: url)
        request.httpMethod = "POST"
        applyAuthHeaders(&request)
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        request.setValue("application/json", forHTTPHeaderField: "Accept")
        request.httpBody = try JSONSerialization.data(withJSONObject: body)
        return try await perform(request, allowNon2xxBody: true)
    }

    // MARK: - Internals

    private func applyAuthHeaders(_ request: inout URLRequest) {
        request.setValue(anonKey, forHTTPHeaderField: "apikey")
        request.setValue("Bearer \(accessToken)", forHTTPHeaderField: "Authorization")
    }

    private func perform(
        _ request: URLRequest,
        allowNon2xxBody: Bool = false
    ) async throws -> Data {
        let (data, response) = try await session.data(for: request)
        guard let http = response as? HTTPURLResponse else {
            throw StorefrontError.message("Invalid HTTP response.")
        }
        if (200 ..< 300).contains(http.statusCode) {
            return data
        }
        if allowNon2xxBody {
            return data
        }
        let message = Self.postgrestErrorMessage(data: data, status: http.statusCode)
        if http.statusCode == 401 || http.statusCode == 403 {
            throw StorefrontError.notAuthenticated
        }
        throw StorefrontError.message(message)
    }

    private static func postgrestErrorMessage(data: Data, status: Int) -> String {
        if let obj = try? JSONSerialization.jsonObject(with: data) as? [String: Any] {
            if let msg = obj["message"] as? String, !msg.isEmpty { return msg }
            if let msg = obj["error"] as? String, !msg.isEmpty { return msg }
            if let msg = obj["msg"] as? String, !msg.isEmpty { return msg }
        }
        let raw = String(data: data, encoding: .utf8)?.trimmingCharacters(in: .whitespacesAndNewlines)
        if let raw, !raw.isEmpty { return "HTTP \(status): \(raw)" }
        return "HTTP \(status)"
    }
}

// MARK: - JSON helpers

enum JSONValue {
    static func number(_ value: Decimal) -> Any {
        NSDecimalNumber(decimal: value)
    }

    static func uuid(_ value: UUID) -> String {
        value.uuidString.lowercased()
    }
}

/// Decodes Postgres `numeric` as `Decimal` from JSON number or string.
struct FlexibleDecimal: Decodable, Sendable {
    let value: Decimal

    init(_ value: Decimal) {
        self.value = value
    }

    init(from decoder: Decoder) throws {
        let container = try decoder.singleValueContainer()
        if let d = try? container.decode(Double.self) {
            value = Decimal(d)
            return
        }
        if let i = try? container.decode(Int64.self) {
            value = Decimal(i)
            return
        }
        if let s = try? container.decode(String.self), let d = Decimal(string: s) {
            value = d
            return
        }
        throw DecodingError.dataCorruptedError(
            in: container,
            debugDescription: "Expected numeric"
        )
    }
}
