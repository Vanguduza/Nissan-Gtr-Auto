package co.zw.nissangtr.customer.reviews

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import co.zw.nissangtr.bridges.podcamera.CameraPermissionStatus
import co.zw.nissangtr.bridges.podcamera.PodCameraBridge
import co.zw.nissangtr.customer.rpc.ProductReview
import co.zw.nissangtr.customer.rpc.ProductReviewStats
import co.zw.nissangtr.customer.rpc.RpcClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ReviewsUiState(
    val ownReviews: List<ProductReview> = emptyList(),
    val approved: List<ProductReview> = emptyList(),
    val stats: ProductReviewStats? = null,
    val oem: String = "",
    val stockItemId: String? = null,
    val rating: Int = 5,
    val body: String = "",
    val lastSubmittedId: String? = null,
    val busy: Boolean = false,
    val message: String? = null,
    val error: String? = null,
)

class ReviewsViewModel(
    private val rpc: RpcClient,
    private val camera: PodCameraBridge?,
    initialOem: String = "",
    initialStockItemId: String? = null,
) : ViewModel() {
    private val _state = MutableStateFlow(
        ReviewsUiState(
            oem = initialOem.trim(),
            stockItemId = initialStockItemId,
        ),
    )
    val state: StateFlow<ReviewsUiState> = _state.asStateFlow()

    val cameraAvailable: Boolean get() = camera != null

    init {
        refreshOwn()
        if (initialOem.isNotBlank()) {
            loadPdp()
        }
    }

    fun onRatingChange(v: Int) = _state.update { it.copy(rating = v.coerceIn(1, 5)) }
    fun onBodyChange(v: String) = _state.update { it.copy(body = v) }

    fun refreshOwn() {
        viewModelScope.launch {
            _state.update { it.copy(busy = true, error = null) }
            try {
                val list = rpc.listOwnReviews()
                _state.update { it.copy(busy = false, ownReviews = list) }
            } catch (e: Exception) {
                _state.update { it.copy(busy = false, error = e.message ?: "list failed") }
            }
        }
    }

    fun loadPdp() {
        val oem = _state.value.oem.trim()
        if (oem.isEmpty()) return
        viewModelScope.launch {
            _state.update { it.copy(busy = true, error = null, message = null) }
            try {
                val stats = rpc.getProductReviewStats(
                    stockItemId = _state.value.stockItemId,
                    oem = oem,
                )
                val approved = rpc.listApprovedReviews(oem)
                _state.update {
                    it.copy(busy = false, stats = stats, approved = approved)
                }
            } catch (e: Exception) {
                _state.update { it.copy(busy = false, error = e.message ?: "PDP load failed") }
            }
        }
    }

    fun submit() {
        val oem = _state.value.oem.trim()
        if (oem.isEmpty()) {
            _state.update { it.copy(error = "OEM required") }
            return
        }
        val s = _state.value
        viewModelScope.launch {
            _state.update { it.copy(busy = true, error = null, message = null) }
            try {
                val id = rpc.submitCustomerProductReview(
                    rating = s.rating,
                    body = s.body,
                    stockItemId = s.stockItemId,
                    oem = oem,
                )
                val own = rpc.listOwnReviews()
                loadPdp()
                _state.update {
                    it.copy(
                        busy = false,
                        lastSubmittedId = id,
                        body = "",
                        ownReviews = own,
                        message = "Review submitted — pending approval.",
                    )
                }
            } catch (e: Exception) {
                _state.update { it.copy(busy = false, error = e.message ?: "submit failed") }
            }
        }
    }

    fun attachPhotoFromPath(localPath: String, mimeType: String = "image/jpeg") {
        val reviewId = _state.value.lastSubmittedId
        if (reviewId.isNullOrBlank()) {
            _state.update { it.copy(error = "Submit a review first") }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(busy = true, error = null, message = null) }
            try {
                val photoId = rpc.uploadReviewPhoto(
                    reviewId = reviewId,
                    localFilePath = localPath,
                    mimeType = mimeType,
                    sortOrder = 0,
                )
                _state.update {
                    it.copy(busy = false, message = "Photo attached (${photoId.take(8)}…)")
                }
            } catch (e: Exception) {
                _state.update { it.copy(busy = false, error = e.message ?: "upload failed") }
            }
        }
    }

    fun captureWithBridge() {
        val bridge = camera
        if (bridge == null) {
            _state.update { it.copy(error = "Camera bridge not attached — use gallery") }
            return
        }
        val reviewId = _state.value.lastSubmittedId
        if (reviewId.isNullOrBlank()) {
            _state.update { it.copy(error = "Submit a review first") }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(busy = true, error = null, message = null) }
            try {
                val perm = bridge.requestCameraPermission()
                if (perm != CameraPermissionStatus.GRANTED) {
                    _state.update {
                        it.copy(busy = false, error = "Camera permission required ($perm)")
                    }
                    return@launch
                }
                val result = bridge.capturePhoto()
                val photoId = rpc.uploadReviewPhoto(
                    reviewId = reviewId,
                    localFilePath = result.localPath,
                    mimeType = result.mimeType,
                    sortOrder = 0,
                )
                _state.update {
                    it.copy(
                        busy = false,
                        message = "Bridge camera photo attached (${photoId.take(8)}…)",
                    )
                }
            } catch (e: Exception) {
                _state.update {
                    it.copy(busy = false, error = e.message ?: "camera capture failed")
                }
            }
        }
    }

    companion object {
        fun factory(
            rpc: RpcClient,
            camera: PodCameraBridge?,
            initialOem: String = "",
            initialStockItemId: String? = null,
        ): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    ReviewsViewModel(rpc, camera, initialOem, initialStockItemId) as T
            }
    }
}
