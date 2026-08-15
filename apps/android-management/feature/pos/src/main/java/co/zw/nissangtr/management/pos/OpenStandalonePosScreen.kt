package co.zw.nissangtr.management.pos

import android.content.ActivityNotFoundException
import android.content.Intent
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import co.zw.nissangtr.management.rpc.SupabaseRpcClient
import co.zw.nissangtr.ui.shop.ShopSecondaryButton
import co.zw.nissangtr.ui.shop.ShopStaffScreen

/**
 * Deep-link / launch standalone `co.zw.nissangtr.pos`.
 * When Live session exists, passes handoff extras (display name + tokens).
 * Never logs access/refresh tokens.
 */
@Composable
fun OpenStandalonePosScreen(
    onBack: () -> Unit,
    supabase: SupabaseRpcClient? = null,
    staffDisplayName: String? = null,
    terminalId: String? = null,
    warehouseId: String? = null,
) {
    val context = LocalContext.current
    var error by remember { mutableStateOf<String?>(null) }
    ShopStaffScreen(title = "Open POS") {
        Text(
            "Counter sales run in the standalone GTR POS app.",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(bottom = 12.dp),
        )
        ShopSecondaryButton(
            label = "Open POS till",
            onClick = {
                try {
                    val launch = context.packageManager.getLaunchIntentForPackage(POS_PACKAGE)
                        ?: Intent().apply {
                            setClassName(POS_PACKAGE, POS_ACTIVITY)
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                    attachHandoffExtras(
                        intent = launch,
                        supabase = supabase,
                        staffDisplayName = staffDisplayName,
                        terminalId = terminalId,
                        warehouseId = warehouseId,
                    )
                    context.startActivity(launch)
                    error = null
                } catch (_: ActivityNotFoundException) {
                    error = "Install / build apps/android-pos (co.zw.nissangtr.pos)"
                } catch (e: Exception) {
                    error = e.message ?: "Launch failed"
                }
            },
        )
        error?.let {
            Text(
                it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
        ShopSecondaryButton(
            label = "Back to hub",
            onClick = onBack,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

/**
 * Same-org Intent extras. Tokens only when a live GoTrue session exists.
 * Do not Log.d tokens.
 */
internal fun attachHandoffExtras(
    intent: Intent,
    supabase: SupabaseRpcClient?,
    staffDisplayName: String?,
    terminalId: String? = null,
    warehouseId: String? = null,
) {
    val session = supabase?.auth?.currentSessionOrNull()
    val access = session?.accessToken
    if (!access.isNullOrBlank()) {
        intent.putExtra(EXTRA_HANDOFF, "1")
        intent.putExtra(EXTRA_ACCESS_TOKEN, access)
        val refresh = session.refreshToken
        if (!refresh.isNullOrBlank()) {
            intent.putExtra(EXTRA_REFRESH_TOKEN, refresh)
        }
    }
    val name = staffDisplayName?.trim()?.takeIf { it.isNotEmpty() }
        ?: session?.user?.email?.substringBefore("@")
    if (!name.isNullOrBlank()) {
        intent.putExtra(EXTRA_STAFF_DISPLAY_NAME, name)
    }
    terminalId?.trim()?.takeIf { it.isNotEmpty() }?.let {
        intent.putExtra(EXTRA_TERMINAL_ID, it)
    }
    warehouseId?.trim()?.takeIf { it.isNotEmpty() }?.let {
        intent.putExtra(EXTRA_WAREHOUSE_ID, it)
    }
}

// Keep keys in sync with apps/android-pos HandoffExtras (no shared module dependency).
private const val POS_PACKAGE = "co.zw.nissangtr.pos"
private const val POS_ACTIVITY = "co.zw.nissangtr.pos.MainActivity"
internal const val EXTRA_HANDOFF = "co.zw.nissangtr.pos.HANDOFF"
internal const val EXTRA_STAFF_DISPLAY_NAME = "co.zw.nissangtr.pos.STAFF_DISPLAY_NAME"
internal const val EXTRA_ACCESS_TOKEN = "co.zw.nissangtr.pos.ACCESS_TOKEN"
internal const val EXTRA_REFRESH_TOKEN = "co.zw.nissangtr.pos.REFRESH_TOKEN"
internal const val EXTRA_TERMINAL_ID = "co.zw.nissangtr.pos.TERMINAL_ID"
internal const val EXTRA_WAREHOUSE_ID = "co.zw.nissangtr.pos.WAREHOUSE_ID"
