package co.zw.nissangtr.delivery.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.ReportProblem
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import co.zw.nissangtr.delivery.design.Slopes
import co.zw.nissangtr.delivery.design.SlopesBanner
import co.zw.nissangtr.delivery.design.SlopesDestructiveButton
import co.zw.nissangtr.delivery.design.SlopesPrimaryButton
import co.zw.nissangtr.delivery.design.SlopesTextField
import co.zw.nissangtr.delivery.design.SlopesTone
import co.zw.nissangtr.delivery.design.neuRaised
import co.zw.nissangtr.delivery.rpc.SupabaseRpcClient

@Composable
fun SignInScreen(
    supabase: SupabaseRpcClient?,
    allowSkip: Boolean,
    onSkip: () -> Unit,
    modifier: Modifier = Modifier,
    title: String = "Driver sign in",
    subtitle: String = "Use your staff email",
    sessionViewModel: AuthSessionViewModel? = null,
) {
    if (supabase == null) {
        // Fake / no Live keys — local demo session only (no GoTrue).
        AuthBackdrop(modifier, title = title, subtitle = subtitle) {
            SlopesBanner(
                "Not connected to the live service. You can look around with sample deliveries.",
                tone = SlopesTone.Warning,
                icon = Icons.Filled.ReportProblem,
                inset = 0.dp,
            )
            Spacer(Modifier.height(16.dp))
            if (allowSkip) {
                SlopesPrimaryButton("Continue with sample jobs", onSkip, icon = Icons.AutoMirrored.Filled.Login)
            } else {
                Text("Sign-in unavailable", style = MaterialTheme.typography.bodyMedium, color = Slopes.colors.danger)
            }
        }
        return
    }

    val vm = sessionViewModel
        ?: viewModel(factory = AuthSessionViewModel.factory(supabase))
    val state by vm.signIn.collectAsState()

    SignInForm(
        email = state.email,
        password = state.password,
        busy = state.busy,
        error = state.error,
        onEmailChange = vm::onEmailChange,
        onPasswordChange = vm::onPasswordChange,
        onSignIn = vm::signIn,
        modifier = modifier,
        title = title,
        subtitle = subtitle,
    )
}

/** Stateless sign-in form (also rendered by screenshot tests). */
@Composable
fun SignInForm(
    email: String,
    password: String,
    busy: Boolean,
    error: String?,
    onEmailChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onSignIn: () -> Unit,
    modifier: Modifier = Modifier,
    title: String = "Driver sign in",
    subtitle: String = "Use your staff email",
) {
    AuthBackdrop(modifier, title = title, subtitle = subtitle) {
        SlopesTextField(
            value = email,
            onValueChange = onEmailChange,
            label = "Email",
            placeholder = "you@nissangtrauto.co.zw",
            enabled = !busy,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
        )
        Spacer(Modifier.height(14.dp))
        SlopesTextField(
            value = password,
            onValueChange = onPasswordChange,
            label = "Password",
            placeholder = "Password",
            password = true,
            enabled = !busy,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        )
        Spacer(Modifier.height(20.dp))
        SlopesPrimaryButton(
            label = if (busy) "Signing in…" else "Sign in",
            onClick = onSignIn,
            enabled = !busy && email.isNotBlank() && password.isNotBlank(),
            icon = Icons.Filled.Lock,
        )
        error?.let {
            Spacer(Modifier.height(14.dp))
            SlopesBanner(it, tone = SlopesTone.Danger, icon = Icons.Filled.ReportProblem, inset = 0.dp)
        }
    }
}

/**
 * Blue sky header with the app badge, and a white sheet holding the form — the Slopes
 * onboarding feel (bright header, card sheet, single blue action).
 */
@Composable
private fun AuthBackdrop(
    modifier: Modifier,
    title: String,
    subtitle: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    val c = Slopes.colors
    // Brand steel header fading into the canvas (brand-tokens: steel #12151C, steelLift #1E2430).
    val sky = Brush.verticalGradient(
        0f to Color(0xFF0A0C0E),
        0.28f to Color(0xFF1E2430),
        0.5f to c.background,
    )
    Box(
        modifier
            .fillMaxSize()
            .background(c.background)
            .background(sky),
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(56.dp))
            Box(
                Modifier
                    .size(84.dp)
                    .shadow(18.dp, RoundedCornerShape(24.dp), spotColor = c.accent)
                    .clip(RoundedCornerShape(24.dp))
                    .background(c.accent),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.LocalShipping, contentDescription = null, tint = Color.White, modifier = Modifier.size(46.dp))
            }
            Spacer(Modifier.height(18.dp))
            Text("GTR Delivery", style = MaterialTheme.typography.displaySmall, color = Color.White)
            Text(
                "Nissan GTR Auto · drivers",
                style = MaterialTheme.typography.bodyLarge,
                color = Color(0xFFC0C5CE),
            )
            Spacer(Modifier.height(36.dp))
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .neuRaised(cornerRadius = 24.dp, distance = 6.dp, blur = 16.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(c.surface)
                    .padding(20.dp),
            ) {
                Text(title, style = MaterialTheme.typography.headlineSmall, color = c.label)
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = c.secondaryLabel)
                Spacer(Modifier.height(20.dp))
                content()
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

/** Centered status card used while restoring a session or for a wrong role. */
@Composable
private fun AuthStatus(
    title: String,
    body: String,
    busy: Boolean = false,
    action: (@Composable () -> Unit)? = null,
) {
    AuthBackdrop(Modifier, title = title, subtitle = body) {
        if (busy) {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Slopes.colors.accent, strokeWidth = 3.dp, modifier = Modifier.size(28.dp))
            }
        }
        action?.invoke()
    }
}

/**
 * Live: block until Authenticated + driver|admin role.
 * Fake: local skip session; Sign out returns to this gate.
 */
@Composable
fun AuthGate(
    liveRpc: Boolean,
    supabase: SupabaseRpcClient?,
    allowFakeSkip: Boolean = true,
    content: @Composable (email: String?, onSignOut: () -> Unit) -> Unit,
) {
    if (!liveRpc || supabase == null) {
        var skipped by remember { mutableStateOf(false) }
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
        is AuthGateState.Checking -> AuthStatus(title = "Signing you in", body = "Restoring your session…", busy = true)
        is AuthGateState.NeedsSignIn -> {
            SignInScreen(
                supabase = supabase,
                allowSkip = false,
                onSkip = {},
                sessionViewModel = vm,
            )
        }
        is AuthGateState.WrongRole -> AuthStatus(
            title = "Driver access needed",
            body = "This account is not set up as a driver. Ask dispatch to add the driver role.",
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (g.roles.isNotEmpty()) {
                    Text(
                        "Roles on this account: ${g.roles.joinToString()}",
                        style = MaterialTheme.typography.bodySmall,
                        color = Slopes.colors.secondaryLabel,
                        textAlign = TextAlign.Start,
                    )
                }
                SlopesDestructiveButton("Sign out", vm::signOut, icon = Icons.AutoMirrored.Filled.Logout)
            }
        }
        is AuthGateState.SignedIn -> content(g.email, vm::signOut)
    }
}
