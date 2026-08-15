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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import co.zw.nissangtr.customer.rpc.SupabaseRpcClient
import co.zw.nissangtr.ui.shop.ShopDefaultScreen
import kotlinx.coroutines.launch

/**
 * Email/phone password sign-in, Edge OTP signup, Edge password reset, optional Google.
 */
@Composable
fun SignInScreen(
    supabase: SupabaseRpcClient?,
    allowSkip: Boolean,
    onSkip: () -> Unit,
    modifier: Modifier = Modifier,
    title: String = "Sign in",
    subtitle: String? = null,
    sessionViewModel: AuthSessionViewModel? = null,
    googleServerClientId: String = "",
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
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val googleEnabled = googleServerClientId.isNotBlank()
    var googleError by remember { mutableStateOf<String?>(null) }

    val screenTitle = when (state.mode) {
        AuthFormMode.SignIn -> title
        AuthFormMode.SignUp -> "Create account"
        AuthFormMode.Forgot -> "Reset password"
    }

    val identifiersEditable = when (state.mode) {
        AuthFormMode.SignUp -> state.signUpStep == SignUpStep.Identifiers
        AuthFormMode.Forgot -> state.forgotStep == ForgotStep.Identifiers
        AuthFormMode.SignIn -> true
    }

    ShopDefaultScreen(
        title = screenTitle,
        subtitle = subtitle,
        modifier = modifier,
    ) {
        OutlinedTextField(
            value = state.email,
            onValueChange = vm::onEmailChange,
            label = {
                Text(
                    when (state.mode) {
                        AuthFormMode.SignIn -> "Email or phone"
                        AuthFormMode.SignUp -> "Email (required to finish signup)"
                        AuthFormMode.Forgot -> "Email"
                    },
                )
            },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            enabled = !state.busy && identifiersEditable,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            shape = MaterialTheme.shapes.extraSmall,
        )

        OutlinedTextField(
            value = state.phone,
            onValueChange = vm::onPhoneChange,
            label = { Text("Phone (+263… or 07…)") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            enabled = !state.busy && identifiersEditable,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
            shape = MaterialTheme.shapes.extraSmall,
        )

        val showPassword = when (state.mode) {
            AuthFormMode.SignIn -> true
            AuthFormMode.SignUp -> state.signUpStep == SignUpStep.Password
            AuthFormMode.Forgot -> state.forgotStep == ForgotStep.Code
        }
        if (showPassword) {
            OutlinedTextField(
                value = state.password,
                onValueChange = vm::onPasswordChange,
                label = {
                    Text(
                        when (state.mode) {
                            AuthFormMode.Forgot -> "New password"
                            else -> "Password"
                        },
                    )
                },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                enabled = !state.busy,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                shape = MaterialTheme.shapes.extraSmall,
            )
        }

        val showOtp = when (state.mode) {
            AuthFormMode.SignUp -> state.signUpStep == SignUpStep.Otp
            AuthFormMode.Forgot -> state.forgotStep == ForgotStep.Code
            AuthFormMode.SignIn -> false
        }
        if (showOtp) {
            OutlinedTextField(
                value = state.otpCode,
                onValueChange = vm::onOtpCodeChange,
                label = { Text("6-digit code") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                enabled = !state.busy,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                shape = MaterialTheme.shapes.extraSmall,
            )
            state.stubHint?.let { hint ->
                Text(
                    "Stub code (local only): $hint",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        val primaryEnabled = !state.busy && when (state.mode) {
            AuthFormMode.SignIn ->
                (state.email.isNotBlank() || state.phone.isNotBlank()) &&
                    state.password.isNotBlank()
            AuthFormMode.SignUp -> when (state.signUpStep) {
                SignUpStep.Identifiers ->
                    state.email.isNotBlank() || state.phone.isNotBlank()
                SignUpStep.Otp -> state.otpCode.length == 6
                SignUpStep.Password ->
                    state.email.isNotBlank() && state.password.length >= 8
            }
            AuthFormMode.Forgot -> when (state.forgotStep) {
                ForgotStep.Identifiers ->
                    state.email.isNotBlank() || state.phone.isNotBlank()
                ForgotStep.Code ->
                    state.otpCode.length == 6 && state.password.length >= 8
            }
        }

        Button(
            onClick = vm::primaryAction,
            modifier = Modifier.fillMaxWidth(),
            enabled = primaryEnabled,
            shape = MaterialTheme.shapes.medium,
        ) {
            Text(
                when {
                    state.busy -> "Please wait…"
                    state.mode == AuthFormMode.SignIn -> "Sign in"
                    state.mode == AuthFormMode.SignUp &&
                        state.signUpStep == SignUpStep.Identifiers ->
                        "Send verification code"
                    state.mode == AuthFormMode.SignUp && state.signUpStep == SignUpStep.Otp ->
                        "Verify code"
                    state.mode == AuthFormMode.SignUp -> "Create account"
                    state.mode == AuthFormMode.Forgot &&
                        state.forgotStep == ForgotStep.Identifiers ->
                        "Send reset code"
                    else -> "Set new password"
                },
            )
        }

        if (state.mode == AuthFormMode.SignIn) {
            OutlinedButton(
                onClick = { vm.setMode(AuthFormMode.SignUp) },
                modifier = Modifier.fillMaxWidth(),
                enabled = !state.busy,
                shape = MaterialTheme.shapes.medium,
            ) {
                Text("Need an account? Sign up")
            }
            TextButton(
                onClick = { vm.setMode(AuthFormMode.Forgot) },
                enabled = !state.busy,
            ) {
                Text("Forgot password?")
            }
            if (googleEnabled) {
                OutlinedButton(
                    onClick = {
                        googleError = null
                        scope.launch {
                            try {
                                val result = GoogleIdTokenSignIn.requestIdToken(
                                    context = context,
                                    serverClientId = googleServerClientId,
                                )
                                vm.signInWithGoogleIdToken(result.idToken, result.rawNonce)
                            } catch (e: Exception) {
                                googleError = GoogleIdTokenSignIn.userMessage(e)
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !state.busy,
                    shape = MaterialTheme.shapes.medium,
                ) {
                    Text(if (state.busy) "Signing in with Google…" else "Continue with Google")
                }
            } else {
                Text(
                    "Google Sign-In appears when GOOGLE_WEB_CLIENT_ID is set in local.properties.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            TextButton(
                onClick = { vm.setMode(AuthFormMode.SignIn) },
                enabled = !state.busy,
            ) {
                Text("Back to sign in")
            }
        }

        if (allowSkip && state.mode == AuthFormMode.SignIn) {
            OutlinedButton(
                onClick = onSkip,
                modifier = Modifier.fillMaxWidth(),
                enabled = !state.busy,
                shape = MaterialTheme.shapes.medium,
            ) {
                Text("Continue without signing in")
            }
        }
        state.info?.let {
            Text(it, style = MaterialTheme.typography.bodySmall)
        }
        (googleError ?: state.error)?.let {
            Text(it, color = MaterialTheme.colorScheme.error)
        }
    }
}

@Composable
private fun FakeSignInPlaceholder(
    title: String,
    subtitle: String?,
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
            "GoTrue sign-in needs Live SUPABASE_URL + ANON_KEY.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
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
 */
@Composable
fun AuthGate(
    liveRpc: Boolean,
    supabase: SupabaseRpcClient?,
    allowFakeSkip: Boolean = true,
    showFakeLogin: Boolean = false,
    googleServerClientId: String = "",
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
                googleServerClientId = googleServerClientId,
            )
        }
        is AuthGateState.SignedIn -> {
            content(g.email, vm::signOut, vm)
        }
    }
}
