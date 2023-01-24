@file:Suppress("DEPRECATION", "unused")

package com.fastcredit.fcbank.scanner.sdk.camera

import android.content.Context
import android.graphics.ImageFormat
import android.graphics.SurfaceTexture
import android.hardware.Camera
import android.hardware.Camera.PreviewCallback
import android.util.Log
import android.view.WindowManager
import com.fastcredit.fcbank.scanner.sdk.camera.AutoFocusManager.FocusMoveCallback
import com.fastcredit.fcbank.scanner.sdk.camera.CameraUtils.NativeSupportedSize
import com.fastcredit.fcbank.scanner.sdk.camera.CameraUtils.findBestCameraSupportedSize
import com.fastcredit.fcbank.scanner.sdk.camera.CameraUtils.getBackCameraDataRotation
import com.fastcredit.fcbank.scanner.sdk.ndk.RecognitionCore
import com.fastcredit.fcbank.scanner.sdk.utils.Constants
import java.io.IOException

internal class CameraManager(context: Context) {
    private val mAppContext: Context
    private val mRecognitionCore: RecognitionCore
    var camera: Camera? = null
        private set
    private var mAutoFocusManager: AutoFocusManager? = null
    private var mTorchManager: TorchManager? = null

    @Volatile
    private var mProcessThread: ProcessFrameThread? = null
    private var mFocusCallbacks: FocusMoveCallback? = null
    private var mProcessFrameCallbacks: ProcessFrameThread.Callbacks? = null

    @Volatile
    private var mSnapNextFrameCallback: PreviewCallback? = null
    private var mIsResumed: Boolean
    private var mIsProcessFramesActive: Boolean

    init {
        mAppContext = context.applicationContext
        mRecognitionCore = RecognitionCore.getInstance(mAppContext)
        mIsResumed = true
        mIsProcessFramesActive = true
    }

    @get:Synchronized
    val isOpen: Boolean
        get() = camera != null
    val currentPreviewSize: Camera.Size?
        get() {
            return camera?.parameters?.previewSize
        }

    fun calculateDataRotation(): Int {
        val display =
            (mAppContext.getSystemService(Context.WINDOW_SERVICE) as WindowManager).defaultDisplay
        return getBackCameraDataRotation(display)
    }

    @Synchronized
    @Throws(Exception::class)
    fun openCamera() {
        if (camera != null) releaseCamera()
        openCameraInternal()
        mAutoFocusManager = AutoFocusManager(camera, mFocusCallbacks)
        syncAutofocusManager()
        mTorchManager = TorchManager(mRecognitionCore, camera)
        syncTorchManager()
        syncProcessThread(true)
    }

    @Synchronized
    fun releaseCamera() {
        if (DBG) Log.d(TAG, "releaseCamera()")
        stopProcessThread()
        if (mAutoFocusManager != null) {
            mAutoFocusManager?.stop()
            mAutoFocusManager = null
        }
        if (mTorchManager != null) {
            mTorchManager?.destroy()
            mTorchManager = null
        }
        if (camera != null) {
            // release the camera for other applications
            camera?.setPreviewCallbackWithBuffer(null)
            camera?.stopPreview()
            camera?.release()
            camera = null
        }
    }

    fun setAutoFocusCallbacks(callback: FocusMoveCallback?) {
        mFocusCallbacks = callback
    }

    fun setProcessFrameCallbacks(callbacks: ProcessFrameThread.Callbacks?) {
        mProcessFrameCallbacks = callbacks
    }

    fun toggleFlash() {
        if (DBG) Log.d(TAG, "toggleFlash()")
        mTorchManager?.toggleTorch()
    }

    fun requestFocus() {
        if (DBG) Log.d(TAG, "requestFocus()")
        mAutoFocusManager?.requestFocus()
    }

    @Synchronized
    fun pause() {
        if (DBG) Log.d(TAG, "pause()")
        if (!mIsResumed) return
        mIsResumed = false
        syncAutofocusManager()
        syncTorchManager()
        syncProcessThread(false)
    }

    @Synchronized
    fun resume() {
        if (DBG) Log.d(TAG, "resume(); is resumed already: $mIsResumed")
        if (mIsResumed) return
        mIsResumed = true
        syncAutofocusManager()
        syncTorchManager()
        syncProcessThread(false)
    }

    @Synchronized
    fun resumeProcessFrames() {
        if (DBG) Log.d(
            TAG,
            "resumeProcessFrames(); frames processed already: $mIsProcessFramesActive"
        )
        if (mIsProcessFramesActive) return
        mIsProcessFramesActive = true
        syncAutofocusManager()
        syncTorchManager()
        syncProcessThread(false)
    }

    @Synchronized
    fun pauseProcessFrames() {
        if (!mIsProcessFramesActive) return
        mIsProcessFramesActive = false
        syncAutofocusManager()
        syncTorchManager()
        syncProcessThread(false)
    }

