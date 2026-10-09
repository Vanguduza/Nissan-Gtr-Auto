package co.zw.nissangtr.management.rpc

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.functions.functions
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

data class AuthEdgeSession(
    val userId: String,
    val accessToken: String,
    val refreshToken: String,
    val expiresIn: Long,
    val email: String?,
)

object AuthEdgeClient {
    private val json = Json { ignoreUnknownKeys = true }

    private suspend fun invoke(client: SupabaseClient, function: String, body: JsonObject): JsonObject {
        val response = client.functions.invoke(function) { setBody(body) }
        val obj = json.parseToJsonElement(response.bodyAsText()) as? JsonObject
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
        email: String,
        password: String,
        deviceId: String? = null,
    ): AuthEdgeSession {
        val obj = invoke(client, "auth-otp", buildJsonObject {
            put("action", "complete_login")
            put("email", email.trim())
            put("password", password)
            deviceId?.trim()?.takeIf { it.isNotEmpty() }?.let { put("device_id", it) }
        })
        return parseSession(obj)
    }

    suspend fun requestPasswordReset(
        client: SupabaseClient,
        email: String,
        deviceId: String? = null,
    ) {
        invoke(client, "request-password-reset", buildJsonObject {
            put("channel", "email")
            put("email", email.trim())
            deviceId?.trim()?.takeIf { it.isNotEmpty() }?.let { put("device_id", it) }
        })
    }

    suspend fun verifyPasswordReset(
        client: SupabaseClient,
        email: String,
        code: String,
        newPassword: String,
        deviceId: String? = null,
    ): AuthEdgeSession {
        val obj = invoke(client, "verify-password-reset", buildJsonObject {
            put("channel", "email")
            put("email", email.trim())
            put("code", code.trim())
            put("new_password", newPassword)
            deviceId?.trim()?.takeIf { it.isNotEmpty() }?.let { put("device_id", it) }
        })
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
        )
    }
}
