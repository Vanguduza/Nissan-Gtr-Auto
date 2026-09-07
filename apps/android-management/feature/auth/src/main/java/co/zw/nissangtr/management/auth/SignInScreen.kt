package co.zw.nissangtr.management.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import co.zw.nissangtr.management.rpc.SupabaseRpcClient
import co.zw.nissangtr.ui.shop.ShopPrimaryButton
import co.zw.nissangtr.ui.shop.ShopSecondaryButton
import co.zw.nissangtr.ui.theme.GtrColors
import co.zw.nissangtr.ui.theme.GtrLogo

/**
 * Staff tablet login — Shopping-By-KMP [LoginScreen] layout (display title, label-above
 * fields, 60dp pill CTA, divider row) with GTR brand tokens. Emp# / email / phone + password
 * via [resolve_staff_login_email] then GoTrue. Not for customer storefront.
 */
@Composable
fun SignInScreen(
    supabase: SupabaseRpcClient?,
    allowSkip: Boolean,
    onSkip: () -> Unit,
    modifier: Modifier = Modifier,
    title: String = "Sign in",
    subtitle: String = "Welcome to Nissan GTR Auto staff",
    sessionViewModel: AuthSessionViewModel? = null,
) {
    if (supabase == null) {
        FakeSignInPlaceholder(
            title = title,
            subtitle = subtitle,
            allowSkip = allowSkip,
            onSkip = onSkip,
            modifier = modifier,
        )
        return
    }

    val vm = sessionViewModel
        ?: viewModel(factory = AuthSessionViewModel.factory(supabase))
    val state by vm.signIn.collectAsState()

    KmpLoginScaffold(modifier = modifier) {
        Text(title, style = MaterialTheme.typography.displaySmall)
        Spacer(modifier = Modifier.height(16.dp))
        Text(subtitle, style = MaterialTheme.typography.labelMedium)
        Spacer(modifier = Modifier.height(32.dp))

        Column(horizontalAlignment = Alignment.Start, modifier = Modifier.fillMaxWidth()) {
            Text("Emp # / email / phone", style = MaterialTheme.typography.bodyMedium)
            Spacer(modifier = Modifier.height(4.dp))
            TextField(
                value = state.identifier,
                onValueChange = vm::onIdentifierChange,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                enabled = !state.busy,
                shape = MaterialTheme.shapes.small,
                colors = kmpTextFieldColors(),
                keyboardOptions = KeyboardOptions(
                    imeAction = ImeAction.Next,
                    keyboardType = KeyboardType.Email,
                ),
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text("Password", style = MaterialTheme.typography.bodyMedium)
            Spacer(modifier = Modifier.height(4.dp))
            TextField(
                value = state.password,
                onValueChange = vm::onPasswordChange,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                enabled = !state.busy,
                shape = MaterialTheme.shapes.small,
                visualTransformation = PasswordVisualTransformation(),
                colors = kmpTextFieldColors(),
                keyboardOptions = KeyboardOptions(
                    imeAction = ImeAction.Done,
                    keyboardType = KeyboardType.Password,
                ),
            )
        }

        Spacer(modifier = Modifier.height(32.dp))

        ShopPrimaryButton(
            label = if (state.busy) "Signing in…" else "Sign in",
            onClick = vm::signIn,
            enabled = !state.busy && state.identifier.isNotBlank() && state.password.isNotBlank(),
        )

        Spacer(modifier = Modifier.height(32.dp))
        LaterAuthDivider()
        Spacer(modifier = Modifier.height(24.dp))
        LaterAuthStubs(enabled = false)

        if (allowSkip) {
            Spacer(modifier = Modifier.height(16.dp))
            ShopSecondaryButton(
                label = "Continue without signing in",
                onClick = onSkip,
                enabled = !state.busy,
            )
        }

        state.error?.let {
            Spacer(modifier = Modifier.height(12.dp))
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun KmpLoginScaffold(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            GtrLogo(modifier = Modifier.height(40.dp).width(140.dp))
            Spacer(modifier = Modifier.height(24.dp))
            content()
        }
    }
}

@Composable
private fun LaterAuthDivider() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        HorizontalDivider(modifier = Modifier.width(72.dp), color = GtrColors.Mist)
        Text(
            "Later sign-in methods",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        HorizontalDivider(modifier = Modifier.width(72.dp), color = GtrColors.Mist)
    }
}

/**
 * Catalog features #16–19 — visible but disabled until ADR + Bridge work.
 * Do not wire NFC / QR badge / biometric here (Bridge-First Later).
 */
@Composable
private fun LaterAuthStubs(enabled: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        listOf("PIN", "NFC", "QR", "Bio").forEach { label ->
            OutlinedButton(
                onClick = {},
                enabled = enabled,
                shape = MaterialTheme.shapes.extraLarge,
                modifier = Modifier.height(44.dp),
            ) { Text(label, style = MaterialTheme.typography.labelMedium) }
        }
    }
}

