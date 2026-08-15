package co.zw.nissangtr.delivery.auth

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import co.zw.nissangtr.delivery.rpc.SupabaseRpcClient
import co.zw.nissangtr.ui.shop.ShopDefaultScreen
import co.zw.nissangtr.ui.shop.ShopPrimaryButton
import co.zw.nissangtr.ui.shop.ShopProfileAvatar
import co.zw.nissangtr.ui.shop.ShopSecondaryButton
import co.zw.nissangtr.ui.theme.GtrColors

@Composable
fun SignInScreen(
    supabase: SupabaseRpcClient?,
    allowSkip: Boolean,
    onSkip: () -> Unit,
    modifier: Modifier = Modifier,
    title: String = "Driver sign in",
    subtitle: String = "Nissan GTR Auto · delivery",
    sessionViewModel: AuthSessionViewModel? = null,
) {
    if (supabase == null) {
        // Shopping-By-KMP DefaultScreenUI — full ShopKit density (no ShopStaff*).
        ShopDefaultScreen(
            title = title,
            subtitle = subtitle,
            modifier = modifier,
        ) {
            ShopProfileAvatar(initials = "DR")
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                "RPC Fake mode — GoTrue sign-in needs Live SUPABASE_URL + ANON_KEY.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (allowSkip) {
                ShopPrimaryButton(
                    label = "Continue without signing in",
                    onClick = onSkip,
                )
            }
        }
        return
    }

    val vm = sessionViewModel
        ?: viewModel(factory = AuthSessionViewModel.factory(supabase))
    val state by vm.signIn.collectAsState()

    ShopDefaultScreen(
        title = title,
        subtitle = subtitle,
        modifier = modifier,
    ) {
        ShopProfileAvatar(initials = state.email.take(2).ifBlank { "DR" })
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            "Session via supabase-kt Auth — no JWTs in BuildConfig.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedTextField(
            value = state.email,
            onValueChange = vm::onEmailChange,
            label = { Text("Email") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            enabled = !state.busy,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            shape = MaterialTheme.shapes.small,
        )
        OutlinedTextField(
            value = state.password,
            onValueChange = vm::onPasswordChange,
            label = { Text("Password") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            enabled = !state.busy,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            shape = MaterialTheme.shapes.small,
        )
        ShopPrimaryButton(
            label = if (state.busy) "Signing in…" else "Sign in",
            onClick = vm::signIn,
            enabled = !state.busy && state.email.isNotBlank() && state.password.isNotBlank(),
        )
        if (allowSkip) {
            ShopSecondaryButton(
                label = "Continue without signing in",
                onClick = onSkip,
                enabled = !state.busy,
            )
        }
        state.error?.let {
            Text(it, color = MaterialTheme.colorScheme.error)
        }
    }
}

/**
 * Live: block until Authenticated + driver|admin role.
 * Fake: bypass by default ([allowFakeSkip]).
 */
@Composable
fun AuthGate(
    liveRpc: Boolean,
    supabase: SupabaseRpcClient?,
    allowFakeSkip: Boolean = true,
    content: @Composable (email: String?, onSignOut: () -> Unit) -> Unit,
) {
    if (!liveRpc || supabase == null) {
        var skipped by remember { mutableStateOf(allowFakeSkip) }
        if (skipped) {
            content("fake@driver.local") { skipped = false }
        } else {
            SignInScreen(
                supabase = null,
                allowSkip = allowFakeSkip,
                onSkip = { skipped = true },
            )
        }
        return
    }

    val vm: AuthSessionViewModel = viewModel(factory = AuthSessionViewModel.factory(supabase))
    val gate by vm.gate.collectAsState()

    when (val g = gate) {
        is AuthGateState.Checking -> {
            ShopDefaultScreen(
                title = "Nissan GTR Auto",
                subtitle = "Driver",
            ) {
                Text("Restoring session…", style = MaterialTheme.typography.bodyMedium)
                Text(
                    "Shopping-By-KMP splash → auth gate",
                    style = MaterialTheme.typography.bodySmall,
                    color = GtrColors.SilverDim,
                )
            }
        }
        is AuthGateState.NeedsSignIn -> {
            SignInScreen(
                supabase = supabase,
                allowSkip = false,
                onSkip = {},
                sessionViewModel = vm,
            )
        }
        is AuthGateState.WrongRole -> {
            ShopDefaultScreen(
                title = "Driver access required",
                subtitle = "Wrong staff role",
            ) {
                ShopProfileAvatar(initials = "!")
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    "Signed in but staff roles ${g.roles} do not include driver. " +
                        "Use a driver account or ask admin to grant role.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                ShopPrimaryButton(label = "Sign out", onClick = vm::signOut)
            }
        }
        is AuthGateState.SignedIn -> {
            content(g.email, vm::signOut)
        }
    }
}
