package com.joker.coolmall.feature.main.view

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.joker.coolmall.core.designsystem.theme.SpacePaddingMedium
import com.joker.coolmall.core.ui.component.appbar.CenterTopAppBar
import com.joker.coolmall.core.ui.component.button.AppButton
import com.joker.coolmall.feature.main.R
import com.joker.coolmall.feature.main.component.CommonScaffold
import com.joker.coolmall.feature.main.viewmodel.StaffAccountViewModel
import com.joker.coolmall.navigation.auth.AuthNavigator

@Composable
internal fun StaffAccountRoute(
    viewModel: StaffAccountViewModel = hiltViewModel(),
) {
    val roles by viewModel.rolesLabel.collectAsStateWithLifecycle()
    val mustChange by viewModel.mustChange.collectAsStateWithLifecycle()
    val newPassword by viewModel.newPassword.collectAsStateWithLifecycle()
    val confirmPassword by viewModel.confirmPassword.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()

    StaffAccountScreen(
        rolesLabel = roles,
        mustChange = mustChange,
        newPassword = newPassword,
        confirmPassword = confirmPassword,
        message = message,
        onNewPasswordChange = viewModel::updateNewPassword,
        onConfirmPasswordChange = viewModel::updateConfirmPassword,
        onChangePassword = viewModel::changePassword,
        onSignOut = {
            viewModel.signOut()
            AuthNavigator.toLogin()
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun StaffAccountScreen(
    rolesLabel: String,
    mustChange: Boolean,
    newPassword: String,
    confirmPassword: String,
    message: String?,
    onNewPasswordChange: (String) -> Unit,
    onConfirmPasswordChange: (String) -> Unit,
    onChangePassword: () -> Unit,
    onSignOut: () -> Unit,
) {
    CommonScaffold(
        topBar = {
            CenterTopAppBar(R.string.staff_account_title, showBackIcon = false)
        },
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(SpacePaddingMedium),
        ) {
            Text(
                text = stringResource(R.string.staff_roles_fmt, rolesLabel.ifBlank { "—" }),
                style = MaterialTheme.typography.titleMedium,
            )
            if (mustChange) {
                Text(
                    text = stringResource(R.string.staff_must_change_password),
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.staff_change_password_title),
                style = MaterialTheme.typography.titleSmall,
            )
            OutlinedTextField(
                value = newPassword,
                onValueChange = onNewPasswordChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                singleLine = true,
                label = { Text(stringResource(R.string.staff_new_password)) },
                visualTransformation = PasswordVisualTransformation(),
            )
            OutlinedTextField(
                value = confirmPassword,
                onValueChange = onConfirmPasswordChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                singleLine = true,
                label = { Text(stringResource(R.string.staff_confirm_password)) },
                visualTransformation = PasswordVisualTransformation(),
            )
            Spacer(modifier = Modifier.height(12.dp))
            AppButton(
                text = stringResource(R.string.staff_change_password_action),
                onClick = onChangePassword,
            )
            if (message != null) {
                Text(
                    text = message,
                    modifier = Modifier.padding(top = 8.dp),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Spacer(modifier = Modifier.height(24.dp))
            AppButton(
                text = stringResource(R.string.staff_sign_out),
                onClick = onSignOut,
            )
        }
    }
}
