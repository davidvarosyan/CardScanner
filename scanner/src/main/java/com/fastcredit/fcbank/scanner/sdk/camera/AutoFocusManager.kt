@file:Suppress("DEPRECATION", "SameParameterValue", "unused")

package com.fastcredit.fcbank.scanner.sdk.camera

import android.hardware.Camera
import android.hardware.Camera.AutoFocusCallback
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.annotation.RestrictTo
import com.fastcredit.fcbank.scanner.sdk.utils.Constants

@RestrictTo(RestrictTo.Scope.LIBRARY)
class AutoFocusManager(private val mCamera: Camera?, private val mCallback: FocusMoveCallback?) {
    private var mFocusManager: FocusManager? = null
    private val mHandler: Handler = Handler(Looper.myLooper()!!)

    interface FocusMoveCallback {
        fun onAutoFocusMoving(start: Boolean, camera: Camera?)
        fun onAutoFocusComplete(success: Boolean, camera: Camera?)
    }

    fun start() {
        if (mFocusManager != null) {
            mFocusManager?.stop()
            mFocusManager = null
        }
        if (isCameraFocusContinuous) {
            mFocusManager = AutoFocusManagerImpl(mCamera, mCallback, mHandler)
            mFocusManager?.start()
            if (DBG) Log.d(TAG, "start(): camera continuous focus")
        } else if (isCameraFocusManual) {
            mFocusManager = ManualFocusManagerImpl(mCamera, mCallback, mHandler)
            mFocusManager?.start()
            if (DBG) Log.d(TAG, "start(): focus with manual reset")
        } else {
            // Focus is fixed. Ignore
        }
    }

    fun stop() {
        if (mFocusManager != null) {
            mFocusManager?.stop()
            mFocusManager = null
        }
    }

    val isStarted: Boolean
        get() = mFocusManager != null

    fun requestFocus() {
        mFocusManager?.requestFocus()
    }

    private val isCameraFocusContinuous: Boolean
        get() {
            val focusMode = mCamera?.parameters?.focusMode
            return Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE == focusMode || Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO == focusMode || Camera.Parameters.FOCUS_MODE_EDOF == focusMode
        }
    private val isCameraFocusFixed: Boolean
        get() {
            val focusMode = mCamera?.parameters?.focusMode
            return Camera.Parameters.FOCUS_MODE_INFINITY == focusMode || Camera.Parameters.FOCUS_MODE_FIXED == focusMode
        }
    private val isCameraFocusManual: Boolean
        get() {
            val focusMode = mCamera?.parameters?.focusMode
            return Camera.Parameters.FOCUS_MODE_AUTO == focusMode || Camera.Parameters.FOCUS_MODE_MACRO == focusMode
        }

    private interface FocusManager {
        fun start()
        fun stop()
        fun requestFocus()
    }

    private class ManualFocusManagerImpl constructor(
        private val mCamera: Camera?,
        private val mCallback: FocusMoveCallback?,
        private val mHandler: Handler
    ) : FocusManager {
        private var mIsFocusMoving = false
        override fun start() {
            cancelAutoFocusSafe()
            restartCounter(FOCUS_DELAY_FAST)
        }

        override fun stop() {
            mHandler.removeCallbacks(mRequestFocusRunnable)
            cancelAutoFocusSafe()
        }

        override fun requestFocus() {
            if (!mIsFocusMoving || !sFocusCompleteWorking) {
                cancelAutoFocusSafe()
                restartCounter(0)
            }
        }

        private fun restartCounter(delay: Int) {
            mHandler.removeCallbacks(mRequestFocusRunnable)
            if (delay == 0) {
                mHandler.post(mRequestFocusRunnable)
            } else {
                mHandler.postDelayed(mRequestFocusRunnable, delay.toLong())
            }
        }

        private val mRequestFocusRunnable = Runnable {
            try {
                mCamera?.autoFocus(mAutoFocusCallback)
                mIsFocusMoving = true
                mCallback?.onAutoFocusMoving(true, mCamera)
            } catch (ignored: Exception) {
                mIsFocusMoving = false
                mCallback?.onAutoFocusMoving(false, mCamera)
            }
        }
        private val mAutoFocusCallback = AutoFocusCallback { success, camera ->
            mCallback?.onAutoFocusComplete(success, camera)
            mIsFocusMoving = false
            if (!sFocusCompleteWorking) {
                sFocusCompleteWorking = true
                if (DBG) Log.d(TAG, "onAutoFocus() onAutoFocus callback looks like working")
            }
            restartCounter(if (success) FOCUS_DELAY_SLOW else FOCUS_DELAY_FAST)
        }

        init {
            if (mCallback != null) {
                mCamera?.setAutoFocusMoveCallback { start, camera ->
                    mCallback.onAutoFocusMoving(
                        start,
                        camera
                    )
                }
            }
        }

        private fun cancelAutoFocusSafe() {
            try {
                mCamera?.cancelAutoFocus()
            } catch (e: RuntimeException) {
                // IGNORE
            }
        }

        companion object {
            private const val FOCUS_DELAY_FAST = 500
            private const val FOCUS_DELAY_SLOW = 3000
            private var sFocusCompleteWorking = false
        }
    }

    /**
     * [Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE] and [Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO]
     * focus mode
     */
    private class AutoFocusManagerImpl constructor(
        private val mCamera: Camera?,
        private val mCallback: FocusMoveCallback?,
        private val mHandler: Handler
    ) : FocusManager {
        private var mCameraMoving = false
        override fun start() {
            resumeAutoFocus()
            restartCounter(FOCUS_RESET_DELAY)
        }

        override fun stop() {
            mHandler.removeCallbacks(mResetFocusRunnable)
        }

        override fun requestFocus() {
            if (!mCameraMoving) {
                cancelAutoFocusSafe()
                restartCounter(FOCUS_RESET_DELAY)
            } else {
                if (DBG) Log.d(TAG, "requestFocus(): ignore since camera is moving")
            }
        }

        private fun resumeAutoFocus() {
            //if (DBG) Log.d(TAG, "resumeAutoFocus()");
            cancelAutoFocusSafe()
        }

        private fun restartCounter(delay: Int) {
            mHandler.removeCallbacks(mResetFocusRunnable)
            if (delay == 0) {
                mHandler.post(mResetFocusRunnable)
            } else {
                mHandler.postDelayed(mResetFocusRunnable, delay.toLong())
            }
        }

        private val mResetFocusRunnable = Runnable {
            try {
                resumeAutoFocus()
                restartCounter(FOCUS_RESET_DELAY)
            } catch (ignored: Exception) {
                // ignore
            }
        }

        init {
            if (mCallback != null) {
                mCamera?.setAutoFocusMoveCallback { start, camera ->
                    mCallback.onAutoFocusMoving(start, camera)
                    mCameraMoving = start
                }
            }
        }

        private fun cancelAutoFocusSafe() {
            try {
                mCamera?.cancelAutoFocus()
            } catch (e: RuntimeException) {
                // IGNORE
            }
        }

        companion object {
            private const val FOCUS_RESET_DELAY = 1000
        }
    }

    companion object {
        private val DBG = Constants.DEBUG
        private const val TAG = "AutoFocusManager"
    }
}