package com.joker.coolmall.feature.main.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.zw.nissangtr.management.gtradapter.GtrMyAccountAdapter
import co.zw.nissangtr.management.gtradapter.GtrPasswordAdapter
import co.zw.nissangtr.management.gtradapter.GtrPayslipHistoryRow
import co.zw.nissangtr.management.gtradapter.GtrStaffAuthAdapter
import co.zw.nissangtr.management.gtradapter.GtrStaffProfile
import co.zw.nissangtr.management.gtradapter.GtrStaffSession
import com.joker.coolmall.core.data.state.AppState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class StaffAccountViewModel @Inject constructor(
    private val auth: GtrStaffAuthAdapter,
    private val passwordAdapter: GtrPasswordAdapter,
    private val myAccount: GtrMyAccountAdapter,
    private val session: GtrStaffSession,
    private val appState: AppState,
) : ViewModel() {

    private val _rolesLabel = MutableStateFlow("")
    val rolesLabel: StateFlow<String> = _rolesLabel.asStateFlow()

    private val _mustChange = MutableStateFlow(false)
    val mustChange: StateFlow<Boolean> = _mustChange.asStateFlow()

    private val _profile = MutableStateFlow<GtrStaffProfile?>(null)
    val profile: StateFlow<GtrStaffProfile?> = _profile.asStateFlow()

    private val _editPhone = MutableStateFlow("")
    val editPhone: StateFlow<String> = _editPhone.asStateFlow()

    private val _editEmail = MutableStateFlow("")
    val editEmail: StateFlow<String> = _editEmail.asStateFlow()

    private val _editAddress = MutableStateFlow("")
    val editAddress: StateFlow<String> = _editAddress.asStateFlow()

    private val _payslips = MutableStateFlow<List<GtrPayslipHistoryRow>>(emptyList())
    val payslips: StateFlow<List<GtrPayslipHistoryRow>> = _payslips.asStateFlow()

    private val _newPassword = MutableStateFlow("")
    val newPassword: StateFlow<String> = _newPassword.asStateFlow()

    private val _confirmPassword = MutableStateFlow("")
    val confirmPassword: StateFlow<String> = _confirmPassword.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    init {
        refresh()
        loadAccount()
    }

    fun refresh() {
        val ctx = session.context
        _rolesLabel.value = ctx?.roles?.joinToString(", ").orEmpty()
        _mustChange.value = ctx?.mustChangePassword == true
    }

    fun loadAccount() {
        viewModelScope.launch {
            myAccount.getMyStaffProfile().onSuccess { p ->
                _profile.value = p
                _editPhone.value = p.phoneE164.orEmpty()
                _editEmail.value = p.email.orEmpty()
                _editAddress.value = p.address.orEmpty()
                if (p.staffRoles.isNotEmpty()) {
                    _rolesLabel.value = p.staffRoles.joinToString(", ")
                }
            }
            myAccount.listMyPayslipHistory().onSuccess { _payslips.value = it }
        }
    }

    fun updateEditPhone(value: String) {
        _editPhone.value = value
    }

    fun updateEditEmail(value: String) {
        _editEmail.value = value
    }

    fun updateEditAddress(value: String) {
        _editAddress.value = value
    }

    fun saveProfile() {
        viewModelScope.launch {
            myAccount.updateMyStaffProfile(
                phoneE164 = _editPhone.value,
                address = _editAddress.value,
                email = _editEmail.value,
                syncAuthEmail = false,
            ).fold(
                onSuccess = {
                    _profile.value = it
                    _message.value = "Profile saved"
                },
                onFailure = { _message.value = it.message ?: "Save failed" },
            )
        }
    }

    fun updateNewPassword(value: String) {
        _newPassword.value = value
    }

    fun updateConfirmPassword(value: String) {
        _confirmPassword.value = value
    }

    fun changePassword() {
        viewModelScope.launch {
            if (_newPassword.value != _confirmPassword.value) {
                _message.value = "Passwords do not match"
                return@launch
            }
            passwordAdapter.changePassword(_newPassword.value).fold(
                onSuccess = {
                    _message.value = "Password updated"
                    _newPassword.value = ""
                    _confirmPassword.value = ""
                    refresh()
                },
                onFailure = { _message.value = it.message ?: "Change password failed" },
            )
        }
    }

    fun signOut() {
        viewModelScope.launch {
            auth.signOut()
            appState.logout()
        }
    }
}
