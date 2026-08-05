package co.zw.nissangtr.bridges.biometricphoto



/**

 * Mirrors bridges/contracts/biometric.ts — BiometricPhotoCaptureBridge.

 * Local JPEG paths only; app uploads to Storage. No matching / network.

 */



enum class CameraPermissionStatus {

    GRANTED,

    DENIED,

    RESTRICTED,

    NOT_DETERMINED,

}



data class BiometricPhotoCaptureResult(

    val localPath: String,

    val mimeType: String,

    val capturedAt: String,

)



data class BiometricPhotoCaptureOptions(

    val title: String? = null,

    val preferFrontCamera: Boolean = true,

)



interface BiometricPhotoCaptureBridge {

    suspend fun getCameraPermissionStatus(): CameraPermissionStatus

    suspend fun requestCameraPermission(): CameraPermissionStatus

    suspend fun capturePhoto(

        options: BiometricPhotoCaptureOptions = BiometricPhotoCaptureOptions(),

    ): BiometricPhotoCaptureResult

}


