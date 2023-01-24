@file:Suppress("DEPRECATION", "MemberVisibilityCanBePrivate")

package com.fastcredit.fcbank.scanner.sdk.camera

import android.content.Context
import android.hardware.Camera
import android.util.Log
import com.fastcredit.fcbank.scanner.sdk.ndk.RecognitionCore
import com.fastcredit.fcbank.scanner.sdk.utils.Constants
import java.util.*

// https://github.com/googlesamples/android-vision/blob/master/visionSamples/barcode-reader/app/src/main/java/com/google/android/gms/samples/vision/barcodereader/ui/camera/CameraSource.java
internal class ProcessFrameThread(appContext: Context, camera: Camera, callbacks: Callbacks) :
    Thread("ProcessFrameThread") {
    private val mRecognitionCore: RecognitionCore
    private val mCamera: Camera
    private val mCallbacks: Callbacks
    private var mFpsCounter: FpsCounter? = null
    private var mDropFpsCounter: FpsCounter? = null
    private val mLock = Object()
    private var mActive = true

    @Volatile
    private var mPendingFrameData: ByteArray? = null
    private var mFpsNo = 0
    var mPredBorders = 0

    interface Callbacks {
        fun onFrameProcessed(newBorders: Int)
        fun onFpsReport(report: String?)
    }

    /**
     * Marks the runnable as active/not active.  Signals any blocked threads to continue.
     */
    fun setActive(active: Boolean) {
        synchronized(mLock) {
            if (active != mActive) {
                mActive = active
                if (!mActive) {
                    mLock.notifyAll()
                } else {
                    if (mPendingFrameData != null) mLock.notifyAll()
                }
            }
        }
    }

    fun processFrame(data: ByteArray?) {
        checkNotNull(data)
        synchronized(mLock) {
            if (mPendingFrameData != null) {
                mCamera.addCallbackBuffer(mPendingFrameData)
                mPendingFrameData = null
                if (DBG) tickDropFps()
            }
            mPendingFrameData = data

            // Notify the processor thread if it is waiting on the next frame (see below).
            mLock.notifyAll()
        }
    }

    init {
        mRecognitionCore = RecognitionCore.getInstance(appContext)
        mCamera = camera
        mCallbacks = callbacks
        if (DBG) {
            mFpsCounter = FpsCounter()
            mDropFpsCounter = FpsCounter()
        } else {
            mFpsCounter = null
            mDropFpsCounter = null
        }
    }

    /**
     * As long as the processing thread is active, this executes detection on frames
     * continuously.  The next pending frame is either immediately available or hasn't been
     * received yet.  Once it is available, we transfer the frame info to local variables and
     * run detection on that frame.  It immediately loops back for the next frame without
     * pausing.
     *
     *
     * If detection takes longer than the time in between new frames from the camera, this will
     * mean that this loop will run without ever waiting on a frame, avoiding any context
     * switching or frame acquisition time latency.
     *
     *
     * If you find that this is using more CPU than you'd like, you should probably decrease the
     * FPS setting above to allow for some idle time in between frames.
     */
    override fun run() {
        var data: ByteArray?
        if (DBG) Log.d(TAG, "Thread started. TID: " + currentThread().id)
        while (true) {
            synchronized(mLock) {
                if (mPendingFrameData == null) {
                    try {
                        // Wait for the next frame to be received from the camera, since we
                        // don't have it yet.
                        mLock.wait()
                    } catch (e: InterruptedException) {
                        if (DBG) Log.d(TAG, "Frame processing loop terminated.", e)
                        return
                    }
                }
                if (!mActive) {
                    // Exit the loop once this camera source is stopped or released.  We check
                    // this here, immediately after the wait() above, to handle the case where
                    // setActive(false) had been called, triggering the termination of this
                    // loop.
                    return
                }

                // Hold onto the frame data locally, so that we can use this for detection
                // below.  We need to clear mPendingFrameData to ensure that this buffer isn't
                // recycled back to the camera before we are done using that data.
                data = mPendingFrameData
                mPendingFrameData = null
            }
            if (DBG) tickFps(data)
            if (data == null) {
                if (DBG) Log.e(TAG, "data is null")
                throw NullPointerException()
            }

            // The code below needs to run outside of synchronization, because this will allow
            // the camera to add pending frame(s) while we are running detection on the current
            // frame.
            val borders = mRecognitionCore.processFrameYV12(1280, 720, data)
            if (borders != mPredBorders) {
                mPredBorders = borders
            }
            if (!mActive) {
                return
            }
            mCamera.addCallbackBuffer(data)
            mCallbacks.onFrameProcessed(borders)
        }
    }

    private fun tickFps(data: ByteArray?) {
        mFpsCounter?.tickFPS()
        mDropFpsCounter?.update()
        nextFps()
        if (mFpsNo == 1) {
            if (DBG) Log.d(
                TAG, "onPreviewFrame() called with: " + "data.length: " + data!!.size
                        + "; thread: " + currentThread() + "; "
            )
        }
    }

    private fun tickDropFps() {
        mDropFpsCounter!!.tickFPS()
        nextFps()
    }

    private fun nextFps() {
        mFpsNo += 1
        if (mFpsNo == 1) {
            mFpsCounter?.updateFPSFrames = 50
            mDropFpsCounter?.updateFPSFrames = 50
        } else {
            if (DBG && mFpsNo % 20 == 0) {
                mCallbacks.onFpsReport(
                    String.format(
                        Locale.US, "%s dropped: %.1f fps", mFpsCounter.toString(),
                        mDropFpsCounter?.lastFPS
                    )
                )
            }
        }
    }

    companion object {
        private val DBG = Constants.DEBUG
        private const val TAG = "ProcessFrameThread"
    }
}