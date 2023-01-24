@file:Suppress("DEPRECATION")

package com.fastcredit.fcbank.scanner.sdk.camera

import android.hardware.Camera
import android.util.Log
import androidx.annotation.RestrictTo
import com.fastcredit.fcbank.scanner.sdk.ndk.RecognitionCore
import com.fastcredit.fcbank.scanner.sdk.ndk.TorchStatusListener
import com.fastcredit.fcbank.scanner.sdk.utils.Constants

@RestrictTo(RestrictTo.Scope.LIBRARY)
class TorchManager(private val mRecognitionCore: RecognitionCore, private val mCamera: Camera?) {
    private var mPaused = false
    private var mTorchTurnedOn = false
    fun pause() {
        if (DBG) Log.d(TAG, "pause()")
        CameraConfigurationUtils.setFlashLight(mCamera, false)
        mPaused = true
        mRecognitionCore.setTorchListener(null)
    }

    fun resume() {
        if (DBG) Log.d(TAG, "resume()")
        mPaused = false
        mRecognitionCore.setTorchListener(mRecognitionCoreTorchStatusListener)
        if (mTorchTurnedOn) {
            mRecognitionCore.setTorchStatus(true)
        } else {
            mRecognitionCore.setTorchStatus(false)
        }
    }

    fun destroy() {
        mRecognitionCore.setTorchListener(null)
    }

    private val isTorchTurnedOn: Boolean
        get() {
            val flashMode = mCamera?.parameters?.flashMode
            return Camera.Parameters.FLASH_MODE_TORCH == flashMode || Camera.Parameters.FLASH_MODE_ON == flashMode
        }

    fun toggleTorch() {
        if (mPaused) return
        val newStatus = !isTorchTurnedOn
        if (DBG) Log.d(TAG, "toggleTorch() called with newStatus: $newStatus")
        mRecognitionCore.setTorchStatus(newStatus)

        // onTorchStatusChanged() will not be called if the RecognitionCore internal status will not be changed.
        // Sync twice to keep safe
        CameraConfigurationUtils.setFlashLight(mCamera, newStatus)
    }

    private val mRecognitionCoreTorchStatusListener = object : TorchStatusListener {
        override fun onTorchStatusChanged(turnTorchOn: Boolean) {
            // called from RecognitionCore
            if (mCamera == null) return
            if (DBG) Log.d(TAG, "onTorchStatusChanged() called with: turnTorchOn = [$turnTorchOn]")
            if (turnTorchOn) {
                mTorchTurnedOn = true
                if (!mPaused) CameraConfigurationUtils.setFlashLight(mCamera, true)
            } else {
                mTorchTurnedOn = false
                CameraConfigurationUtils.setFlashLight(mCamera, false)
            }
        }
    }

    companion object {
        private val DBG = Constants.DEBUG
        private const val TAG = "TorchManager"
    }
}