package co.zw.nissangtr.pos.ui.store

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import co.zw.nissangtr.pos.domain.state.PosIntent

/**
 * Owns the [PosStore] for the life of the POS screen, across configuration changes. [onCleared]
 * releases resources the gateways hold (the encrypted offline store) with the store, not the UI.
 */
class PosStoreViewModel(gateways: PosGateways, private val release: () -> Unit = {}) : ViewModel() {
    val store = PosStore(viewModelScope, gateways).also { it.dispatch(PosIntent.Start) }

    override fun onCleared() = release()

    companion object {
        fun factory(gateways: () -> PosGateways): ViewModelProvider.Factory = owning { gateways() to {} }

        /** [build] returns the gateways and what to release when the view model is cleared. */
        fun owning(build: () -> Pair<PosGateways, () -> Unit>): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                val (gateways, release) = build()
                return PosStoreViewModel(gateways, release) as T
            }
        }
    }
}
