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
import co.zw.nissangtr.customer.cart.CartModule
import co.zw.nissangtr.customer.cart.CartScreen
import co.zw.nissangtr.customer.garage.GarageModule
import co.zw.nissangtr.customer.garage.GarageScreen
import co.zw.nissangtr.customer.orders.OrdersModule
import co.zw.nissangtr.customer.orders.OrdersScreen
import co.zw.nissangtr.customer.pay.PayIntentScreen
import co.zw.nissangtr.customer.pay.PayModule
import co.zw.nissangtr.customer.rpc.FakeRpcClient
import co.zw.nissangtr.customer.rpc.RpcClient

private enum class CustomerRoute {
    Home,
    Cart,
    Orders,
    Garage,
    Pay,
}

/**
 * Customer shell. Feature screens are thin scaffolds over [RpcClient]
 * (FakeRpcClient until Supabase Kotlin SDK is wired).
 * Money/pricing: @gtr/shared. Hardware QR: bridges/ only — never HTML5.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Keep placeholder modules on the compile classpath.
        listOf(CartModule.id, OrdersModule.id, GarageModule.id, PayModule.id)
        // TODO(live): build SupabaseRpcClient from BuildConfig.SUPABASE_URL / ANON_KEY
        val rpc: RpcClient = FakeRpcClient()
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    CustomerApp(rpc = rpc)
                }
            }
        }
    }
}

@Composable
private fun CustomerApp(rpc: RpcClient) {
    var route by remember { mutableStateOf(CustomerRoute.Home) }
    when (route) {
        CustomerRoute.Home -> CustomerHome(
            onCart = { route = CustomerRoute.Cart },
            onOrders = { route = CustomerRoute.Orders },
            onGarage = { route = CustomerRoute.Garage },
            onPay = { route = CustomerRoute.Pay },
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
    }
}

@Composable
private fun CustomerHome(
    onCart: () -> Unit,
    onOrders: () -> Unit,
    onGarage: () -> Unit,
    onPay: () -> Unit,
) {
    val configured =
        BuildConfig.SUPABASE_URL.isNotBlank() && BuildConfig.SUPABASE_ANON_KEY.isNotBlank()
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
            "Modules: ${CartModule.id}, ${OrdersModule.id}, ${GarageModule.id}, ${PayModule.id}",
            style = MaterialTheme.typography.bodySmall,
        )
        Text(
            if (configured) "Supabase env present (live client TODO)"
            else "Set SUPABASE_URL + SUPABASE_ANON_KEY for live bind",
            style = MaterialTheme.typography.bodySmall,
        )
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
        Text(
            "RPC: FakeRpcClient stub (see core:rpc). Bridge-First for QR — no HTML5.",
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}
