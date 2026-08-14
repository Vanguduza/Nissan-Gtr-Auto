package co.zw.nissangtr.management.crm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import co.zw.nissangtr.management.rpc.RpcClient
import co.zw.nissangtr.management.rpc.StaffProductImage
import co.zw.nissangtr.management.rpc.StaffProductPageRow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ProductPagesUiState(
    val query: String = "",
    val rows: List<StaffProductPageRow> = emptyList(),
    val selected: StaffProductPageRow? = null,
    val images: List<StaffProductImage> = emptyList(),
    val priceText: String = "",
    val discountKind: String = "none",
    val discountValueText: String = "0",
    val discountDescription: String = "",
    /** Manual Storage path register (when gallery upload not used). */
    val imagePathText: String = "",
    val busy: Boolean = false,
    val message: String? = null,
    val error: String? = null,
)

class ProductPagesViewModel(
    private val rpc: RpcClient,
) : ViewModel() {
    private val _state = MutableStateFlow(ProductPagesUiState())
    val state: StateFlow<ProductPagesUiState> = _state.asStateFlow()

    init {
        refresh()
    }

    fun onQueryChange(v: String) = _state.update { it.copy(query = v, error = null) }
    fun onPriceChange(v: String) = _state.update { it.copy(priceText = v, error = null) }
    fun onDiscountKindChange(v: String) = _state.update { it.copy(discountKind = v, error = null) }
    fun onDiscountValueChange(v: String) = _state.update { it.copy(discountValueText = v, error = null) }
    fun onDiscountDescriptionChange(v: String) =
        _state.update { it.copy(discountDescription = v, error = null) }
    fun onImagePathChange(v: String) = _state.update { it.copy(imagePathText = v, error = null) }

    fun refresh() {
        viewModelScope.launch {
            _state.update { it.copy(busy = true, error = null) }
            try {
                val rows = rpc.listStaffProductPages(_state.value.query, 100)
                _state.update { it.copy(busy = false, rows = rows) }
            } catch (e: Exception) {
                _state.update { it.copy(busy = false, error = e.message ?: "list failed") }
            }
        }
    }

    fun select(row: StaffProductPageRow) {
        viewModelScope.launch {
            _state.update {
                it.copy(
                    selected = row,
                    priceText = row.unitPrice?.toString().orEmpty(),
                    discountKind = row.discountKind,
                    discountValueText = row.discountValue.toString(),
                    discountDescription = row.discountDescription.orEmpty(),
                    imagePathText = row.primaryImagePath.orEmpty(),
                    error = null,
                    message = null,
                    busy = true,
                )
            }
            try {
                val images = rpc.listStaffProductImages(row.stockItemId)
                _state.update { it.copy(busy = false, images = images) }
            } catch (e: Exception) {
                _state.update {
                    it.copy(busy = false, images = emptyList(), error = e.message ?: "images failed")
                }
            }
        }
    }

    fun save() {
        val sel = _state.value.selected ?: return
        val price = _state.value.priceText.toDoubleOrNull()
        if (price == null || price < 0) {
            _state.update { it.copy(error = "Enter a valid unit price") }
            return
        }
        val discountValue = _state.value.discountValueText.toDoubleOrNull() ?: 0.0
        viewModelScope.launch {
            _state.update { it.copy(busy = true, error = null, message = null) }
            try {
                rpc.upsertStaffProductPage(
                    stockItemId = sel.stockItemId,
                    unitPrice = price,
                    discountKind = _state.value.discountKind,
                    discountValue = discountValue,
                    discountDescription = _state.value.discountDescription.trim().ifEmpty { null },
                )
                _state.update { it.copy(busy = false, message = "Saved price / discount") }
                refresh()
            } catch (e: Exception) {
                _state.update { it.copy(busy = false, error = e.message ?: "save failed") }
            }
        }
    }

    fun registerImagePathAsPrimary() {
        val sel = _state.value.selected ?: return
        val path = _state.value.imagePathText.trim()
        if (path.isEmpty()) {
            _state.update { it.copy(error = "Enter a Storage path under product-images") }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(busy = true, error = null, message = null) }
            try {
                rpc.registerStaffProductImage(sel.stockItemId, path, asPrimary = true)
                _state.update { it.copy(busy = false, message = "Primary image registered") }
                select(sel)
            } catch (e: Exception) {
                _state.update { it.copy(busy = false, error = e.message ?: "register failed") }
            }
        }
    }

    /** Gallery / file pick → Storage upload when Live; Fake stores a synthetic path. */
    fun uploadLocalImage(localPath: String, mimeType: String) {
        val sel = _state.value.selected ?: return
        viewModelScope.launch {
            _state.update { it.copy(busy = true, error = null, message = null) }
            try {
                rpc.uploadStaffProductImage(
                    stockItemId = sel.stockItemId,
                    localFilePath = localPath,
                    mimeType = mimeType,
                    asPrimary = true,
                )
                _state.update { it.copy(busy = false, message = "Image uploaded as primary") }
                select(sel)
            } catch (e: Exception) {
                _state.update { it.copy(busy = false, error = e.message ?: "upload failed") }
            }
        }
    }

    fun setPrimary(imageId: String) {
        val sel = _state.value.selected ?: return
        viewModelScope.launch {
            _state.update { it.copy(busy = true, error = null) }
            try {
                rpc.setStaffProductPrimaryImage(imageId)
                _state.update { it.copy(busy = false, message = "Primary image updated") }
                select(sel)
            } catch (e: Exception) {
                _state.update { it.copy(busy = false, error = e.message ?: "set primary failed") }
            }
        }
    }

    companion object {
        fun factory(rpc: RpcClient): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    ProductPagesViewModel(rpc) as T
            }
    }
}
