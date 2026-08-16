package com.joker.coolmall.feature.main.view

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
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
import co.zw.nissangtr.management.gtradapter.GtrPayslipHistoryRow
import co.zw.nissangtr.management.gtradapter.GtrStaffProfile
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
    val profile by viewModel.profile.collectAsStateWithLifecycle()
    val editPhone by viewModel.editPhone.collectAsStateWithLifecycle()
    val editEmail by viewModel.editEmail.collectAsStateWithLifecycle()
    val editAddress by viewModel.editAddress.collectAsStateWithLifecycle()
    val payslips by viewModel.payslips.collectAsStateWithLifecycle()
    val newPassword by viewModel.newPassword.collectAsStateWithLifecycle()
    val confirmPassword by viewModel.confirmPassword.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()

    StaffAccountScreen(
        rolesLabel = roles,
        mustChange = mustChange,
        profile = profile,
        editPhone = editPhone,
        editEmail = editEmail,
        editAddress = editAddress,
        payslips = payslips,
        newPassword = newPassword,
        confirmPassword = confirmPassword,
        message = message,
        onPhoneChange = viewModel::updateEditPhone,
        onEmailChange = viewModel::updateEditEmail,
        onAddressChange = viewModel::updateEditAddress,
        onSaveProfile = viewModel::saveProfile,
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
    profile: GtrStaffProfile?,
    editPhone: String,
    editEmail: String,
    editAddress: String,
    payslips: List<GtrPayslipHistoryRow>,
    newPassword: String,
    confirmPassword: String,
    message: String?,
    onPhoneChange: (String) -> Unit,
    onEmailChange: (String) -> Unit,
    onAddressChange: (String) -> Unit,
    onSaveProfile: () -> Unit,
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
                .padding(SpacePaddingMedium)
                .verticalScroll(rememberScrollState()),
        ) {
            Text(
                text = stringResource(R.string.staff_roles_fmt, rolesLabel.ifBlank { "—" }),
                style = MaterialTheme.typography.titleMedium,
            )
            if (profile != null) {
                Text(
                    text = stringResource(
                        R.string.staff_emp_fmt,
                        profile.employeeCode ?: "—",
                        profile.fullName ?: "—",
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            if (mustChange) {
                Text(
                    text = stringResource(R.string.staff_must_change_password),
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.staff_profile_title),
                style = MaterialTheme.typography.titleSmall,
            )
            OutlinedTextField(
                value = editEmail,
                onValueChange = onEmailChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                singleLine = true,
                label = { Text(stringResource(R.string.staff_email)) },
            )
            OutlinedTextField(
                value = editPhone,
                onValueChange = onPhoneChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                singleLine = true,
                label = { Text(stringResource(R.string.staff_phone)) },
            )
            OutlinedTextField(
                value = editAddress,
                onValueChange = onAddressChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                singleLine = true,
                label = { Text(stringResource(R.string.staff_address)) },
            )
            Spacer(modifier = Modifier.height(12.dp))
            AppButton(
                text = stringResource(R.string.staff_save_profile),
                onClick = onSaveProfile,
            )

            Spacer(modifier = Modifier.height(20.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(12.dp))
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

            if (payslips.isNotEmpty()) {
                Spacer(modifier = Modifier.height(20.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = stringResource(R.string.staff_payslips_title),
                    style = MaterialTheme.typography.titleSmall,
                )
                payslips.forEach { row ->
                    Text(
                        text = stringResource(
                            R.string.staff_payslip_row_fmt,
                            row.periodEnd.ifBlank { row.periodStart },
                            row.currency,
                            row.netAmount,
                            if (row.funded) "funded" else "unfunded",
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
            }

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