    @Throws(IOException::class, RuntimeException::class)
    fun startPreview(texture: SurfaceTexture) {
        if (DBG) Log.d(TAG, "startPreview() called with: texture = [$texture]")
        try {
            if (camera != null) {
                camera?.setPreviewTexture(texture)
                camera?.startPreview()
            } else {
                if (DBG) Log.e(TAG, "Camera is not opened. Skip startPreview()")
            }
        } catch (e: IOException) {
            releaseCamera()
            throw e
        } catch (e: RuntimeException) {
            releaseCamera()
            throw e
        }
    }

    @Throws(Exception::class)
    private fun openCameraInternal() {
        if (camera != null) releaseCamera()
        try {
            camera = Camera.open()
            camera?.let {
                val supportedSize: NativeSupportedSize
                val parameters = it.parameters
                val supportedPreviewSizes = parameters.supportedPreviewSizes
                supportedSize = findBestCameraSupportedSize(supportedPreviewSizes)
                if (supportedSize === NativeSupportedSize.RESOLUTION_NO_CAMERA) {
                    throw RecognitionUnavailableException(RecognitionUnavailableException.ERROR_CAMERA_NOT_SUPPORTED)
                }
                parameters.setPreviewSize(supportedSize.size.width, supportedSize.size.height)
                parameters.previewFormat = ImageFormat.YV12
                CameraConfigurationUtils.setBestExposure(parameters, false)
                CameraConfigurationUtils.initWhiteBalance(parameters)
                CameraConfigurationUtils.initAutoFocus(parameters)

                // parameters.setRecordingHint(true);
                CameraConfigurationUtils.setMetering(parameters)
                it.parameters = parameters
            }
            // if (DBG) Log.v(TAG, "Camera parameters: " + mCamera.getParameters().flatten().replace(";", "; "));
        } catch (e: Exception) {
            // Something bad happened
            if (DBG) Log.e(TAG, "startCamera() error: ", e)
            releaseCamera()
            throw e
        }
    }

    @get:Synchronized
    private val isTorchManagerShouldBeActive: Boolean
        get() = camera != null && mIsResumed && mIsProcessFramesActive
    private val isAutofocusShouldBeActive: Boolean
        get() = camera != null && mIsResumed

    @Synchronized
    private fun syncTorchManager() {
        if (isTorchManagerShouldBeActive) {
            mTorchManager?.resume()
        } else {
            mTorchManager?.pause()
        }
    }

    @Synchronized
    private fun syncAutofocusManager() {
        if (isAutofocusShouldBeActive) {
            mAutoFocusManager?.start()
        } else {
            mAutoFocusManager?.stop()
        }
    }

    @Synchronized
    private fun syncProcessThread(forceRestart: Boolean) {
        if (mIsResumed && mIsProcessFramesActive && camera != null) {
            if (forceRestart || mProcessThread == null) startProcessThread()
        } else {
            if (mProcessThread != null) stopProcessThread()
        }
    }

    @Synchronized
    private fun startProcessThread() {
        if (camera == null) {
            if (DBG) Log.e(TAG, "Camera is not opened. Skip startProcessThread()")
            return
        }
        stopProcessThread()
        mProcessThread = null
        camera?.let {
            mProcessThread =
                ProcessFrameThread(mAppContext, it, object : ProcessFrameThread.Callbacks {
                    override fun onFrameProcessed(newBorders: Int) {
                        mProcessFrameCallbacks?.onFrameProcessed(
                            newBorders
                        )
                    }

                    override fun onFpsReport(report: String?) {
                        //if (DBG) Log.v(TAG, report);
                        mProcessFrameCallbacks?.onFpsReport(report)
                    }
                })
        }

        mProcessThread?.start()
        val thread: ProcessFrameThread? = mProcessThread
        camera?.setPreviewCallbackWithBuffer(object : PreviewCallback {
            var singleFrameCallback: PreviewCallback? = null
            @Deprecated("Deprecated in Java")
            override fun onPreviewFrame(data: ByteArray, camera: Camera) {
                if (this@CameraManager.camera == null) return
                if (DBG) {
                    singleFrameCallback = mSnapNextFrameCallback
                    mSnapNextFrameCallback = null
                    singleFrameCallback?.onPreviewFrame(
                        data,
                        camera
                    )
                }
                thread?.processFrame(data)
            }
        })
        camera?.let {
            val size = it.parameters.previewSize
            for (i in 0..2) {
                it.addCallbackBuffer(ByteArray(size.width * size.height * 3 / 2))
            }
        }

    }

    @Synchronized
    private fun stopProcessThread() {
        if (DBG) Log.d(TAG, "stopProcessThread()")
        if (mProcessThread != null) {
            mProcessThread?.setActive(false)
            mProcessThread = null
            camera?.setPreviewCallbackWithBuffer(null)
        }
    }

    companion object {
        private val DBG = Constants.DEBUG
        private const val TAG = "RenderNCamThreadCamera"
    }
}