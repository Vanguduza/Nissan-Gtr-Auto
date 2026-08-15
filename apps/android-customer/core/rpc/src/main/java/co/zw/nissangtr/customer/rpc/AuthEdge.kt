package co.zw.nissangtr.customer.rpc

import io.github.jan.supabase.functions.functions
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/**
 * Edge `auth-otp` + password-reset — mirrors apps/web/lib/auth-otp.ts and
 * auth-password-reset.ts. Public GoTrue email signup is blocked on hosted.
 */
object AuthEdge {
    private val json = Json { ignoreUnknownKeys = true }

    @Serializable
    data class SessionPayload(
        val ok: Boolean? = null,
        val error: String? = null,
        @SerialName("user_id") val userId: String? = null,
        @SerialName("access_token") val accessToken: String? = null,
        @SerialName("refresh_token") val refreshToken: String? = null,
        @SerialName("expires_in") val expiresIn: Long? = null,
        val email: String? = null,
        @SerialName("phone_e164") val phoneE164: String? = null,
        @SerialName("stub_code") val stubCode: String? = null,
        val stub: Boolean? = null,
        @SerialName("proof_token") val proofToken: String? = null,
        val verified: Boolean? = null,
    )

    data class OtpRequestResult(val stub: Boolean, val stubCode: String?)
    data class OtpVerifyResult(
        val proofToken: String,
        val email: String?,
        val phoneE164: String?,
    )
    data class SessionTokens(
        val accessToken: String,
        val refreshToken: String,
        val expiresIn: Long,
        val email: String?,
    )

    /** Normalize Zimbabwe-friendly phone to E.164 (+263…) when possible. */
    fun normalizeE164(raw: String?): String? {
        val t = raw?.trim().orEmpty()
        if (t.isEmpty()) return null
        val digits = t.filter { it.isDigit() || it == '+' }
        if (digits.startsWith("+") && digits.length >= 10) return digits
        val only = t.filter { it.isDigit() }
        when {
            only.startsWith("263") && only.length >= 12 -> return "+$only"
            only.startsWith("0") && only.length >= 9 -> return "+263${only.drop(1)}"
            only.length in 9..10 && !only.startsWith("0") -> return "+263$only"
        }
        return if (t.startsWith("+")) t else null
    }

    fun looksLikePhone(raw: String): Boolean {
        val t = raw.trim()
        if (t.contains("@")) return false
        return normalizeE164(t) != null || t.filter { it.isDigit() }.length >= 9
    }

    suspend fun requestAuthOtp(
        client: SupabaseRpcClient,
        email: String? = null,
        phoneE164: String? = null,
    ): OtpRequestResult {
        val em = email?.trim()?.lowercase()?.takeIf { it.isNotBlank() }
        val ph = normalizeE164(phoneE164)
        require(em != null || ph != null) { "Enter email and/or phone (E.164)." }
        val body = invoke(client, "auth-otp") {
            put("action", "request")
            if (em != null) put("email", em)
            if (ph != null) put("phone_e164", ph)
        }
        if (body.error != null) error(body.error)
        return OtpRequestResult(
            stub = body.stub == true,
            stubCode = body.stubCode?.takeIf { it.isNotBlank() },
        )
    }

    suspend fun verifyAuthOtp(
        client: SupabaseRpcClient,
        email: String? = null,
        phoneE164: String? = null,
        code: String,
    ): OtpVerifyResult {
        require(code.trim().matches(Regex("^\\d{6}$"))) { "Enter the 6-digit code." }
        val em = email?.trim()?.lowercase()?.takeIf { it.isNotBlank() }
        val ph = normalizeE164(phoneE164)
        require(em != null || ph != null) { "Enter email and/or phone (E.164)." }
        val body = invoke(client, "auth-otp") {
            put("action", "verify")
            put("code", code.trim())
            if (em != null) put("email", em)
            if (ph != null) put("phone_e164", ph)
        }
        if (body.error != null) error(body.error)
        if (body.verified != true) error(body.error ?: "OTP verification failed")
        val proof = body.proofToken?.trim().orEmpty()
        require(proof.isNotEmpty()) { "OTP verify did not return proof_token" }
        return OtpVerifyResult(
            proofToken = proof,
            email = body.email ?: em,
            phoneE164 = body.phoneE164 ?: ph,
        )
    }

