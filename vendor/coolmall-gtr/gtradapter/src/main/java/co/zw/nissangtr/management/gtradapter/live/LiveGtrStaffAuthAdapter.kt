package co.zw.nissangtr.management.gtradapter.live

import co.zw.nissangtr.management.gtradapter.GtrStaffAuthAdapter
import co.zw.nissangtr.management.gtradapter.GtrStaffContext
import co.zw.nissangtr.management.gtradapter.GtrStaffSession
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/**
 * Live staff auth — web `signInWithStaffIdentifier` + `loadStaffContext`.
 * Non-enumerating errors on resolve/sign-in failure.
 */
class LiveGtrStaffAuthAdapter(
    private val client: SupabaseClient,
    private val session: GtrStaffSession,
) : GtrStaffAuthAdapter {

    override suspend fun resolveLoginEmail(identifier: String): String? {
        val id = identifier.trim()
        if (id.isEmpty()) return null
        return runCatching {
            client.postgrest.rpc(
                "resolve_staff_login_email",
                buildJsonObject { put("p_identifier", id) },
            ).decodeAs<String>()
        }.getOrNull()?.trim()?.takeIf { it.isNotEmpty() }
    }

    override suspend fun signInWithPassword(email: String, password: String): Result<Unit> {
        if (email.isBlank() || password.isBlank()) {
            return Result.failure(IllegalArgumentException("Sign-in failed"))
        }
        return runCatching {
            client.auth.signInWith(Email) {
                this.email = email.trim()
                this.password = password
            }
        }.fold(
            onSuccess = { Result.success(Unit) },
            onFailure = { Result.failure(IllegalArgumentException("Sign-in failed")) },
        )
    }

    override suspend fun signInWithStaffIdentifier(
        identifier: String,
        password: String,
    ): Result<Unit> {
        val email = resolveLoginEmail(identifier)
            ?: return Result.failure(IllegalArgumentException("Sign-in failed"))
        val signed = signInWithPassword(email, password)
        if (signed.isFailure) return signed
        val ctx = loadStaffContext().getOrElse {
            return Result.failure(IllegalArgumentException("Sign-in failed"))
        }
        if (!ctx.isStaff) {
            runCatching { client.auth.signOut() }
            session.clear()
            return Result.failure(IllegalArgumentException("Sign-in failed"))
        }
        return Result.success(Unit)
    }

    override suspend fun loadStaffContext(): Result<GtrStaffContext> {
        val userId = client.auth.currentSessionOrNull()?.user?.id
            ?: return Result.failure(IllegalStateException("Not signed in"))

        val profile = runCatching {
            client.from("profiles")
                .select(Columns.list("is_staff", "must_change_password")) {
                    filter { eq("id", userId) }
                    limit(1)
                }
                .decodeList<ProfileRow>()
                .firstOrNull()
                ?: ProfileRow()
        }.recoverCatching {
            client.from("profiles")
                .select(Columns.list("is_staff")) {
                    filter { eq("id", userId) }
                    limit(1)
                }
                .decodeList<ProfileStaffOnlyRow>()
                .firstOrNull()
                ?.let { ProfileRow(isStaff = it.isStaff, mustChangePassword = false) }
                ?: ProfileRow()
        }.getOrElse { return Result.failure(it) }

        val roles = runCatching {
            client.from("staff_roles")
                .select(Columns.list("role")) {
                    filter { eq("user_id", userId) }
                }
                .decodeList<StaffRoleRow>()
                .map { it.role }
        }.getOrElse { return Result.failure(it) }

        val moduleAccess = runCatching {
            client.postgrest.rpc("my_module_access").decodeAs<JsonArray>().mapNotNull {
                it.jsonPrimitive.contentOrNull?.trim()?.takeIf { s -> s.isNotEmpty() }
            }
        }.getOrNull()

        val ctx = GtrStaffContext(
            userId = userId,
            isStaff = profile.isStaff,
            roles = roles,
            moduleAccess = moduleAccess?.takeIf { it.isNotEmpty() },
            mustChangePassword = profile.mustChangePassword,
        )
        session.update(ctx)
        return Result.success(ctx)
    }

    override suspend fun signOut() {
        runCatching { client.auth.signOut() }
        session.clear()
    }

    override suspend fun reauthWithPassword(password: String): Result<Unit> {
        val email = client.auth.currentSessionOrNull()?.user?.email
            ?: return Result.failure(IllegalStateException("Not signed in"))
        return signInWithPassword(email, password)
    }

    override fun isFakeMode(): Boolean = false
}

@Serializable
private data class ProfileRow(
    @SerialName("is_staff") val isStaff: Boolean = false,
    @SerialName("must_change_password") val mustChangePassword: Boolean = false,
)

@Serializable
private data class ProfileStaffOnlyRow(
    @SerialName("is_staff") val isStaff: Boolean = false,
)

@Serializable
private data class StaffRoleRow(
    val role: String,
)
