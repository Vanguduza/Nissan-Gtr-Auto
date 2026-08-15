package com.joker.coolmall.feature.main.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.zw.nissangtr.management.gtradapter.GtrStaffAuthAdapter
import co.zw.nissangtr.management.gtradapter.GtrStaffHubAdapter
import co.zw.nissangtr.management.gtradapter.GtrStaffSession
import co.zw.nissangtr.management.gtradapter.StaffNavModule
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class StaffHubViewModel @Inject constructor(
    private val auth: GtrStaffAuthAdapter,
    private val hub: GtrStaffHubAdapter,
    private val session: GtrStaffSession,
) : ViewModel() {

    private val _modules = MutableStateFlow<List<StaffNavModule>>(emptyList())
    val modules: StateFlow<List<StaffNavModule>> = _modules.asStateFlow()

    private val _rolesLabel = MutableStateFlow("")
    val rolesLabel: StateFlow<String> = _rolesLabel.asStateFlow()

    private val _fakeMode = MutableStateFlow(auth.isFakeMode())
    val fakeMode: StateFlow<Boolean> = _fakeMode.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            val ctx = session.context
                ?: auth.loadStaffContext().getOrNull()
                ?: return@launch
            _rolesLabel.value = ctx.roles.joinToString(", ")
            _modules.value = hub.filterModules(ctx)
            _fakeMode.value = auth.isFakeMode()
        }
    }
}
