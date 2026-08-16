package co.zw.nissangtr.management.gtradapter.live

import co.zw.nissangtr.management.gtradapter.GtrPasswordAdapter
import co.zw.nissangtr.management.gtradapter.GtrStaffSession
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest

/**
 * Live change-password — web `/staff/change-password`:
 * GoTrue `updateUser` then RPC `clear_must_change_password`.
 */
class LiveGtrPasswordAdapter(
    private val client: SupabaseClient,
    private val session: GtrStaffSession,
) : GtrPasswordAdapter {
    override suspend fun changePassword(newPassword: String): Result<Unit> {
        if (newPassword.length < 8) {
            return Result.failure(IllegalArgumentException("Password too short"))
        }
        if (client.auth.currentSessionOrNull() == null) {
            return Result.failure(IllegalStateException("Not signed in"))
        }
        return runCatching {
            client.auth.updateUser { password = newPassword }
            runCatching { client.postgrest.rpc("clear_must_change_password") }
            session.context?.let { session.update(it.copy(mustChangePassword = false)) }
        }
    }
}
