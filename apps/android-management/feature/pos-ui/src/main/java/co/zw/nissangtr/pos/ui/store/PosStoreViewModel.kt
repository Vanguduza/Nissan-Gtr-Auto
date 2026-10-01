package co.zw.nissangtr.pos.ui.store

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import co.zw.nissangtr.pos.domain.state.PosIntent

/** Owns the [PosStore] for the life of the POS screen, across configuration changes. */
class PosStoreViewModel(gateways: PosGateways) : ViewModel() {
    val store = PosStore(viewModelScope, gateways).also { it.dispatch(PosIntent.Start) }

    companion object {
        fun factory(gateways: () -> PosGateways): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = PosStoreViewModel(gateways()) as T
        }
    }
}
