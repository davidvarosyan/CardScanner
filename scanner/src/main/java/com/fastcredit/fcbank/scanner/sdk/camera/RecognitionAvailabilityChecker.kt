@file:Suppress("UNUSED_VARIABLE")

package com.fastcredit.fcbank.scanner.sdk.camera

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import androidx.annotation.RestrictTo
import androidx.core.content.ContextCompat
import com.fastcredit.fcbank.scanner.sdk.ndk.RecognitionCore
import com.facebook.device.yearclass.YearClass
import com.fastcredit.fcbank.scanner.BuildConfig
import java.util.*

@RestrictTo(RestrictTo.Scope.LIBRARY)
object RecognitionAvailabilityChecker {
    private val DBG = BuildConfig.DEBUG
    const val TAG = "CameraChecker"

    // Execute non-blocking tests
    @JvmStatic
    fun doCheck(context: Context): Result {
        return doCheckInternal(context).build()
    }

    @Suppress("unused")
    fun doCheckBlocking(context: Context): Result {
        val builder = doCheckInternal(context)
        val result = builder.build()
        if (!builder.build().isAdditionalCheckRequired) {
            return result
        }
        builder.isBlockingCheck(true)
        builder.recognitionCoreSupported(RecognitionCore.getInstance(context).isDeviceSupported)
        if (builder.recognitionCoreSupported == Result.STATUS_FAILED) {
            return builder.build()
        }
        builder.isCameraSupported(CameraUtils.isCameraSupportedBlocking)
        return builder.build()
    }

