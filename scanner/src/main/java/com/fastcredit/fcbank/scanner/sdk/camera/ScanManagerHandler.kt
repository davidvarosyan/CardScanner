@file:Suppress("DEPRECATION")

package com.fastcredit.fcbank.scanner.sdk.camera

import android.hardware.Camera
import android.os.Handler
import android.os.Message
import android.util.Log
import androidx.annotation.RestrictTo
import com.fastcredit.fcbank.scanner.sdk.utils.Constants
import java.lang.ref.WeakReference

/**
 * Custom message handler for main UI thread.
 *
 *
 * Receives messages from the renderer thread with UI-related updates, like the camera
 * parameters (which we show in a text message on screen).
 */
@RestrictTo(RestrictTo.Scope.LIBRARY)
class ScanManagerHandler(manager: ScanManager) : Handler() {
    private val mWeakScanManager: WeakReference<ScanManager>

    init {
        mWeakScanManager = WeakReference(manager)
    }

    fun sendOpenCameraError(e: Exception?) {
        sendMessage(obtainMessage(MSG_SEND_OPEN_CAMERA_ERROR, e))
    }

    fun sendRenderThreadError(e: Throwable?) {
        sendMessage(obtainMessage(MSG_SEND_RENDER_THREAD_ERROR, e))
    }

    fun sendCameraOpened(cameraParameters: Camera.Parameters?) {
        sendMessage(obtainMessage(MSG_SEND_CAMERA_OPENED, cameraParameters))
    }

    fun sendFrameProcessed(newBorders: Int) {
        sendMessage(obtainMessage(MSG_SEND_NEW_BORDERS, newBorders, 0))
    }

    fun sendFpsResport(fpSreport: String?) {
        sendMessage(obtainMessage(MSG_SEND_FPS_REPORT, fpSreport))
    }

    fun sendAutoFocusMoving(isStart: Boolean, cameraFocusMode: String?) {
        sendMessage(
            obtainMessage(
                MSG_SEND_AUTO_FOCUS_MOVING,
                if (isStart) 1 else 0,
                0,
                cameraFocusMode
            )
        )
    }

    fun sendAutoFocusComplete(isSuccess: Boolean, cameraFocusMode: String?) {
        sendMessage(
            obtainMessage(
                MSG_SEND_AUTO_FOCUS_COMPLETE,
                if (isSuccess) 1 else 0,
                0,
                cameraFocusMode
            )
        )
    }

    override fun handleMessage(msg: Message) {
        val scanManager = mWeakScanManager.get()
        if (scanManager == null) {
            if (DBG) Log.d(TAG, "Got message for dead activity")
            return
        }
        when (msg.what) {
            MSG_SEND_OPEN_CAMERA_ERROR -> scanManager.onOpenCameraError(
                (msg.obj as Exception)
            )
            MSG_SEND_RENDER_THREAD_ERROR -> scanManager.onRenderThreadError(
                (msg.obj as Throwable)
            )
            MSG_SEND_CAMERA_OPENED -> scanManager.onCameraOpened((msg.obj as Camera.Parameters))
            MSG_SEND_NEW_BORDERS -> scanManager.onFrameProcessed(msg.arg1)
            MSG_SEND_FPS_REPORT -> scanManager.onFpsReport(msg.obj as String)
            MSG_SEND_AUTO_FOCUS_MOVING -> scanManager.onAutoFocusMoving(
                msg.arg1 != 0,
                msg.obj as String
            )
            MSG_SEND_AUTO_FOCUS_COMPLETE -> scanManager.onAutoFocusComplete(
                msg.arg1 != 0,
                msg.obj as String
            )
            else -> throw RuntimeException("Unknown message " + msg.what)
        }
    }

    companion object {
        private val DBG = Constants.DEBUG
        private const val TAG = "ScanManagerHandler"
        private const val MSG_SEND_OPEN_CAMERA_ERROR = 1
        private const val MSG_SEND_RENDER_THREAD_ERROR = 2
        private const val MSG_SEND_CAMERA_OPENED = 3
        private const val MSG_SEND_NEW_BORDERS = 4
        private const val MSG_SEND_FPS_REPORT = 5
        private const val MSG_SEND_AUTO_FOCUS_MOVING = 6
        private const val MSG_SEND_AUTO_FOCUS_COMPLETE = 7
    }
}