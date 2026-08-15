package co.zw.nissangtr.management

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.PointOfSale
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import co.zw.nissangtr.management.auth.AuthGate
import co.zw.nissangtr.management.pos.OpenStandalonePosScreen
import co.zw.nissangtr.management.rpc.ManagementHomeLanding
import co.zw.nissangtr.management.rpc.ManagementHomeRoles
import co.zw.nissangtr.management.rpc.RpcClient
import co.zw.nissangtr.management.rpc.RpcClientFactory
import co.zw.nissangtr.management.rpc.SupabaseRpcClient
import co.zw.nissangtr.management.warehouse.WarehouseReceiveScreen
import co.zw.nissangtr.ui.shop.ShopSecondaryButton
import co.zw.nissangtr.ui.shop.ShopSectionHeader
import co.zw.nissangtr.ui.shop.ShopStaffScreen
import co.zw.nissangtr.ui.shop.StaffModuleTile
import co.zw.nissangtr.ui.theme.GtrTheme

/**
 * Phase 1 OSS shell — CoolMall-inspired modular chrome + inventree-like warehouse entry.
 * Till chrome lives in apps/android-pos; this app deep-links via [OpenStandalonePosScreen].
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val forceFake = BuildConfig.RPC_FORCE_FAKE
        val rpc = RpcClientFactory.create(
            supabaseUrl = BuildConfig.SUPABASE_URL,
            supabaseAnonKey = BuildConfig.SUPABASE_ANON_KEY,
            forceFake = forceFake,
        )
        val live = RpcClientFactory.isLive(
            supabaseUrl = BuildConfig.SUPABASE_URL,
            supabaseAnonKey = BuildConfig.SUPABASE_ANON_KEY,
            forceFake = forceFake,
        )
        val supabase = rpc as? SupabaseRpcClient

        setContent {
            GtrTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    AuthGate(
                        liveRpc = live,
                        supabase = supabase,
                        allowFakeSkip = true,
                    ) { email, onSignOut ->
                        ManagementShell(
                            rpc = rpc,
                            email = email,
                            onSignOut = onSignOut,
                            isTabletKiosk = BuildConfig.IS_TABLET_KIOSK,
                        )
                    }
                }
            }
        }
    }
}

private enum class ShellRoute {
    Hub,
    RoleDenied,
    Pos,
    WarehouseReceive,
}

@Composable
private fun ManagementShell(
    rpc: RpcClient,
    email: String?,
    onSignOut: () -> Unit,
    isTabletKiosk: Boolean,
) {
    var roles by remember { mutableStateOf<List<String>>(emptyList()) }
    var moduleAccess by remember { mutableStateOf<List<String>>(emptyList()) }
    var route by remember { mutableStateOf(ShellRoute.Hub) }
    var booted by remember { mutableStateOf(false) }

    LaunchedEffect(rpc) {
        roles = runCatching { rpc.listMyStaffRoles() }.getOrDefault(emptyList())
        moduleAccess = runCatching { rpc.listMyModuleAccess() }.getOrDefault(emptyList())
        route = when (ManagementHomeRoles.resolveLanding(roles)) {
            ManagementHomeLanding.Deny -> ShellRoute.RoleDenied
            ManagementHomeLanding.Pos -> ShellRoute.Pos
            ManagementHomeLanding.Hub -> ShellRoute.Hub
        }
        booted = true
    }

    if (!booted) {
        ShopStaffScreen(title = "GTR Management") {
            Text("Loading roles…", style = MaterialTheme.typography.bodyMedium)
        }
        return
    }

    when (route) {
        ShellRoute.RoleDenied -> {
            ShopStaffScreen(title = "Access denied") {
                Text(
                    "No recognized staff role. Fail closed.",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(bottom = 16.dp),
                )
                ShopSecondaryButton(label = "Sign out", onClick = onSignOut)
            }
        }
        ShellRoute.Hub -> {
            RoleHubScreen(
                email = email,
                roles = roles,
                moduleAccess = moduleAccess,
                isTabletKiosk = isTabletKiosk,
                onOpenPos = { route = ShellRoute.Pos },
                onOpenWarehouse = { route = ShellRoute.WarehouseReceive },
                onSignOut = onSignOut,
            )
        }
        ShellRoute.Pos -> {
            OpenStandalonePosScreen(
                onBack = { route = ShellRoute.Hub },
                supabase = rpc as? SupabaseRpcClient,
                staffDisplayName = email,
            )
        }
        ShellRoute.WarehouseReceive -> {
            WarehouseReceiveScreen(
                rpc = rpc,
                onBack = { route = ShellRoute.Hub },
            )
        }
    }
}

@Composable
private fun RoleHubScreen(
    email: String?,
    roles: List<String>,
    moduleAccess: List<String>,
    isTabletKiosk: Boolean,
    onOpenPos: () -> Unit,
    onOpenWarehouse: () -> Unit,
    onSignOut: () -> Unit,
) {
    val posOk = ManagementHomeRoles.moduleAllowed("pos", roles, moduleAccess)
    val whOk = ManagementHomeRoles.moduleAllowed("warehouse", roles, moduleAccess)

    ShopStaffScreen(title = "Staff hub") {
        Text(
            email?.let { "Signed in as $it" } ?: "Fake mode — Supabase structures via RpcClient",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            "Roles: ${roles.joinToString().ifBlank { "—" }}",
            style = MaterialTheme.typography.labelMedium,
        )
        if (isTabletKiosk) {
            Text(
                "Tablet kiosk flavor — Lock Task port is Phase 2 (see legacy).",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        ShopSectionHeader(title = "Phase 1 modules", actionLabel = null)
        if (posOk) {
            StaffModuleTile(
                title = "Open POS",
                subtitle = "Standalone till · co.zw.nissangtr.pos",
                icon = Icons.Filled.PointOfSale,
                onClick = onOpenPos,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (whOk) {
            StaffModuleTile(
                title = "Warehouse receive",
                subtitle = "InvenTree IA · scan/OEM → location → receive",
                icon = Icons.Filled.Inventory2,
                onClick = onOpenWarehouse,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        ShopSecondaryButton(
            label = "Sign out",
            onClick = onSignOut,
            modifier = Modifier.padding(top = 8.dp),
        )
        Text(
            "More modules (dispatch, HR, procurement, …) remain in android-management-legacy until Phase 2+.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
