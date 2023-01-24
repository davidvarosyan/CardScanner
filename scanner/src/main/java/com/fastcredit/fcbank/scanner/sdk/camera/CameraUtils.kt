@file:Suppress("DEPRECATION", "MemberVisibilityCanBePrivate", "unused")

package com.fastcredit.fcbank.scanner.sdk.camera

import android.hardware.Camera
import android.hardware.Camera.CameraInfo
import android.text.TextUtils
import android.view.Display
import androidx.annotation.RestrictTo
import com.fastcredit.fcbank.scanner.sdk.camera.OrientationHelper.getCameraRotationToNatural
import com.fastcredit.fcbank.scanner.sdk.camera.OrientationHelper.getDisplayRotationDegrees
import com.fastcredit.fcbank.scanner.sdk.utils.Size
import java.util.*

@RestrictTo(RestrictTo.Scope.LIBRARY)
object CameraUtils {
    val CAMERA_RESOLUTION = NativeSupportedSize.RESOLUTION_1280_X_720
    private var sBestCameraPreviewSize: NativeSupportedSize? = null

    @get:Throws(BlockingOperationException::class)
    @JvmStatic
    val isCameraSupported: Boolean
        get() {
            if (sBestCameraPreviewSize != null) {
                return sBestCameraPreviewSize != NativeSupportedSize.RESOLUTION_NO_CAMERA
            }
            throw BlockingOperationException()
        }

    // XXX slooow
    val isCameraSupportedBlocking: Boolean
        get() {
            try {
                return isCameraSupported
            } catch (ignore: BlockingOperationException) {
            }

            // XXX slooow
            generateBestCameraPreviewSize()
            return sBestCameraPreviewSize != null && sBestCameraPreviewSize != NativeSupportedSize.RESOLUTION_NO_CAMERA
        }

    @JvmStatic
    fun findBestCameraSupportedSize(previewSizes: Iterable<Camera.Size>?): NativeSupportedSize {
        var best = NativeSupportedSize.RESOLUTION_NO_CAMERA
        if (null == previewSizes) {
            return NativeSupportedSize.RESOLUTION_NO_CAMERA
        }
        for (previewSize in previewSizes) {
            for (supportedSize in NativeSupportedSize.values()) {
                if (previewSize.width == supportedSize.size.width && previewSize.height == supportedSize.size.height) {
                    if (supportedSize < best) {
                        best = supportedSize
                    }
                    break
                }
            }
        }
        return best
    }

    fun generateBestCameraPreviewSize() {
        if (null == sBestCameraPreviewSize) {
            try {
                val camera = Camera.open()
                if (null != camera) {
                    generateBestCameraPreviewSize(camera, camera.parameters.supportedPreviewSizes)
                    camera.release()
                }
            } catch (ignored: Exception) {
                // pass
            }
        }
    }

    fun generateBestCameraPreviewSize(camera: Camera?, previewSizes: Iterable<Camera.Size>?) {
        if (null == previewSizes) {
            sBestCameraPreviewSize = NativeSupportedSize.RESOLUTION_NO_CAMERA
            return
        }
        sBestCameraPreviewSize = findBestCameraSupportedSize(previewSizes)
        if (sBestCameraPreviewSize == NativeSupportedSize.RESOLUTION_NO_CAMERA) {
            //trying to set all items...
            for (nativeSupportedSize in NativeSupportedSize.values()) {
                if (tryToSetCameraSize(camera, nativeSupportedSize)) {
                    sBestCameraPreviewSize = nativeSupportedSize
                    return
                }
            }
            sBestCameraPreviewSize = NativeSupportedSize.RESOLUTION_NO_CAMERA
        }
    }

    private fun tryToSetCameraSize(
        camera: Camera?, nativeSupportedSize: NativeSupportedSize
    ): Boolean {
        if (null == camera) {
            return false
        }
        val params = camera.parameters
        params.setPreviewSize(nativeSupportedSize.size.width, nativeSupportedSize.size.height)
        return try {
            camera.parameters = params
            val previewSize = camera.parameters.previewSize
            !(previewSize.width != nativeSupportedSize.size.width && previewSize.height != nativeSupportedSize.size.height)
        } catch (ignored: Exception) {
            false
        }
    }

    fun getSupportedSizesDescription(sizes: List<Camera.Size>): String {
        val text: MutableList<String?> = ArrayList(sizes.size)
        for (size in sizes) text.add(String.format(Locale.US, "[%dx%d]", size.width, size.height))
        return TextUtils.join(", ", text)
    }

    val backCameraInfo: CameraInfo?
        get() {
            val numberOfCameras = Camera.getNumberOfCameras()
            val cameraInfo = CameraInfo()
            for (i in 0 until numberOfCameras) {
                Camera.getCameraInfo(i, cameraInfo)
                if (cameraInfo.facing == CameraInfo.CAMERA_FACING_BACK) {
                    return cameraInfo
                }
            }
            return null
        }

    @JvmStatic
    val backCameraSensorOrientation: Int
        get() {
            val cameraInfo = backCameraInfo
            return cameraInfo?.orientation ?: 0
        }

    @JvmStatic
    fun getBackCameraDataRotation(display: Display): Int {
        return getCameraDataRotation(display, backCameraInfo)
    }

    // http://www.wordsaretoys.com/2013/10/25/roll-that-camera-zombie-rotation-and-coversion-from-yv12-to-yuv420planar/
    private fun getCameraDataRotation(display: Display, cameraInfo: CameraInfo?): Int {
        val rotation = getDisplayRotationDegrees(display)
        return if (cameraInfo == null) 0 else getCameraRotationToNatural(
            rotation, cameraInfo.orientation, cameraInfo.facing == CameraInfo.CAMERA_FACING_FRONT
        )
    }

    enum class NativeSupportedSize(width: Int, height: Int) {
        RESOLUTION_1280_X_720(1280, 720), RESOLUTION_NO_CAMERA(-1, -1);

        @JvmField
        val size: Size

        init {
            size = Size(width, height)
        }
    }
}