    private fun doCheckInternal(context: Context): RecognitionCheckResultBuilder {
        val builder = RecognitionCheckResultBuilder()
            .isBlockingCheck(false)
            .isDeviceNewEnough(isDeviceNewEnough(context))
            .hasCamera(isDeviceHasCamera(context))
            .hasCameraPermission(
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.CAMERA
                ) == PackageManager.PERMISSION_GRANTED
            )
        try {
            builder.isCameraSupported(CameraUtils.isCameraSupported)
        } catch (e: BlockingOperationException) {
            // IGNORE
        }
        if (RecognitionCore.isInitialized) {
            builder.recognitionCoreSupported(RecognitionCore.getInstance(context).isDeviceSupported)
        }
        return builder
    }

    @JvmStatic
    fun isDeviceNewEnough(context: Context?): Boolean {
        val year = YearClass.get(context)
        if (DBG) Log.d(TAG, "Device year is: $year")
        return year >= 2011
    }

    @JvmStatic
    fun isDeviceHasCamera(context: Context): Boolean {
        val pm = context.packageManager
        val hasCameraFeature = pm.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY)
        val hasAutofocus = pm.hasSystemFeature(PackageManager.FEATURE_CAMERA_AUTOFOCUS)
        return hasCameraFeature /* && hasAutofocus*/
    }

    class Result internal constructor(
        private val isBlockingCheck: Boolean,
        private val isDeviceNewEnough: Int,
        private val recognitionCoreSupported: Int,
        private val hasCamera: Int,
        private val hasCameraPermission: Int,
        private val isCameraSupported: Int
    ) {
        // some of checked tests are failed
        private val isFailedNonBlocking: Boolean
            get() =// some of checked tests are failed
                (isDeviceNewEnough == STATUS_FAILED
                        || recognitionCoreSupported == STATUS_FAILED
                        || hasCamera == STATUS_FAILED
                        || hasCameraPermission == STATUS_FAILED
                        || isCameraSupported == STATUS_FAILED)
        val isFailed: Boolean
            get() = if (isBlockingCheck) {
                !isPassed
            } else {
                isFailedNonBlocking
            }

        // all the tests has been completed and passed
        val isPassed: Boolean
            get() =// all the tests has been completed and passed
                (isDeviceNewEnough == STATUS_PASSED
                        && recognitionCoreSupported == STATUS_PASSED
                        && hasCamera == STATUS_PASSED
                        && hasCameraPermission == STATUS_PASSED
                        && isCameraSupported == STATUS_PASSED)
        val isAdditionalCheckRequired: Boolean
            get() = !isFailed && !isPassed
        val isFailedOnCameraPermission: Boolean
            get() = (hasCameraPermission == STATUS_FAILED
                    && isDeviceNewEnough != STATUS_FAILED
                    && recognitionCoreSupported != STATUS_FAILED
                    && hasCamera != STATUS_FAILED
                    && isCameraSupported != STATUS_FAILED)

        private fun statusToString(status: Int): String {
            return when (status) {
                STATUS_PASSED -> "yes"
                STATUS_FAILED -> "no"
                STATUS_NOT_CHECKED -> "not checked"
                else -> throw IllegalArgumentException()
            }
        }

        val message: String
            get() {
                if (isDeviceNewEnough == STATUS_FAILED) return "Device is considered being too old for smooth camera experience, so camera will not be used."
                if (hasCamera == STATUS_FAILED) return "No camera"
                if (hasCameraPermission == STATUS_FAILED) return "No camera permission"
                if (isCameraSupported == STATUS_FAILED) return "Camera not supported"
                return if (recognitionCoreSupported == STATUS_FAILED) "Unsupported architecture" else toString()
            }

        override fun toString(): String {
            return String.format(
                Locale.US,
                "Is new enough: %s, has camera: %s, has camera persmission: %s, recognition library supported: %s, camera supported: %s",
                statusToString(isDeviceNewEnough),
                statusToString(hasCamera),
                statusToString(hasCameraPermission),
                statusToString(recognitionCoreSupported),
                statusToString(isCameraSupported)
            )
        }

        companion object {
            const val STATUS_NOT_CHECKED = 0
            const val STATUS_PASSED = 1
            const val STATUS_FAILED = -1
        }
    }

    private class RecognitionCheckResultBuilder {
        private var isBlockingCheck = true
        private var hasCameraPermission = Result.STATUS_NOT_CHECKED
        private var isDeviceNewEnough = Result.STATUS_NOT_CHECKED
        var recognitionCoreSupported = Result.STATUS_NOT_CHECKED
        private var hasCamera = Result.STATUS_NOT_CHECKED
        private var isCameraSupported = Result.STATUS_NOT_CHECKED
        fun isBlockingCheck(isBlockingCheck: Boolean): RecognitionCheckResultBuilder {
            this.isBlockingCheck = isBlockingCheck
            return this
        }

        fun isDeviceNewEnough(isDeviceNewEnough: Boolean): RecognitionCheckResultBuilder {
            this.isDeviceNewEnough = toStatus(isDeviceNewEnough)
            return this
        }

        fun recognitionCoreSupported(recognitionCoreSupported: Boolean): RecognitionCheckResultBuilder {
            this.recognitionCoreSupported = toStatus(recognitionCoreSupported)
            return this
        }

        fun hasCamera(hasCamera: Boolean): RecognitionCheckResultBuilder {
            this.hasCamera = toStatus(hasCamera)
            return this
        }

        fun hasCameraPermission(hasCameraPermission: Boolean): RecognitionCheckResultBuilder {
            this.hasCameraPermission = toStatus(hasCameraPermission)
            return this
        }

        fun isCameraSupported(isCameraSupported: Boolean): RecognitionCheckResultBuilder {
            this.isCameraSupported = toStatus(isCameraSupported)
            return this
        }

        fun build(): Result {
            return Result(
                isBlockingCheck,
                isDeviceNewEnough,
                recognitionCoreSupported,
                hasCamera,
                hasCameraPermission,
                isCameraSupported
            )
        }

        private fun toStatus(isPassed: Boolean): Int {
            return if (isPassed) Result.STATUS_PASSED else Result.STATUS_FAILED
        }
    }
}