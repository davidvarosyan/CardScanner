@file:Suppress("DEPRECATION")

package com.fastcredit.fcbank.scanner.sdk.camera

import android.content.Context
import android.hardware.display.DisplayManager
import android.hardware.display.DisplayManager.DisplayListener
import android.os.Handler
import android.util.Log
import android.view.Display
import androidx.annotation.RestrictTo
import com.fastcredit.fcbank.scanner.sdk.utils.Constants

@RestrictTo(RestrictTo.Scope.LIBRARY)
open class WindowRotationListener {
    interface RotationListener {
        fun onWindowRotationChanged()
    }

    private interface Impl {
        fun register(context: Context, display: Display, listener: RotationListener?)
        fun unregister()
    }

    fun register(context: Context, display: Display, listener: RotationListener?) {
        sImpl?.register(context, display, listener)
    }

    fun unregister() {
        sImpl?.unregister()
    }

    private class ImplApi17 : Impl, DisplayListener {
        private var mListener: RotationListener? = null
        private val mHandler: Handler = Handler()
        private var mDisplayManager: DisplayManager? = null
        private var mDisplayId = 0

        override fun register(context: Context, display: Display, listener: RotationListener?) {
            mListener = listener
            mDisplayManager = context.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
            mDisplayManager?.registerDisplayListener(this, mHandler)
            mDisplayId = display.displayId
        }

        override fun unregister() {
            if (mDisplayManager == null) return
            mDisplayManager?.unregisterDisplayListener(this)
            mDisplayManager = null
            mListener = null
        }

        override fun onDisplayAdded(displayId: Int) {
            if (DBG) Log.d(TAG, "onDisplayAdded() called with: displayId = [$displayId]")
        }

        override fun onDisplayRemoved(displayId: Int) {
            if (DBG) Log.d(TAG, "onDisplayRemoved() called with: displayId = [$displayId]")
        }

        override fun onDisplayChanged(displayId: Int) {
            if (DBG) Log.d(TAG, "onDisplayChanged() called with: displayId = [$displayId]")
            if (mListener != null && displayId == mDisplayId) {
                mListener?.onWindowRotationChanged()
            }
        }
    }

    companion object {
        private const val TAG = "WindowRotationListener"
        private val DBG = Constants.DEBUG
        private var sImpl: Impl? = null

        init {
            sImpl = ImplApi17()
        }
    }
}