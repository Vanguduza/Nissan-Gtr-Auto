package co.zw.nissangtr.customer.auth

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import co.zw.nissangtr.customer.rpc.SupabaseRpcClient
import co.zw.nissangtr.customer.visual.GtrPremiumColors
import co.zw.nissangtr.customer.visual.PremiumMessageBanner
import co.zw.nissangtr.customer.visual.PremiumMessageKind
import co.zw.nissangtr.customer.visual.PremiumPrimaryButton
import co.zw.nissangtr.customer.visual.PremiumSecondaryButton
import co.zw.nissangtr.customer.visual.PremiumSurfaceCard
import co.zw.nissangtr.customer.visual.R
import kotlinx.coroutines.launch

@Composable
fun SignInScreen(
    supabase: SupabaseRpcClient?,
    allowSkip: Boolean,
    onSkip: () -> Unit,
    modifier: Modifier = Modifier,
    title: String = "Welcome back",
    subtitle: String? = null,
    sessionViewModel: AuthSessionViewModel? = null,
    googleServerClientId: String = "",
) {
    if (supabase == null) {
        FakeSignInPlaceholder(
            allowSkip = allowSkip,
            onSkip = onSkip,
            modifier = modifier,
        )
        return
    }

    val vm = sessionViewModel ?: viewModel(factory = AuthSessionViewModel.factory(supabase))
    val state by vm.signIn.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val googleEnabled = googleServerClientId.isNotBlank()
    var googleError by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(GtrPremiumColors.Background),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(230.dp)
                .background(GtrPremiumColors.Background),
        ) {
            Image(
                painter = painterResource(R.drawable.gtr_hero_workshop_r35),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                alpha = .72f,
            )
            Column(
                Modifier
                    .align(Alignment.BottomStart)
                    .padding(20.dp),
            ) {
                Text(
                    "NISSAN GTR AUTO",
                    color = GtrPremiumColors.TextPrimary,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    "PARTS • PERFORMANCE • PRECISION",
                    color = GtrPremiumColors.TextSecondary,
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }

        Column(
            Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                if (state.mode == AuthFormMode.SignIn) title else "Create your account",
                color = GtrPremiumColors.TextPrimary,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(
                subtitle ?: if (state.mode == AuthFormMode.SignIn) {
                    "Sign in to sync your garage, orders, wishlist and delivery updates."
                } else {
                    "Create an account for a faster Nissan parts experience."
                },
                color = GtrPremiumColors.TextSecondary,
                style = MaterialTheme.typography.bodyMedium,
            )

            PremiumSurfaceCard {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
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

                    PremiumPrimaryButton(
                        text = when {
                            state.busy && state.mode == AuthFormMode.SignIn -> "Signing in…"
                            state.busy -> "Creating account…"
                            state.mode == AuthFormMode.SignUp -> "Create account"
                            else -> "Sign in"
                        },
                        onClick = {
                            if (state.mode == AuthFormMode.SignIn) vm.signIn() else vm.signUp()
                        },
                        enabled = !state.busy && state.email.isNotBlank() && state.password.isNotBlank(),
                        modifier = Modifier.fillMaxWidth(),
                    )

                    PremiumSecondaryButton(
                        text = if (state.mode == AuthFormMode.SignIn) {
                            "Need an account? Sign up"
                        } else {
                            "Have an account? Sign in"
                        },
                        onClick = {
                            vm.setMode(
                                if (state.mode == AuthFormMode.SignIn) AuthFormMode.SignUp
                                else AuthFormMode.SignIn,
                            )
                        },
                        enabled = !state.busy,
                        modifier = Modifier.fillMaxWidth(),
                    )

                    if (state.mode == AuthFormMode.SignIn) {
                        TextButton(
                            onClick = vm::forgotPassword,
                            enabled = !state.busy,
                        ) {
                            Text("Forgot password?", color = GtrPremiumColors.RedBright)
                        }
                    }

                    if (googleEnabled) {
                        PremiumSecondaryButton(
                            text = "Continue with Google",
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
                            enabled = !state.busy,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }

                    if (allowSkip) {
                        PremiumSecondaryButton(
                            text = "Continue without signing in",
                            onClick = onSkip,
                            enabled = !state.busy,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }

            state.info?.let {
                PremiumMessageBanner(it, PremiumMessageKind.Info)
            }
            (googleError ?: state.error)?.let {
                PremiumMessageBanner(it, PremiumMessageKind.Error)
            }
        }
    }
}

@Composable
private fun FakeSignInPlaceholder(
    allowSkip: Boolean,
    onSkip: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(GtrPremiumColors.Background)
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        PremiumSurfaceCard {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "Nissan GTR Auto",
                    color = GtrPremiumColors.TextPrimary,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    "Live authentication is not configured in this build.",
                    color = GtrPremiumColors.TextSecondary,
                )
                if (allowSkip) {
                    PremiumPrimaryButton(
                        text = "Continue",
                        onClick = onSkip,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

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
            content(null, { }, null)
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
                    .background(GtrPremiumColors.Background)
                    .padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    "Restoring your Nissan GTR Auto session…",
                    color = GtrPremiumColors.TextSecondary,
                )
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
        is AuthGateState.SignedIn -> content(g.email, vm::signOut, vm)
    }
}
