@file:Suppress("MemberVisibilityCanBePrivate")

package com.fastcredit.fcbank.scanner.sdk.camera

import android.graphics.Rect
import android.util.Log
import android.view.Display
import android.view.Surface
import androidx.annotation.RestrictTo
import com.fastcredit.fcbank.scanner.BuildConfig
import java.lang.IllegalArgumentException

@RestrictTo(RestrictTo.Scope.LIBRARY)
object OrientationHelper {
    private val DBG = BuildConfig.DEBUG
    private const val TAG = "OrientationHelper"

    /**
     * Returns the rotation of the screen with the rotation of the camera from its "natural" orientation.
     * @param displayRotation [Display.getRotation] in degrees
     * @param cameraOrientation [android.hardware.Camera.CameraInfo.orientation] in degrees
     */
    // http://developer.android.com/intl/ru/reference/android/hardware/Camera.html#setDisplayOrientation%28int%29
    @JvmStatic
    fun getCameraRotationToNatural(
        displayRotation: Int,
        cameraOrientation: Int,
        compensateMirror: Boolean
    ): Int {
        if (DBG) Log.d(
            TAG,
            "getCameraRotationToNatural() called with: displayRotation = [$displayRotation], cameraOrientation = [$cameraOrientation], compensateMirror = [$compensateMirror]"
        )
        var result: Int
        if (compensateMirror) {
            result = (cameraOrientation + displayRotation) % 360
            result = (360 - result) % 360 // compensate the mirror
        } else {
            result = (cameraOrientation - displayRotation + 360) % 360
        }
        return result
    }

    @JvmStatic
    fun getDisplayRotationDegrees(display: Display): Int {
        return getDisplayRotationDegrees(display.rotation)
    }

    fun getDisplayRotationDegrees(surfaceRotationVal: Int): Int {
        return when (surfaceRotationVal) {
            Surface.ROTATION_0 -> 0
            Surface.ROTATION_90 -> 90
            Surface.ROTATION_180 -> 180
            Surface.ROTATION_270 -> 270
            else -> throw IllegalArgumentException()
        }
    }

    @JvmStatic
    fun rotateRect(src: Rect, width: Int, height: Int, degrees: Int, dstRect: Rect?): Rect {
        var dst = dstRect
        if (dst == null) dst = Rect()
        val rotation = if (degrees >= 0) degrees else 360 - degrees
        val offset1 = src.left
        val offset2 = src.top
        val offset3 = width - src.right
        val offset4 = height - src.bottom
        when (rotation) {
            0 -> {
                dst[src.left, src.top, src.right] = src.bottom
            }
            90 -> {
                dst[offset2, offset3, offset2 + src.height()] = offset3 + src.width()
            }
            180 -> {
                dst[offset3, offset4, offset3 + src.width()] = offset4 + src.height()
            }
            270 -> {
                dst[offset4, offset1, offset4 + src.height()] = offset1 + src.width()
            }
        }
        if (DBG) Log.v(
            TAG,
            "rotateRect() degrees: " + degrees + "src: " + src.toString() + "; res: " + dst.toString()
        )
        return dst
    }
}