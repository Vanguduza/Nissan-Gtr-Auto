package co.zw.nissangtr.customer

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import co.zw.nissangtr.customer.auth.AuthGate
import co.zw.nissangtr.customer.auth.AuthModule
import co.zw.nissangtr.customer.cart.CartModule
import co.zw.nissangtr.customer.cart.CartScreen
import co.zw.nissangtr.customer.chat.ChatModule
import co.zw.nissangtr.customer.chat.ChatScreen
import co.zw.nissangtr.customer.garage.GarageModule
import co.zw.nissangtr.customer.garage.GarageScreen
import co.zw.nissangtr.customer.orders.OrdersModule
import co.zw.nissangtr.customer.orders.OrdersScreen
import co.zw.nissangtr.customer.pay.PayIntentScreen
import co.zw.nissangtr.customer.pay.PayModule
import co.zw.nissangtr.customer.rpc.RpcClient
import co.zw.nissangtr.customer.rpc.RpcClientFactory
import co.zw.nissangtr.customer.rpc.SupabaseRpcClient

private enum class CustomerRoute {
    Home,
    Cart,
    Orders,
    Garage,
    Pay,
    Chat,
}

/**
 * Customer shell. Feature screens are thin scaffolds over [RpcClient]
 * ([RpcClientFactory]: Live [SupabaseRpcClient] or Fake).
 * Live requires GoTrue email/password session via [AuthGate].
 * Money/pricing: @gtr/shared. Hardware QR: bridges/ only — never HTML5.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        listOf(
            AuthModule.id,
            CartModule.id,
            OrdersModule.id,
            GarageModule.id,
            PayModule.id,
            ChatModule.id,
        )
        val live = RpcClientFactory.isLive(
            BuildConfig.SUPABASE_URL,
            BuildConfig.SUPABASE_ANON_KEY,
            BuildConfig.RPC_FORCE_FAKE,
        )
        val rpc: RpcClient = RpcClientFactory.create(
            supabaseUrl = BuildConfig.SUPABASE_URL,
            supabaseAnonKey = BuildConfig.SUPABASE_ANON_KEY,
            forceFake = BuildConfig.RPC_FORCE_FAKE,
        )
        val supabase = rpc as? SupabaseRpcClient
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    AuthGate(liveRpc = live, supabase = supabase) { email, onSignOut ->
                        CustomerApp(
                            rpc = rpc,
                            liveRpc = live,
                            signedInEmail = email,
                            onSignOut = onSignOut,
                            whatsappE164 = BuildConfig.WHATSAPP_E164,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CustomerApp(
    rpc: RpcClient,
    liveRpc: Boolean,
    signedInEmail: String?,
    onSignOut: () -> Unit,
    whatsappE164: String,
) {
    var route by remember { mutableStateOf(CustomerRoute.Home) }
    when (route) {
        CustomerRoute.Home -> CustomerHome(
            liveRpc = liveRpc,
            signedInEmail = signedInEmail,
            onSignOut = onSignOut,
            onCart = { route = CustomerRoute.Cart },
            onOrders = { route = CustomerRoute.Orders },
            onGarage = { route = CustomerRoute.Garage },
            onPay = { route = CustomerRoute.Pay },
            onChat = { route = CustomerRoute.Chat },
        )
        CustomerRoute.Cart -> CartScreen(
            rpc = rpc,
            onBack = { route = CustomerRoute.Home },
        )
        CustomerRoute.Orders -> OrdersScreen(
            rpc = rpc,
            onBack = { route = CustomerRoute.Home },
        )
        CustomerRoute.Garage -> GarageScreen(
            rpc = rpc,
            onBack = { route = CustomerRoute.Home },
        )
        CustomerRoute.Pay -> PayIntentScreen(
            rpc = rpc,
            onBack = { route = CustomerRoute.Home },
        )
        CustomerRoute.Chat -> ChatScreen(
            rpc = rpc,
            onBack = { route = CustomerRoute.Home },
            whatsappE164Digits = whatsappE164,
        )
    }
}

@Composable
private fun CustomerHome(
    liveRpc: Boolean,
    signedInEmail: String?,
    onSignOut: () -> Unit,
    onCart: () -> Unit,
    onOrders: () -> Unit,
    onGarage: () -> Unit,
    onPay: () -> Unit,
    onChat: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Nissan GTR Auto", style = MaterialTheme.typography.headlineMedium)
        Text("Customer app", style = MaterialTheme.typography.bodyMedium)
        Text(
            "Modules: ${AuthModule.id}, ${CartModule.id}, ${OrdersModule.id}, " +
                "${GarageModule.id}, ${PayModule.id}, ${ChatModule.id}",
            style = MaterialTheme.typography.bodySmall,
        )
        Text(
            if (liveRpc) "RPC: Live (supabase-kt)"
            else "RPC: Fake (set SUPABASE_URL + SUPABASE_ANON_KEY)",
            style = MaterialTheme.typography.bodySmall,
        )
        if (signedInEmail != null) {
            Text("Signed in: $signedInEmail", style = MaterialTheme.typography.bodySmall)
            OutlinedButton(onClick = onSignOut, modifier = Modifier.fillMaxWidth()) {
                Text("Sign out")
            }
        } else if (!liveRpc) {
            Text("Fake mode — auth optional / bypassed", style = MaterialTheme.typography.bodySmall)
        }
        Button(onClick = onCart, modifier = Modifier.fillMaxWidth()) {
            Text("Cart")
        }
        Button(onClick = onOrders, modifier = Modifier.fillMaxWidth()) {
            Text("Orders")
        }
        Button(onClick = onGarage, modifier = Modifier.fillMaxWidth()) {
            Text("My Garage")
        }
        Button(onClick = onPay, modifier = Modifier.fillMaxWidth()) {
            Text("Pay — ContiPay / Paynow")
        }
        Button(onClick = onChat, modifier = Modifier.fillMaxWidth()) {
            Text("Live chat")
        }
        Text(
            "Auth: GoTrue signInWith(Email). Bridge-First for QR.",
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}
