package co.zw.nissangtr.delivery.pod

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import co.zw.nissangtr.bridges.podcamera.CameraPermissionStatus
import co.zw.nissangtr.bridges.podcamera.PodCameraBridge
import co.zw.nissangtr.bridges.podsignature.PodCaptureResult
import co.zw.nissangtr.bridges.podsignature.PodSignatureBridge
import co.zw.nissangtr.bridges.podsignature.PodSignatureOptions
import co.zw.nissangtr.delivery.rpc.RpcClient
import co.zw.nissangtr.delivery.rpc.RpcNames
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PodUiState(
    val jobId: String = "",
    val photoLocalPath: String? = null,
    val photoMime: String = "image/jpeg",
    val signatureLocalPath: String? = null,
    val signatureMime: String = "image/png",
    val otpCode: String = "",
    val notes: String = "",
    val otpGenerated: Boolean = false,
    val otpVerified: Boolean = false,
    val queuedCount: Int = 0,
    val busy: Boolean = false,
    val message: String? = null,
    val error: String? = null,
    val completed: Boolean = false,
)

/**
 * POD capture via Bridge-First camera + signature; OTP generate/verify; offline queue.
 */
class PodViewModel(
    private val rpc: RpcClient,
    private val camera: PodCameraBridge,
    private val signature: PodSignatureBridge,
    private val appContext: Context,
    private val podQueue: OfflinePodQueue = OfflinePodQueue(appContext),
) : ViewModel() {
    private val _state = MutableStateFlow(PodUiState(queuedCount = podQueue.size()))
    val state: StateFlow<PodUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            while (true) {
                delay(20_000L)
                if (isOnline()) flushQueue()
            }
        }
    }

    fun bindJob(jobId: String) {
        _state.update {
            it.copy(
                jobId = jobId,
                photoLocalPath = null,
                photoMime = "image/jpeg",
                signatureLocalPath = null,
                signatureMime = "image/png",
                otpCode = "",
                otpGenerated = false,
                otpVerified = false,
                completed = false,
                error = null,
                message = null,
            )
        }
    }

    fun onOtpChange(v: String) = _state.update {
        it.copy(otpCode = v.filter { c -> c.isDigit() }.take(8), otpVerified = false, error = null)
    }

    fun onNotesChange(v: String) = _state.update { it.copy(notes = v) }

    fun capturePhoto() {
        val jobId = _state.value.jobId
        if (jobId.isBlank()) {
            _state.update { it.copy(error = "Job required") }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(busy = true, error = null) }
            try {
                val perm = camera.requestCameraPermission()
                if (perm != CameraPermissionStatus.GRANTED) {
                    _state.update {
                        it.copy(busy = false, error = "Camera permission required ($perm)")
                    }
                    return@launch
                }
                val result = camera.capturePhoto()
                _state.update {
                    it.copy(
                        busy = false,
                        photoLocalPath = result.localPath,
                        photoMime = result.mimeType.ifBlank { "image/jpeg" },
                        message = "Evidence photo captured",
                    )
                }
            } catch (e: Exception) {
                _state.update {
                    it.copy(busy = false, error = e.message ?: "photo capture failed")
                }
            }
        }
    }

    /** Accept a PNG from inline Compose Canvas pad (preferred UX). */
    fun acceptSignature(result: PodCaptureResult) {
        if (_state.value.jobId.isBlank()) {
            _state.update { it.copy(error = "Job required") }
            return
        }
        _state.update {
            it.copy(
                signatureLocalPath = result.localPath,
                signatureMime = result.mimeType.ifBlank { "image/png" },
                message = "Signature captured",
                error = null,
            )
        }
    }

    fun clearSignature() {
        _state.update { it.copy(signatureLocalPath = null, message = "Signature cleared") }
    }

    /** Full-screen bridge Activity fallback when inline pad is not used. */
    fun captureSignatureFullscreen() {
        if (_state.value.jobId.isBlank()) {
            _state.update { it.copy(error = "Job required") }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(busy = true, error = null) }
            try {
                val result = signature.captureSignature(
                    PodSignatureOptions(title = "Customer signature"),
                )
                _state.update {
                    it.copy(
                        busy = false,
                        signatureLocalPath = result.localPath,
                        signatureMime = result.mimeType.ifBlank { "image/png" },
                        message = "Signature captured",
                    )
                }
            } catch (e: Exception) {
                _state.update {
                    it.copy(busy = false, error = e.message ?: "signature capture failed")
                }
            }
        }
    }

    fun generateOtp() {
        val jobId = _state.value.jobId
        if (jobId.isBlank()) return
        viewModelScope.launch {
            _state.update { it.copy(busy = true, error = null) }
            try {
                rpc.generateDeliveryPodOtp(jobId)
                _state.update {
                    it.copy(
                        busy = false,
                        otpGenerated = true,
                        message = "OTP sent — enter the customer code",
                    )
                }
            } catch (e: Exception) {
                _state.update {
                    it.copy(busy = false, error = e.message ?: "OTP generate failed")
                }
            }
        }
    }

    fun verifyOtp() {
        val jobId = _state.value.jobId
        val code = _state.value.otpCode
        if (jobId.isBlank() || code.isBlank()) {
            _state.update { it.copy(error = "OTP code required") }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(busy = true, error = null) }
            try {
                val ok = rpc.verifyDeliveryPodOtp(jobId, code)
                _state.update {
                    it.copy(
                        busy = false,
                        otpVerified = ok,
                        message = if (ok) "OTP verified" else "OTP invalid",
                        error = if (ok) null else "OTP invalid",
                    )
                }
            } catch (e: Exception) {
                _state.update {
                    it.copy(busy = false, error = e.message ?: "OTP verify failed")
                }
            }
        }
    }

    fun submitPod() {
        val s = _state.value
        val jobId = s.jobId
        val photo = s.photoLocalPath
        val sig = s.signatureLocalPath
        val otp = s.otpCode
        if (jobId.isBlank()) {
            _state.update { it.copy(error = "Job required") }
            return
        }
        val blocked = PodEvidenceGate.blockingReason(photo, sig, otp, s.otpVerified)
        if (blocked != null) {
            _state.update { it.copy(error = blocked) }
            return
        }
        check(photo != null && sig != null)
        val photoKey = deliveryPodPhotoObjectKey(jobId)
        val sigKey = deliveryPodSignatureObjectKey(jobId)
        val photoMime = s.photoMime.ifBlank { "image/jpeg" }
        val sigMime = s.signatureMime.ifBlank { "image/png" }
        viewModelScope.launch {
            _state.update { it.copy(busy = true, error = null) }
            if (!isOnline()) {
                podQueue.enqueue(
                    QueuedPodSubmission(
                        deliveryJobId = jobId,
                        localPhotoPath = photo,
                        photoMime = photoMime,
                        localSignaturePath = sig,
                        signatureMime = sigMime,
                        otpCode = otp,
                        notes = s.notes.ifBlank { null },
                        photoObjectKey = photoKey,
                        signatureObjectKey = sigKey,
                    ),
                )
                _state.update {
                    it.copy(
                        busy = false,
                        queuedCount = podQueue.size(),
                        message = "Offline — POD queued for flush",
                    )
                }
                return@launch
            }
            try {
                rpc.uploadPodAsset(photoKey, photo, photoMime)
                rpc.uploadPodAsset(sigKey, sig, sigMime)
                val id = rpc.submitDeliveryPod(
                    deliveryJobId = jobId,
                    photoPath = photoKey,
                    signaturePath = sigKey,
                    otpCode = otp,
                    notes = s.notes.ifBlank { null },
                )
                _state.update {
                    it.copy(
                        busy = false,
                        completed = true,
                        message = "${RpcNames.SUBMIT_DELIVERY_POD} → $id",
                    )
                }
                flushQueue()
            } catch (e: Exception) {
                podQueue.enqueue(
                    QueuedPodSubmission(
                        deliveryJobId = jobId,
                        localPhotoPath = photo,
                        photoMime = photoMime,
                        localSignaturePath = sig,
                        signatureMime = sigMime,
                        otpCode = otp,
                        notes = s.notes.ifBlank { null },
                        photoObjectKey = photoKey,
                        signatureObjectKey = sigKey,
                    ),
                )
                _state.update {
                    it.copy(
                        busy = false,
                        queuedCount = podQueue.size(),
                        error = e.message ?: "POD submit failed — queued",
                    )
                }
            }
        }
    }

    fun flushNow() {
        viewModelScope.launch { flushQueue() }
    }

    private suspend fun flushQueue() {
        if (!isOnline()) return
        val pending = podQueue.peek()
        var flushed = 0
        for (item in pending) {
            try {
                rpc.uploadPodAsset(item.photoObjectKey, item.localPhotoPath, item.photoMime)
                rpc.uploadPodAsset(
                    item.signatureObjectKey,
                    item.localSignaturePath,
                    item.signatureMime,
                )
                rpc.submitDeliveryPod(
                    deliveryJobId = item.deliveryJobId,
                    photoPath = item.photoObjectKey,
                    signaturePath = item.signatureObjectKey,
                    otpCode = item.otpCode,
                    notes = item.notes,
                )
                flushed++
                podQueue.removeJob(item.deliveryJobId)
            } catch (_: Exception) {
                break
            }
        }
        if (flushed > 0) {
            _state.update {
                it.copy(
                    queuedCount = podQueue.size(),
                    message = "Flushed $flushed queued POD(s)",
                    error = null,
                )
            }
        }
    }

    private fun isOnline(): Boolean {
        val cm = appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    companion object {
        fun factory(
            rpc: RpcClient,
            camera: PodCameraBridge,
            signature: PodSignatureBridge,
            appContext: Context,
        ): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    PodViewModel(rpc, camera, signature, appContext.applicationContext) as T
            }
    }
}
