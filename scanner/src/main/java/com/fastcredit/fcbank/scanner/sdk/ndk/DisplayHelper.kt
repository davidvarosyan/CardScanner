@file:Suppress("MemberVisibilityCanBePrivate", "DEPRECATION")

package com.fastcredit.fcbank.scanner.sdk.ndk

import android.util.DisplayMetrics
import android.util.Log
import android.view.Display
import android.view.Surface
import com.fastcredit.fcbank.scanner.BuildConfig

internal object DisplayHelper {
    private val DBG = BuildConfig.DEBUG
    private const val TAG = "DisplayHelper"

    /**
     * Returns the rotation of the screen with the rotation of the camera from its "natural" orientation.
     * @param displayRotation [Display.getRotation] in degrees
     * @param cameraOrientation [android.hardware.Camera.CameraInfo.orientation] in degrees
     */
    // http://developer.android.com/intl/ru/reference/android/hardware/Camera.html#setDisplayOrientation%28int%29
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

    fun getDisplayRotationDegrees(surfaceRotationVal: Int): Int {
        return when (surfaceRotationVal) {
            Surface.ROTATION_0 -> 0
            Surface.ROTATION_90 -> 90
            Surface.ROTATION_180 -> 180
            Surface.ROTATION_270 -> 270
            else -> throw IllegalArgumentException()
        }
    }

    fun getDisplayRotationDegrees(display: Display): Int {
        return getDisplayRotationDegrees(display.rotation)
    }

    fun naturalOrientationIsLandscape(display: Display): Boolean {
        val rotation = display.rotation
        val dm = DisplayMetrics()
        display.getMetrics(dm)
        return when (rotation) {
            Surface.ROTATION_0, Surface.ROTATION_180 -> dm.widthPixels > dm.heightPixels
            Surface.ROTATION_270, Surface.ROTATION_90 -> dm.heightPixels > dm.widthPixels
            else -> dm.heightPixels > dm.widthPixels
        }
    }
}