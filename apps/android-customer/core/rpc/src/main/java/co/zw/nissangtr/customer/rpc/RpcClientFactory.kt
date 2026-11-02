package co.zw.nissangtr.customer.rpc

import java.net.URI
import java.util.Base64
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** Live transport in production; fake data is allowed only by an explicit debug caller. */
object RpcClientFactory {
    fun create(
        supabaseUrl: String,
        supabaseAnonKey: String,
        forceFake: Boolean = false,
        allowFake: Boolean = false,
    ): RpcClient {
        if (!isLive(supabaseUrl, supabaseAnonKey, forceFake, allowFake)) return FakeRpcClient()
        return SupabaseRpcClient.create(supabaseUrl.trim(), supabaseAnonKey.trim())
    }

    fun isLive(
        supabaseUrl: String,
        supabaseAnonKey: String,
        forceFake: Boolean = false,
        allowFake: Boolean = false,
    ): Boolean {
        // The library build type is authoritative; a caller cannot enable demo data in Release.
        val debugFakeAllowed = allowFake && BuildConfig.DEBUG
        if (!debugFakeAllowed) {
            require(!forceFake) { "Fake RPC transport is unavailable in production." }
            val url = runCatching { URI(supabaseUrl.trim()) }.getOrNull()
            require(url?.scheme == "https" && url.host == "bicyjghgdnzlnjqxzoud.supabase.co" && url.userInfo == null) {
                "Production requires the canonical HTTPS Supabase project."
            }
            val key = supabaseAnonKey.trim()
            require(isPublicKey(key)) {
                "A public Supabase publishable/anon key is required."
            }
        }
        return !forceFake && supabaseUrl.isNotBlank() && supabaseAnonKey.isNotBlank()
    }
    private fun isPublicKey(key: String): Boolean {
        if (Regex("sb_publishable_[A-Za-z0-9_-]{20,}").matches(key)) return true
        return runCatching {
            val parts = key.split('.')
            if (parts.size != 3 || parts[0].isEmpty() || parts[2].isEmpty()) return false
            val claims = Json.parseToJsonElement(String(Base64.getUrlDecoder().decode(parts[1]))).jsonObject
            val expiry = claims["exp"]?.jsonPrimitive
            val expiryValue = expiry?.takeUnless { it.isString }?.content?.toDoubleOrNull()
            val expiryValid = expiryValue != null && expiryValue.isFinite() && expiryValue > System.currentTimeMillis() / 1000.0
            claims["role"]?.jsonPrimitive?.content == "anon" &&
                claims["ref"]?.jsonPrimitive?.content == "bicyjghgdnzlnjqxzoud" && expiryValid
        }.getOrDefault(false)
    }

}
