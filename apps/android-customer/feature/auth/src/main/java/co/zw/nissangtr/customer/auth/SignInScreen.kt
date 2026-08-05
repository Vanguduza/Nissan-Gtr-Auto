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
import androidx.compose.material3.TextButton
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
import co.zw.nissangtr.ui.shop.ShopDefaultScreen

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

    ShopDefaultScreen(
        title = title,
        subtitle = subtitle,
        modifier = modifier,
    ) {
        Text(
            "Session persisted by supabase-kt Auth — no JWTs in BuildConfig.",
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
            shape = MaterialTheme.shapes.extraSmall,
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
            shape = MaterialTheme.shapes.extraSmall,
        )
        Button(
            onClick = {
                if (state.mode == AuthFormMode.SignIn) vm.signIn() else vm.signUp()
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = !state.busy && state.email.isNotBlank() && state.password.isNotBlank(),
            shape = MaterialTheme.shapes.extraSmall,
        ) {
            Text(
                when {
                    state.busy && state.mode == AuthFormMode.SignIn -> "Signing in…"
                    state.busy -> "Creating account…"
                    state.mode == AuthFormMode.SignUp -> "Create account"
                    else -> "Sign in"
                },
            )
        }
        OutlinedButton(
            onClick = {
                vm.setMode(
                    if (state.mode == AuthFormMode.SignIn) AuthFormMode.SignUp
                    else AuthFormMode.SignIn,
                )
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = !state.busy,
            shape = MaterialTheme.shapes.extraSmall,
        ) {
            Text(
                if (state.mode == AuthFormMode.SignIn) "Need an account? Sign up"
                else "Have an account? Sign in",
            )
        }
        TextButton(
            onClick = vm::forgotPassword,
            enabled = !state.busy,
        ) {
            Text("Forgot password?")
        }
        Text(
            "Social login (Google / Facebook) appears only when GoTrue providers are configured — hidden until then.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (allowSkip) {
            OutlinedButton(
                onClick = onSkip,
                modifier = Modifier.fillMaxWidth(),
                enabled = !state.busy,
                shape = MaterialTheme.shapes.extraSmall,
            ) {
                Text("Continue without signing in")
            }
        }
        state.info?.let {
            Text(it, style = MaterialTheme.typography.bodySmall)
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
    ShopDefaultScreen(
        title = title,
        subtitle = subtitle,
        modifier = modifier,
    ) {
        Text(
            "RPC Fake mode — GoTrue sign-in needs Live SUPABASE_URL + ANON_KEY.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (allowSkip) {
            Button(
                onClick = onSkip,
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.extraSmall,
            ) {
                Text("Continue without signing in")
            }
        }
    }
}

/**
 * Live: block until Authenticated. Fake: bypass by default ([allowFakeSkip]).
 * Exposes the single [AuthSessionViewModel] to content so shell SignIn overlay never creates an orphan VM.
 */
@Composable
fun AuthGate(
    liveRpc: Boolean,
    supabase: SupabaseRpcClient?,
    allowFakeSkip: Boolean = true,
    showFakeLogin: Boolean = false,
    content: @Composable (
        email: String?,
        onSignOut: () -> Unit,
        sessionViewModel: AuthSessionViewModel?,
    ) -> Unit,
) {
    if (!liveRpc || supabase == null) {
        var skipped by remember { mutableStateOf(allowFakeSkip && !showFakeLogin) }
        if (skipped) {
            content(null, { /* no session in Fake */ }, null)
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
            content(g.email, vm::signOut, vm)
        }
    }
}
