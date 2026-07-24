package co.zw.nissangtr.customer.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import co.zw.nissangtr.customer.rpc.SupabaseRpcClient

/**
 * Email/password sign-in. Live: [SupabaseRpcClient.signInWithEmail] → GoTrue session.
 * Fake: [allowSkip] shows Continue without signing in.
 */
@Composable
fun SignInScreen(
    supabase: SupabaseRpcClient?,
    allowSkip: Boolean,
    onSkip: () -> Unit,
    modifier: Modifier = Modifier,
    title: String = "Sign in",
    subtitle: String = "Customer account",
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

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(title, style = MaterialTheme.typography.headlineMedium)
        Text(subtitle, style = MaterialTheme.typography.bodyMedium)
        Text(
            "Session persisted by supabase-kt Auth — no JWTs in BuildConfig.",
            style = MaterialTheme.typography.bodySmall,
        )
        OutlinedTextField(
            value = state.email,
            onValueChange = vm::onEmailChange,
            label = { Text("Email") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            enabled = !state.busy,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
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
        )
        Button(
            onClick = vm::signIn,
            modifier = Modifier.fillMaxWidth(),
            enabled = !state.busy && state.email.isNotBlank() && state.password.isNotBlank(),
        ) {
            Text(if (state.busy) "Signing in…" else "Sign in")
        }
        if (allowSkip) {
            OutlinedButton(
                onClick = onSkip,
                modifier = Modifier.fillMaxWidth(),
                enabled = !state.busy,
            ) {
                Text("Continue without signing in")
            }
        }
        state.error?.let {
            Text(it, color = MaterialTheme.colorScheme.error)
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
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(title, style = MaterialTheme.typography.headlineMedium)
        Text(subtitle, style = MaterialTheme.typography.bodyMedium)
        Text(
            "RPC Fake mode — GoTrue sign-in needs Live SUPABASE_URL + ANON_KEY.",
            style = MaterialTheme.typography.bodySmall,
        )
        if (allowSkip) {
            Button(onClick = onSkip, modifier = Modifier.fillMaxWidth()) {
                Text("Continue without signing in")
            }
        }
    }
}

/**
 * Live: block until Authenticated. Fake: bypass by default ([allowFakeSkip]).
 */
@Composable
fun AuthGate(
    liveRpc: Boolean,
    supabase: SupabaseRpcClient?,
    allowFakeSkip: Boolean = true,
    showFakeLogin: Boolean = false,
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
            )
        }
        return
    }

    val vm: AuthSessionViewModel = viewModel(factory = AuthSessionViewModel.factory(supabase))
    val gate by vm.gate.collectAsState()

    when (val g = gate) {
        is AuthGateState.Checking -> {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("Restoring session…", style = MaterialTheme.typography.bodyMedium)
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
        is AuthGateState.SignedIn -> {
            content(g.email, vm::signOut)
        }
    }
}
