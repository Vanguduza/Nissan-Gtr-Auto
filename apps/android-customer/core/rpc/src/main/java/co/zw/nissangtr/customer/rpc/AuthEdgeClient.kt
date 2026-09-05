package co.zw.nissangtr.customer.rpc

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.functions.functions
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

internal data class AuthEdgeSession(
    val userId: String,
    val accessToken: String,
    val refreshToken: String,
    val expiresIn: Long,
    val email: String?,
    val phoneE164: String?,
)

internal data class AuthEdgeVerifyState(
    val emailVerified: Boolean,
    val phoneVerified: Boolean?,
    val signupReady: Boolean,
)

internal object AuthEdgeClient {
    private val json = Json { ignoreUnknownKeys = true }

    private suspend fun invoke(
        client: SupabaseClient,
        function: String,
        body: JsonObject,
    ): JsonObject {
        val response = client.functions.invoke(function) { setBody(body) }
        val text = response.bodyAsText()
        val obj = json.parseToJsonElement(text) as? JsonObject
            ?: error("Authentication service returned invalid JSON")
        val ok = obj["ok"]?.jsonPrimitive?.booleanOrNull
        if (ok == false || obj["error"] != null) {
            val message = obj["error"]?.jsonPrimitive?.contentOrNull
                ?: "Authentication request failed"
            val code = obj["code"]?.jsonPrimitive?.contentOrNull
            error(if (code.isNullOrBlank()) message else "$message [$code]")
        }
        return obj
    }

    suspend fun login(
        client: SupabaseClient,
        email: String? = null,
        phoneE164: String? = null,
        password: String,
        deviceId: String? = null,
    ): AuthEdgeSession {
        val obj = invoke(
            client,
            "auth-otp",
            buildJsonObject {
                put("action", "complete_login")
                put("password", password)
                email?.trim()?.takeIf { it.isNotEmpty() }?.let { put("email", it) }
                phoneE164?.trim()?.takeIf { it.isNotEmpty() }?.let { put("phone_e164", it) }
                deviceId?.trim()?.takeIf { it.isNotEmpty() }?.let { put("device_id", it) }
            },
        )
        return parseSession(obj)
    }

    suspend fun requestSignupOtp(
        client: SupabaseClient,
        email: String,
        phoneE164: String? = null,
        channel: String = "email",
        fullName: String? = null,
        deviceId: String? = null,
    ) {
        invoke(
            client,
            "auth-otp",
            buildJsonObject {
                put("action", "request")
                put("channel", channel)
                put("email", email.trim())
                phoneE164?.trim()?.takeIf { it.isNotEmpty() }?.let { put("phone_e164", it) }
                fullName?.trim()?.takeIf { it.isNotEmpty() }?.let { put("full_name", it) }
                deviceId?.trim()?.takeIf { it.isNotEmpty() }?.let { put("device_id", it) }
            },
        )
    }

    suspend fun verifySignupOtp(
        client: SupabaseClient,
        email: String,
        phoneE164: String? = null,
        channel: String = "email",
        code: String,
        deviceId: String? = null,
    ): AuthEdgeVerifyState {
        val obj = invoke(
            client,
            "auth-otp",
            buildJsonObject {
                put("action", "verify")
                put("channel", channel)
                put("code", code.trim())
                put("email", email.trim())
                phoneE164?.trim()?.takeIf { it.isNotEmpty() }?.let { put("phone_e164", it) }
                deviceId?.trim()?.takeIf { it.isNotEmpty() }?.let { put("device_id", it) }
            },
        )
        val verified = obj["verified"] as? JsonObject
        return AuthEdgeVerifyState(
            emailVerified = verified?.get("email")?.jsonPrimitive?.booleanOrNull == true,
            phoneVerified = verified?.get("phone")?.let {
                if (it is JsonPrimitive && it.isString.not() && it.content == "null") null
                else it.jsonPrimitive.booleanOrNull
            },
            signupReady = obj["signup_ready"]?.jsonPrimitive?.booleanOrNull == true,
        )
    }

    suspend fun completeSignup(
        client: SupabaseClient,
        email: String,
        password: String,
        phoneE164: String? = null,
        fullName: String? = null,
        deviceId: String? = null,
    ): AuthEdgeSession {
        val obj = invoke(
            client,
            "auth-otp",
            buildJsonObject {
                put("action", "complete_signup")
                put("email", email.trim())
                put("password", password)
                phoneE164?.trim()?.takeIf { it.isNotEmpty() }?.let { put("phone_e164", it) }
                fullName?.trim()?.takeIf { it.isNotEmpty() }?.let { put("full_name", it) }
                deviceId?.trim()?.takeIf { it.isNotEmpty() }?.let { put("device_id", it) }
            },
        )
        return parseSession(obj)
    }

    suspend fun requestPasswordReset(
        client: SupabaseClient,
        email: String,
        deviceId: String? = null,
    ) {
        invoke(
            client,
            "request-password-reset",
            buildJsonObject {
                put("channel", "email")
                put("email", email.trim())
                deviceId?.trim()?.takeIf { it.isNotEmpty() }?.let { put("device_id", it) }
            },
        )
    }

    suspend fun verifyPasswordReset(
        client: SupabaseClient,
        email: String,
        code: String,
        newPassword: String,
        deviceId: String? = null,
    ): AuthEdgeSession {
        val obj = invoke(
            client,
            "verify-password-reset",
            buildJsonObject {
                put("channel", "email")
                put("email", email.trim())
                put("code", code.trim())
                put("new_password", newPassword)
                deviceId?.trim()?.takeIf { it.isNotEmpty() }?.let { put("device_id", it) }
            },
        )
        return parseSession(obj)
    }

    private fun parseSession(obj: JsonObject): AuthEdgeSession {
        val userId = obj["user_id"]?.jsonPrimitive?.contentOrNull.orEmpty()
        val access = obj["access_token"]?.jsonPrimitive?.contentOrNull.orEmpty()
        val refresh = obj["refresh_token"]?.jsonPrimitive?.contentOrNull.orEmpty()
        if (userId.isBlank() || access.isBlank() || refresh.isBlank()) {
            error("Authentication service returned an incomplete session")
        }
        return AuthEdgeSession(
            userId = userId,
            accessToken = access,
            refreshToken = refresh,
            expiresIn = obj["expires_in"]?.jsonPrimitive?.intOrNull?.toLong() ?: 3600L,
            email = obj["email"]?.jsonPrimitive?.contentOrNull,
            phoneE164 = obj["phone_e164"]?.jsonPrimitive?.contentOrNull,
        )
    }
}
