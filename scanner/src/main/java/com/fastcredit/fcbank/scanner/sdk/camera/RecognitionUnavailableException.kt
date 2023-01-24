package com.fastcredit.fcbank.scanner.sdk.camera

class RecognitionUnavailableException : Exception {
    val errorCode: Int

    constructor() {
        errorCode = ERROR_OTHER
    }

    constructor(errorCode: Int) {
        this.errorCode = errorCode
    }

    constructor(detailMessage: String?) : super(detailMessage) {
        errorCode = ERROR_OTHER
    }

    override val message: String
        get() = when (errorCode) {
            ERROR_NO_CAMERA -> "No camera"
            ERROR_OLD_DEVICE -> "Device is considered being too old for smooth camera experience, so camera will not be used."
            ERROR_NO_CAMERA_PERMISSION -> "No camera permission"
            ERROR_CAMERA_NOT_SUPPORTED -> "Camera not supported"
            ERROR_UNSUPPORTED_ARCHITECTURE -> "Unsupported architecture"
            else -> super.message ?: "Unknown error"
        }

    companion object {
        const val ERROR_OTHER = 0
        const val ERROR_NO_CAMERA = 1
        const val ERROR_OLD_DEVICE = 2
        const val ERROR_CAMERA_NOT_SUPPORTED = 3
        const val ERROR_NO_CAMERA_PERMISSION = 4
        const val ERROR_UNSUPPORTED_ARCHITECTURE = 5
    }
}