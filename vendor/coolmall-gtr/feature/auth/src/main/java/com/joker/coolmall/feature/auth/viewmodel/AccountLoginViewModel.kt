package com.joker.coolmall.feature.auth.viewmodel

import androidx.lifecycle.viewModelScope
import co.zw.nissangtr.management.gtradapter.GtrStaffAuthAdapter
import com.joker.coolmall.core.common.base.viewmodel.BaseViewModel
import com.joker.coolmall.core.data.state.AppState
import com.joker.coolmall.core.model.entity.Auth
import com.joker.coolmall.core.model.entity.User
import com.joker.coolmall.core.util.storage.MMKVUtils
import com.joker.coolmall.core.util.toast.ToastUtils
import com.joker.coolmall.feature.auth.R
import com.joker.coolmall.navigation.navigateBack
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Staff account login — web [signInWithStaffIdentifier]
 * (emp# | email | phone → resolve_staff_login_email → GoTrue).
 * Fake adapter is default; CoolMall fashion AuthRepository is not used here.
 */
@HiltViewModel
class AccountLoginViewModel @Inject constructor(
    private val appState: AppState,
    private val gtrStaffAuth: GtrStaffAuthAdapter,
) : BaseViewModel() {

    companion object {
        private const val KEY_SAVED_IDENTIFIER = "saved_staff_identifier"
        private const val KEY_SAVED_PASSWORD = "saved_staff_password"
        private const val FAKE_TOKEN_TTL_SEC = 86_400L
    }

    private val _account = MutableStateFlow("")
    val account: StateFlow<String> = _account

    private val _password = MutableStateFlow("")
    val password: StateFlow<String> = _password

    private val _mustChangePassword = MutableStateFlow(false)
    val mustChangePassword: StateFlow<Boolean> = _mustChangePassword

    init {
        loadSavedCredentials()
    }

    val isLoginEnabled = _account.combine(_password) { account, password ->
        account.trim().isNotEmpty() && password.length >= 6
    }

    fun updateAccount(value: String) {
        _account.value = value
    }

    fun updatePassword(value: String) {
        _password.value = value
    }

    fun login() {
        val identifier = _account.value.trim()
        val password = _password.value
        if (identifier.isEmpty()) {
            ToastUtils.showError(R.string.invalid_staff_identifier)
            return
        }
        if (password.length < 6) {
            ToastUtils.showError(R.string.invalid_password)
            return
        }

        viewModelScope.launch {
            val result = gtrStaffAuth.signInWithStaffIdentifier(identifier, password)
            result.fold(
                onSuccess = {
                    val ctx = gtrStaffAuth.loadStaffContext().getOrNull()
                    _mustChangePassword.value = ctx?.mustChangePassword == true
                    loginSuccess(ctx?.userId ?: "staff", ctx?.roles.orEmpty())
                },
                onFailure = {
                    ToastUtils.showError(R.string.staff_sign_in_failed)
                },
            )
        }
    }

    private fun loginSuccess(userId: String, roles: List<String>) {
        viewModelScope.launch {
            saveCredentials(_account.value.trim(), _password.value)
            ToastUtils.showSuccess(R.string.login_success)
            val auth = Auth(
                token = "gtr-fake-access",
                refreshToken = "gtr-fake-refresh",
                expire = FAKE_TOKEN_TTL_SEC,
                refreshExpire = FAKE_TOKEN_TTL_SEC * 7,
                createdAt = System.currentTimeMillis(),
            )
            val user = User(
                id = userId.hashCode().toLong().and(0x7fff_ffffL),
                unionid = userId,
                nickName = roles.firstOrNull()?.uppercase() ?: "STAFF",
                phone = _account.value.trim(),
            )
            appState.updateUserState(auth, user)
            navigateBack()
            navigateBack()
        }
    }

    private fun loadSavedCredentials() {
        val savedId = MMKVUtils.getString(KEY_SAVED_IDENTIFIER, "")
        val savedPassword = MMKVUtils.getString(KEY_SAVED_PASSWORD, "")
        if (savedId.isNotEmpty()) _account.value = savedId
        if (savedPassword.isNotEmpty()) _password.value = savedPassword
    }

    private fun saveCredentials(identifier: String, password: String) {
        MMKVUtils.putString(KEY_SAVED_IDENTIFIER, identifier)
        MMKVUtils.putString(KEY_SAVED_PASSWORD, password)
    }
}
