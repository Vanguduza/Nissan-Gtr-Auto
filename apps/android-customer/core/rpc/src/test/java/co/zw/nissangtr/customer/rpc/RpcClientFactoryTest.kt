package co.zw.nissangtr.customer.rpc

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
import org.junit.Test

class RpcClientFactoryTest {
    private val url = "https://bicyjghgdnzlnjqxzoud.supabase.co"
    private val key = "sb_publishable_" + "a".repeat(32)

    @Test fun productionRejectsMissingConfiguration() {
        assertThrows(IllegalArgumentException::class.java) { RpcClientFactory.create("", "") }
        assertThrows(IllegalArgumentException::class.java) { RpcClientFactory.create(url, "") }
    }

    @Test fun productionRejectsForcedFakeWrongProjectAndSecretKeys() {
        assertThrows(IllegalArgumentException::class.java) { RpcClientFactory.create(url, key, forceFake = true) }
        assertThrows(IllegalArgumentException::class.java) { RpcClientFactory.create("https://other.supabase.co", key) }
        assertThrows(IllegalArgumentException::class.java) { RpcClientFactory.create(url, "sb_secret_fixture") }
        val servicePayload = java.util.Base64.getUrlEncoder().withoutPadding()
            .encodeToString("{\"role\":\"service_role\"}".toByteArray())
        assertThrows(IllegalArgumentException::class.java) { RpcClientFactory.create(url, "header.$servicePayload.signature") }
    }

    @Test fun canonicalProductionConfigurationSelectsLive() {
        assertTrue(RpcClientFactory.isLive(url, key))
    }

    @Test fun legacyAnonKeysMustMatchProjectAndRemainUnexpired() {
        fun token(ref: String, expiry: Long): String {
            val claims = "{\"role\":\"anon\",\"ref\":\"$ref\",\"exp\":$expiry}"
            val payload = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(claims.toByteArray())
            return "header.$payload.signature"
        }
        assertTrue(RpcClientFactory.isLive(url, token("bicyjghgdnzlnjqxzoud", 4102444800)))
        assertThrows(IllegalArgumentException::class.java) { RpcClientFactory.isLive(url, token("retired-project", 4102444800)) }
        assertThrows(IllegalArgumentException::class.java) { RpcClientFactory.isLive(url, token("bicyjghgdnzlnjqxzoud", 1)) }
    }

    @Test fun legacyExpiryMustBeNumericFiniteAndPresent() {
        for (expiry in listOf(null, "null", "true", "\"4102444800\"", "1e999", "\"1e999\"")) {
            val extra = expiry?.let { ",\"exp\":$it" }.orEmpty()
            val claims = "{\"role\":\"anon\",\"ref\":\"bicyjghgdnzlnjqxzoud\"$extra}"
            val payload = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(claims.toByteArray())
            assertThrows(IllegalArgumentException::class.java) { RpcClientFactory.isLive(url, "header.$payload.signature") }
        }
    }

    @Test fun explicitlyAllowedDebugDemoStillWorks() {
        if (BuildConfig.DEBUG) {
            assertTrue(RpcClientFactory.create("", "", allowFake = true) is FakeRpcClient)
            assertTrue(RpcClientFactory.create(url, key, forceFake = true, allowFake = true) is FakeRpcClient)
            assertFalse(RpcClientFactory.isLive("", "", allowFake = true))
        } else {
            assertThrows(IllegalArgumentException::class.java) { RpcClientFactory.create("", "", allowFake = true) }
            assertThrows(IllegalArgumentException::class.java) { RpcClientFactory.create(url, key, forceFake = true, allowFake = true) }
        }
    }
}
