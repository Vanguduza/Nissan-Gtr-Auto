package com.joker.coolmall.feature.main.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.zw.nissangtr.management.gtradapter.GtrPasswordAdapter
import co.zw.nissangtr.management.gtradapter.GtrStaffAuthAdapter
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
    private val session: GtrStaffSession,
    private val appState: AppState,
) : ViewModel() {

    private val _rolesLabel = MutableStateFlow("")
    val rolesLabel: StateFlow<String> = _rolesLabel.asStateFlow()

    private val _mustChange = MutableStateFlow(false)
    val mustChange: StateFlow<Boolean> = _mustChange.asStateFlow()

    private val _newPassword = MutableStateFlow("")
    val newPassword: StateFlow<String> = _newPassword.asStateFlow()

    private val _confirmPassword = MutableStateFlow("")
    val confirmPassword: StateFlow<String> = _confirmPassword.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        val ctx = session.context
        _rolesLabel.value = ctx?.roles?.joinToString(", ").orEmpty()
        _mustChange.value = ctx?.mustChangePassword == true
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
