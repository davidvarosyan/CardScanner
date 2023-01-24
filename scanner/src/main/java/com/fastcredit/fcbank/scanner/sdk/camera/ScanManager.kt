@file:Suppress("DEPRECATION")

package com.fastcredit.fcbank.scanner.sdk.camera

import android.content.Context
import android.graphics.Bitmap
import android.hardware.*
import android.util.Log
import android.view.*
import androidx.annotation.MainThread
import androidx.annotation.RestrictTo
import com.fastcredit.fcbank.scanner.sdk.camera.widget.CameraPreviewLayout
import com.fastcredit.fcbank.scanner.sdk.camera.widget.CardDetectionStateView
import com.fastcredit.fcbank.scanner.sdk.camera.widget.OnWindowFocusChangedListener
import com.fastcredit.fcbank.scanner.sdk.ndk.*
import com.fastcredit.fcbank.scanner.sdk.ndk.RecognitionConstants.RecognitionMode
import com.fastcredit.fcbank.scanner.sdk.utils.Constants
import java.util.*
import kotlin.math.sqrt

@RestrictTo(RestrictTo.Scope.LIBRARY)
class ScanManager(
    recognitionMode: Int,
    context: Context,
    previewLayout: CameraPreviewLayout,
    callbacks: Callbacks?
) {
    @RecognitionMode
    private val mRecognitionMode: Int
    private val mAppContext: Context
    private var mCallbacks: Callbacks? = null
    private val mRecognitionCore: RecognitionCore
    private val mPreviewLayout: CameraPreviewLayout

    // Receives messages from renderer thread.
    private val mHandler: ScanManagerHandler

    // Thread that handles rendering and controls the camera.  Started in onResume(),
    // stopped in onPause().
    private var mRenderThread: RenderThread? = null
    private val mWindowRotationListener: WindowRotationListener
    private val mDisplayConfiguration: DisplayConfigurationImpl

    interface Callbacks {
        fun onCameraOpened(cameraParameters: Camera.Parameters?)
        fun onOpenCameraError(exception: Exception?)
        fun onRecognitionComplete(result: RecognitionResult?)
        fun onCardImageReceived(bitmap: Bitmap?)
        fun onFpsReport(report: String?)
        fun onAutoFocusMoving(start: Boolean, cameraFocusMode: String?)
        fun onAutoFocusComplete(success: Boolean, cameraFocusMode: String?)
    }

    fun onResume() {
        if (DBG) Log.d(TAG, "onResume()")
        mRenderThread = RenderThread(mAppContext, mHandler)
        mRenderThread?.name = "Camera thread"
        mRenderThread?.start()
        mRenderThread?.waitUntilReady()
        val rh = mRenderThread?.handler
        if (sSurfaceHolder != null) {
            if (DBG) Log.d(TAG, "Sending previous surface")
            rh?.sendSurfaceAvailable(sSurfaceHolder, false)
        } else {
            if (DBG) Log.d(TAG, "No previous surface")
        }
        mDisplayConfiguration.setCameraParameters(CameraUtils.backCameraSensorOrientation)
        mRecognitionCore.setRecognitionMode(mRecognitionMode)
        mRecognitionCore.setStatusListener(mRecognitionStatusListener)
        mRecognitionCore.resetResult()
        val handler = mRenderThread?.handler
        handler?.sendOrientationChanged(CameraUtils.getBackCameraDataRotation(display))
        handler?.sendUnfreeze()
        mPreviewLayout.setOnWindowFocusChangedListener(object : OnWindowFocusChangedListener {
            override fun onWindowFocusChanged(view: View?, hasWindowFocus: Boolean) {
                if (hasWindowFocus) {
                    setRecognitionCoreIdle(false)
                } else {
                    setRecognitionCoreIdle(true)
                }
            }
        })
        startShakeDetector()
        mWindowRotationListener.register(mAppContext, display, object : WindowRotationListener(),
            WindowRotationListener.RotationListener {
            override fun onWindowRotationChanged() {
                refreshDisplayOrientation()
            }

        })
        cardDetectionStateView?.setRecognitionResult(RecognitionResult.empty())
        setRecognitionCoreIdle(false)
    }

    fun onPause() {
        if (DBG) Log.d(TAG, "onPause()")
        setRecognitionCoreIdle(true)
        stopShakeDetector()
        mPreviewLayout.setOnWindowFocusChangedListener(null)
        mRecognitionCore.setStatusListener(null)
        if (mRenderThread != null) {
            mRenderThread?.handler?.sendShutdown()
            try {
                mRenderThread?.join()
            } catch (ie: InterruptedException) {
                // not expected
                mCallbacks?.onOpenCameraError(ie)
            }
            mRenderThread = null
        }
        mWindowRotationListener.unregister()
    }

    fun resumeScan() {
        setRecognitionCoreIdle(false)
    }

    fun toggleFlash() {
        if (mRenderThread == null) return
        mRenderThread?.handler?.sendToggleFlash()
    }

    private val surfaceView: SurfaceView?
        get() = mPreviewLayout.surfaceView
    private val cardDetectionStateView: CardDetectionStateView?
        get() = mPreviewLayout.detectionStateOverlay
    private val display: Display
        get() = (mAppContext.getSystemService(Context.WINDOW_SERVICE) as WindowManager).defaultDisplay

    fun resetResult() {
        if (DBG) Log.d(TAG, "resetResult()")
        mRecognitionCore.resetResult()
        mRenderThread?.handler?.sendResumeProcessFrames()
        unfreezeCameraPreview()
    }

    private fun refreshDisplayOrientation() {
        if (DBG) Log.d(TAG, "refreshDisplayOrientation()")
        val display = display
        mDisplayConfiguration.setDisplayParameters(display)
        mRecognitionCore.setDisplayConfiguration(mDisplayConfiguration)
        mRenderThread?.handler?.sendOrientationChanged(CameraUtils.getBackCameraDataRotation(display))
    }

    private fun setRecognitionCoreIdle(idle: Boolean) {
        if (DBG) Log.d(TAG, "setRecognitionCoreIdle() called with: idle = [$idle]")
        mRecognitionCore.isIdle = idle
        if (mRenderThread != null) {
            if (idle) {
                mRenderThread?.handler?.sendPauseCamera()
            } else {
                mRenderThread?.handler?.sendResumeCamera()
            }
        }
    }

    private fun setupCardDetectionCameraParameters(previewSizeWidth: Int, previewSizeHeight: Int) {
        /* Card on 720x1280 preview frame */
        val cardNdkRect = mRecognitionCore.cardFrameRect

        /* Card on 1280x720 preview frame */
        val cardCameraRect = OrientationHelper.rotateRect(
            cardNdkRect,
            CameraUtils.CAMERA_RESOLUTION.size.height,
            CameraUtils.CAMERA_RESOLUTION.size.width,
            90,
            null
        )
        mPreviewLayout.setCameraParameters(
            previewSizeWidth,
            previewSizeHeight,
            CameraUtils.getBackCameraDataRotation(display),
            cardCameraRect
        )
    }

    @MainThread
    fun onCameraOpened(parameters: Camera.Parameters) {
        val previewSize = parameters.previewSize
        setupCardDetectionCameraParameters(previewSize.width, previewSize.height)
        mCallbacks?.onCameraOpened(parameters)
    }

    @MainThread
    fun onOpenCameraError(e: Exception) {
        if (DBG) Log.d(TAG, "onOpenCameraError() called with: e = [$e]")
        mCallbacks?.onOpenCameraError(e)
        mRenderThread = null
    }

    @MainThread
    fun onRenderThreadError(e: Throwable) {
        // XXX
        if (DBG) Log.d(TAG, "onRenderThreadError() called with: e = [$e]")
        mCallbacks?.onOpenCameraError(e as Exception)
        mRenderThread = null
    }

    @MainThread
    fun onFrameProcessed(newBorders: Int) {
        if (mCallbacks != null) mPreviewLayout.detectionStateOverlay?.setDetectionState(newBorders)
    }

    @MainThread
    fun onFpsReport(fpsReport: String?) {
        mCallbacks?.onFpsReport(fpsReport)
    }

    @MainThread
    fun onAutoFocusMoving(isStart: Boolean, focusMode: String?) {
        mCallbacks?.onAutoFocusMoving(isStart, focusMode)
    }

    @MainThread
    fun onAutoFocusComplete(isSuccess: Boolean, focusMode: String?) {
        mCallbacks?.onAutoFocusComplete(isSuccess, focusMode)
    }

    fun freezeCameraPreview() {
        if (DBG) Log.d(TAG, "freezeCameraPreview() called with: " + "")
        mRenderThread?.handler?.sendFreeze()
    }

    private fun unfreezeCameraPreview() {
        if (DBG) Log.d(TAG, "unfreezeCameraPreview() called with: " + "")
        mRenderThread?.handler?.sendUnfreeze()
    }

    private fun startShakeDetector() {
        val sensorManager = mAppContext.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val sensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        if (null != sensor) {
            sensorManager.registerListener(
                mShakeEventListener, sensor, SensorManager.SENSOR_DELAY_GAME
            )
        }
    }

    private fun stopShakeDetector() {
        val sensorManager = mAppContext.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        sensorManager.unregisterListener(mShakeEventListener)
    }

    private val mRecognitionStatusListener: RecognitionStatusListener =
        object : RecognitionStatusListener {
            private var mRecognitionCompleteTs: Long = 0
            override fun onRecognitionComplete(result: RecognitionResult) {
                cardDetectionStateView?.setRecognitionResult(result)
                if (result.isFirst) {
                    mRenderThread?.handler?.sendPauseProcessFrames()
                    cardDetectionStateView?.setDetectionState(
                        RecognitionConstants.DETECTED_BORDER_TOP or RecognitionConstants.DETECTED_BORDER_LEFT or RecognitionConstants.DETECTED_BORDER_RIGHT or RecognitionConstants.DETECTED_BORDER_BOTTOM
                    )
                    if (DBG) mRecognitionCompleteTs = System.nanoTime()
                }
                if (result.isFinal) {
                    val newTs = System.nanoTime()
                    if (DBG) Log.v(
                        TAG, String.format(
                            Locale.US,
                            "Final result received after %.3f ms",
                            (newTs - mRecognitionCompleteTs) / 1000000f
                        )
                    )
                }
                mCallbacks?.onRecognitionComplete(result)
            }

            override fun onCardImageReceived(bitmap: Bitmap) {
                if (DBG) {
                    val newTs = System.nanoTime()
                    Log.v(
                        TAG, String.format(
                            Locale.US,
                            "Card image received after %.3f ms",
                            (newTs - mRecognitionCompleteTs) / 1000000f
                        )
                    )
                }
                mCallbacks?.onCardImageReceived(bitmap)
            }
        }
    private val mShakeEventListener: SensorEventListener = object : SensorEventListener {
        val SHAKE_THRESHOLD = 3.3
        var lastUpdate: Long = 0
        var gravity = DoubleArray(3)
        override fun onSensorChanged(event: SensorEvent) {
            val curTime = System.currentTimeMillis()
            // only allow one update every 100ms.
            val diffTime = curTime - lastUpdate
            if (500 < diffTime) {
                lastUpdate = curTime
                val alpha = 0.8f
                gravity[0] = alpha * gravity[0] + (1 - alpha) * event.values[0]
                gravity[1] = alpha * gravity[1] + (1 - alpha) * event.values[1]
                gravity[2] = alpha * gravity[2] + (1 - alpha) * event.values[2]
                val x = event.values[0] - gravity[0]
                val y = event.values[1] - gravity[1]
                val z = event.values[2] - gravity[2]
                val speed = sqrt(x * x + y * y + z * z)
                if (SHAKE_THRESHOLD < speed) {
                    mRenderThread?.handler?.let {
                        it.sendRequestFocus()
                        if (DBG) Log.d(TAG, "shake focus request")
                    }
                }
            }
        }

        override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {}
    }

    init {
        var mode = recognitionMode
        if (mode == 0) mode = DEFAULT_RECOGNITION_MODE
        mRecognitionMode = mode
        mAppContext = context.applicationContext
        mCallbacks = callbacks
        mPreviewLayout = previewLayout
        mRecognitionCore = RecognitionCore.getInstance(mAppContext)
        mHandler = ScanManagerHandler(this)
        val display = display
        mDisplayConfiguration = DisplayConfigurationImpl()
        mDisplayConfiguration.setCameraParameters(CameraUtils.backCameraSensorOrientation)
        mDisplayConfiguration.setDisplayParameters(display)
        mRecognitionCore.setDisplayConfiguration(mDisplayConfiguration)
        val sh = surfaceView?.holder
        sh?.addCallback(object : SurfaceHolder.Callback {
            override fun surfaceCreated(holder: SurfaceHolder) {
                if (DBG) Log.d(
                    TAG, "SurfaceView  surfaceCreated holder=$holder (static=$sSurfaceHolder)"
                )
                if (sSurfaceHolder != null) {
                    throw RuntimeException("sSurfaceHolder is already set")
                }
                sSurfaceHolder = holder
                // Normal case -- render thread is running, tell it about the new surface.
                mRenderThread?.handler?.sendSurfaceAvailable(holder, true) ?: kotlin.run {
                    if (DBG) Log.d(TAG, "render thread not running")

                }
            }

            override fun surfaceChanged(
                holder: SurfaceHolder, format: Int, width: Int, height: Int
            ) {
                if (DBG) Log.d(
                    TAG,
                    "SurfaceView surfaceChanged fmt=" + format + " size=" + width + "x" + height + " holder=" + holder
                )
                mRenderThread?.handler?.sendSurfaceChanged(format, width, height) ?: kotlin.run {
                    if (DBG) Log.d(TAG, "Ignoring surfaceChanged")
                }
            }

            override fun surfaceDestroyed(holder: SurfaceHolder) {
                // In theory we should tell the RenderThread that the surface has been destroyed.
                mRenderThread?.handler?.sendSurfaceDestroyed()
                if (DBG) Log.d(TAG, "SurfaceView surfaceDestroyed holder=$holder")
                sSurfaceHolder = null
            }
        })
        mWindowRotationListener = WindowRotationListener()
    }

    companion object {
        private const val DEFAULT_RECOGNITION_MODE =
            (RecognitionConstants.RECOGNIZER_MODE_NUMBER or RecognitionConstants.RECOGNIZER_MODE_DATE or RecognitionConstants.RECOGNIZER_MODE_NAME or RecognitionConstants.RECOGNIZER_MODE_GRAB_CARD_IMAGE)
        private val DBG = Constants.DEBUG
        private const val TAG = "ScanManager"
        private var sSurfaceHolder: SurfaceHolder? = null
    }
}