@Composable
private fun FakeSignInPlaceholder(
    title: String,
    subtitle: String,
    allowSkip: Boolean,
    onSkip: () -> Unit,
    modifier: Modifier = Modifier,
) {
    KmpLoginScaffold(modifier = modifier) {
        Text(title, style = MaterialTheme.typography.displaySmall)
        Spacer(modifier = Modifier.height(16.dp))
        Text(subtitle, style = MaterialTheme.typography.labelMedium)
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            "RPC Fake mode — emp#|email|phone resolve needs Live SUPABASE_URL + ANON_KEY.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(32.dp))
        LaterAuthDivider()
        Spacer(modifier = Modifier.height(24.dp))
        LaterAuthStubs(enabled = false)
        if (allowSkip) {
            Spacer(modifier = Modifier.height(24.dp))
            ShopPrimaryButton(
                label = "Continue without signing in",
                onClick = onSkip,
            )
        }
    }
}

@Composable
private fun kmpTextFieldColors() = TextFieldDefaults.colors(
    focusedContainerColor = GtrColors.Mist,
    unfocusedContainerColor = GtrColors.Mist,
    disabledContainerColor = GtrColors.Mist.copy(alpha = 0.6f),
    focusedIndicatorColor = GtrColors.Primary,
    unfocusedIndicatorColor = GtrColors.Silver,
)

/**
 * Live: block until Authenticated. Fake: bypass by default ([allowFakeSkip]).
 */
@Composable
fun AuthGate(
    liveRpc: Boolean,
    supabase: SupabaseRpcClient?,
    allowFakeSkip: Boolean = true,
    showFakeLogin: Boolean = false,
    forceFreshSignIn: Boolean = false,
    content: @Composable (email: String?, onSignOut: () -> Unit) -> Unit,
) {
    if (!liveRpc || supabase == null) {
        var skipped by remember { mutableStateOf(allowFakeSkip && !showFakeLogin) }
        if (skipped) {
            content(null) { /* no session in Fake */ }
        } else {
            SignInScreen(
                supabase = null,
                allowSkip = allowFakeSkip,
                onSkip = { skipped = true },
                subtitle = "Welcome to Nissan GTR Auto staff",
            )
        }
        return
    }

    val vm: AuthSessionViewModel = viewModel(factory = AuthSessionViewModel.factory(supabase))
    val gate by vm.gate.collectAsState()
    var freshSignInBoundaryReached by remember { mutableStateOf(!forceFreshSignIn) }

    LaunchedEffect(forceFreshSignIn) {
        if (forceFreshSignIn) runCatching { supabase.signOut() }
    }
    LaunchedEffect(forceFreshSignIn, gate) {
        if (forceFreshSignIn && gate is AuthGateState.NeedsSignIn) {
            // Latch once: after cold-start sign-out has reached NotAuthenticated, a later
            // successful sign-in may pass through normally without being forced out again.
            freshSignInBoundaryReached = true
        }
    }

    if (!freshSignInBoundaryReached) {
        // Render the actual branded login surface while clearing any restored session;
        // never expose a previously-authenticated POS frame during kiosk cold start.
        SignInScreen(
            supabase = supabase,
            allowSkip = false,
            onSkip = {},
            subtitle = "Welcome to Nissan GTR Auto staff",
            sessionViewModel = vm,
        )
        return
    }

    when (val g = gate) {
        is AuthGateState.Checking -> {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background),
                contentAlignment = Alignment.Center,
            ) {
                Text("Restoring session…", style = MaterialTheme.typography.bodyMedium)
            }
        }
        is AuthGateState.NeedsSignIn -> {
            SignInScreen(
                supabase = supabase,
                allowSkip = false,
                onSkip = {},
                subtitle = "Welcome to Nissan GTR Auto staff",
                sessionViewModel = vm,
            )
        }
        is AuthGateState.SignedIn -> {
            content(g.email, vm::signOut)
        }
    }
}
