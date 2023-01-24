package com.fastcredit.fcbank.scanner.sdk.ndk

import android.annotation.SuppressLint
import android.util.Log
import android.view.Display
import androidx.annotation.RestrictTo
import androidx.annotation.VisibleForTesting
import com.fastcredit.fcbank.scanner.sdk.ndk.RecognitionConstants.WorkAreaOrientation
import com.fastcredit.fcbank.scanner.BuildConfig

@RestrictTo(RestrictTo.Scope.LIBRARY)
class DisplayConfigurationImpl : DisplayConfiguration {
    // rotation of the screen from its "natural" orientation.
    private var mDisplayRotation = 0
    private var mNaturalOrientationIsLandscape = false

    // The orientation of the camera image. The value is the angle that the camera image needs
    // to be rotated clockwise so it shows correctly on the display in its natural orientation.
    private var mCameraSensorRotation = 0
    private var mPreprocessFrameRotation = 0
    fun setDisplayParameters(display: Display) {
        setDisplayParameters(
            DisplayHelper.getDisplayRotationDegrees(display),
            DisplayHelper.naturalOrientationIsLandscape(display)
        )
    }

    @VisibleForTesting
    fun setDisplayParameters(displayRotation: Int, naturalOrientationIsLandscape: Boolean) {
        mDisplayRotation = displayRotation
        mNaturalOrientationIsLandscape = naturalOrientationIsLandscape
        if (DBG) Log.d(
            TAG, "setDisplayParameters() called with: "
                    + "rotation: " + mDisplayRotation
                    + "; natural orientation: " + if (mNaturalOrientationIsLandscape) "landscape" else "portait (or square)"
        )
        refreshPreprocessFrameRotation()
    }

    @VisibleForTesting
    fun setDisplayRotation(displayRotation: Int) {
        mDisplayRotation = displayRotation
        refreshPreprocessFrameRotation()
    }

    fun setCameraParameters(sensorRotation: Int) {
        if (DBG) Log.d(TAG, "setCameraParameters() called with: sensorRotation = [$sensorRotation]")
        mCameraSensorRotation = sensorRotation
        refreshPreprocessFrameRotation()
    }

    @get:WorkAreaOrientation
    override val nativeDisplayRotation: Int
        get() {
            var rotation = mDisplayRotation
            if (mNaturalOrientationIsLandscape) {
                rotation = (360 + rotation + LANDSCAPE_ORIENTATION_CORRECTION) % 360
            }
            return when (rotation) {
                0 -> RecognitionConstants.WORK_AREA_ORIENTATION_PORTAIT
                90 -> RecognitionConstants.WORK_AREA_ORIENTATION_LANDSCAPE_RIGHT
                180 -> RecognitionConstants.WORK_AREA_ORIENTATION_PORTAIT_UPSIDE_DOWN
                270 -> RecognitionConstants.WORK_AREA_ORIENTATION_LANDSCAPE_LEFT
                else -> throw IllegalStateException()
            }
        }

    private fun refreshPreprocessFrameRotation() {
        var rotation =
            DisplayHelper.getCameraRotationToNatural(mDisplayRotation, mCameraSensorRotation, false)
        val nativeDisplayRotation = nativeDisplayRotation
        if (nativeDisplayRotation == RecognitionConstants.WORK_AREA_ORIENTATION_LANDSCAPE_RIGHT
            || nativeDisplayRotation == RecognitionConstants.WORK_AREA_ORIENTATION_LANDSCAPE_LEFT
        ) {
            rotation = (360 + rotation - 90) % 360
        }
        mPreprocessFrameRotation = rotation
        if (DBG) Log.v(
            TAG,
            "refreshPreprocessFrameRotation() rotation result: $mPreprocessFrameRotation"
        )
    }

    @SuppressLint("Range")
    override fun getPreprocessFrameRotation(width: Int, height: Int): Int {
        if (!sanityCheckPreprocessFrameRotation(
                width,
                height,
                mPreprocessFrameRotation
            )
        ) {
            if (DBG) Log.v(
                TAG, "Skipping frame due to orientation inconsistency."
                        + " Frame size: " + width + "x" + height
                        + "; " + this.toString()
            )
            return -1
        }
        return mPreprocessFrameRotation
    }

    private fun sanityCheckPreprocessFrameRotation(
        frameWidth: Int,
        frameHeight: Int,
        rotation: Int
    ): Boolean {
        val isPortraitFrame = frameHeight >= frameWidth
        val orientationChanged = rotation == 90 || rotation == 270

        // Destination frame must be 720x1280
        return !(isPortraitFrame && orientationChanged || !isPortraitFrame && !orientationChanged)
    }

    override fun toString(): String {
        return "DisplayConfigurationImpl{" +
                "mCameraSensorRotation=" + mCameraSensorRotation +
                ", mDisplayRotation=" + mDisplayRotation +
                ", mNaturalOrientationIsLandscape=" + mNaturalOrientationIsLandscape +
                ", mPreprocessFrameRotation=" + mPreprocessFrameRotation +
                '}'
    }

    companion object {
        private const val TAG = "DisplayConfigImpl"
        private val DBG = BuildConfig.DEBUG
        private const val LANDSCAPE_ORIENTATION_CORRECTION = -90
    }
}