    suspend fun completeSignup(
        client: SupabaseRpcClient,
        email: String,
        password: String,
        proofToken: String,
        fullName: String? = null,
        phoneE164: String? = null,
    ): SessionTokens {
        require(password.length >= 8) { "Password must be at least 8 characters" }
        require(proofToken.isNotBlank()) { "OTP proof missing — verify OTP again" }
        require(email.isNotBlank()) { "Email is required to create a storefront account" }
        val ph = normalizeE164(phoneE164)
        val body = invoke(client, "auth-otp") {
            put("action", "complete_signup")
            put("email", email.trim().lowercase())
            put("password", password)
            put("proof_token", proofToken.trim())
            if (!fullName.isNullOrBlank()) put("full_name", fullName.trim())
            if (ph != null) put("phone_e164", ph)
        }
        return requireSession(body, "Signup failed")
    }

    /**
     * Phone (or mixed) password login via Edge `complete_login`.
     * Email-only callers should use GoTrue [SupabaseRpcClient.signInWithEmail] instead.
     */
    suspend fun completeLogin(
        client: SupabaseRpcClient,
        email: String? = null,
        phoneE164: String? = null,
        password: String,
    ): SessionTokens {
        require(password.isNotBlank()) { "Password is required." }
        val em = email?.trim()?.lowercase()?.takeIf { it.isNotBlank() }
        val ph = normalizeE164(phoneE164)
        require(em != null || ph != null) { "Enter email and/or phone (E.164)." }
        val body = invoke(client, "auth-otp") {
            put("action", "complete_login")
            put("password", password)
            if (em != null) put("email", em)
            if (ph != null) put("phone_e164", ph)
        }
        return requireSession(body, "Login failed")
    }

    suspend fun requestPasswordReset(
        client: SupabaseRpcClient,
        email: String? = null,
        phoneE164: String? = null,
    ): OtpRequestResult {
        val em = email?.trim()?.lowercase()?.takeIf { it.isNotBlank() }
        val ph = normalizeE164(phoneE164)
        require(em != null || ph != null) { "Enter email and/or phone (E.164)." }
        val body = invoke(client, "request-password-reset") {
            if (em != null) put("email", em)
            if (ph != null) put("phone_e164", ph)
        }
        if (body.error != null) error(body.error)
        return OtpRequestResult(
            stub = body.stub == true,
            stubCode = body.stubCode?.takeIf { it.isNotBlank() },
        )
    }

    suspend fun verifyPasswordReset(
        client: SupabaseRpcClient,
        email: String? = null,
        phoneE164: String? = null,
        code: String,
        newPassword: String,
    ) {
        require(code.trim().matches(Regex("^\\d{6}$"))) { "Enter the 6-digit code." }
        require(newPassword.length >= 8) { "Password must be at least 8 characters" }
        val em = email?.trim()?.lowercase()?.takeIf { it.isNotBlank() }
        val ph = normalizeE164(phoneE164)
        require(em != null || ph != null) { "Enter email and/or phone (E.164)." }
        val body = invoke(client, "verify-password-reset") {
            put("code", code.trim())
            put("new_password", newPassword)
            if (em != null) put("email", em)
            if (ph != null) put("phone_e164", ph)
        }
        if (body.error != null) error(body.error)
        if (body.ok == false) error(body.error ?: "Could not reset password")
    }

    private fun requireSession(body: SessionPayload, fallback: String): SessionTokens {
        if (body.error != null) error(body.error)
        val access = body.accessToken?.trim().orEmpty()
        val refresh = body.refreshToken?.trim().orEmpty()
        require(access.isNotEmpty() && refresh.isNotEmpty()) {
            body.error ?: fallback
        }
        return SessionTokens(
            accessToken = access,
            refreshToken = refresh,
            expiresIn = body.expiresIn ?: 3600L,
            email = body.email,
        )
    }

    private suspend fun invoke(
        client: SupabaseRpcClient,
        functionName: String,
        bodyBuilder: JsonObjectBuilder.() -> Unit,
    ): SessionPayload {
        val response = client.client.functions.invoke(functionName) {
            setBody(buildJsonObject(bodyBuilder))
        }
        val text = response.bodyAsText()
        return try {
            json.decodeFromString(SessionPayload.serializer(), text)
        } catch (_: Exception) {
            val el = json.parseToJsonElement(text)
            val obj = el as? JsonObject
            val err = obj?.get("error")?.jsonPrimitive?.contentOrNull
                ?: obj?.get("message")?.jsonPrimitive?.contentOrNull
            SessionPayload(
                ok = obj?.get("ok")?.jsonPrimitive?.booleanOrNull,
                error = err ?: "Unexpected Edge response",
            )
        }
    }
}
