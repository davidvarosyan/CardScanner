@file:Suppress("DEPRECATION", "UNUSED_PARAMETER", "unused")

package com.fastcredit.fcbank.scanner.sdk.camera

import android.content.Context
import android.graphics.SurfaceTexture
import android.hardware.Camera
import android.opengl.GLES20
import android.opengl.Matrix
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.util.Log
import android.view.SurfaceHolder
import androidx.annotation.RestrictTo
import com.fastcredit.fcbank.scanner.sdk.camera.AutoFocusManager.FocusMoveCallback
import com.fastcredit.fcbank.scanner.sdk.utils.Constants
import com.fastcredit.fcbank.scanner.sdk.camera.gles.*
import java.io.IOException
import java.lang.ref.WeakReference

/**
 * Thread that handles all rendering and camera operations.
 */
@RestrictTo(RestrictTo.Scope.LIBRARY)
class RenderThread(context: Context, private val mMainHandler: ScanManagerHandler) : Thread() {
    /**
     * Returns the render thread's Handler.  This may be called from any thread.
     */
    // Object must be created on render thread to get correct Looper, but is used from
    // UI thread, so we need to declare it volatile to ensure the UI thread sees a fully
    // constructed object.
    @Volatile
    var handler: RenderHandler? = null
        private set

    // Used to wait for the thread to start.
    private val mStartLock = Object()
    private var mReady = false
    private val mAppContext: Context
    private var mCameraManager: CameraManager? = null

    // Receives the output from the camera preview.
    private var mCameraTexture: SurfaceTexture? = null
    private var mEglCore: EglCore? = null
    private var mWindowSurface: WindowSurface? = null
    private var mTexProgram: Texture2dProgram? = null
    private val mRect = Sprite2d(Drawable2d())

    // Orthographic projection matrix.
    private val mDisplayProjectionMatrix = FloatArray(16)
    private var mWindowSurfaceWidth = 0
    private var mWindowSurfaceHeight = 0
    private var mCameraPreviewWidth = 1280
    private var mCameraPreviewHeight = 720
    private var mCameraRotation = 0
    private var mPosX = 0f
    private var mPosY = 0f

    @Volatile
    private var mOnFreeze = false

    init {
        mAppContext = context.applicationContext
    }

    /**
     * Thread entry point.
     */
    override fun run() {
        Looper.prepare()
        if (DBG) Log.d(TAG, "Thread started. TID: " + currentThread().id)

        // We need to create the Handler before reporting ready.
        handler = RenderHandler(this)
        synchronized(mStartLock) {
            mReady = true
            mStartLock.notify() // signal waitUntilReady()
        }
        try {
            // Prepare EGL and open the camera before we start handling messages.
            mEglCore = EglCore(null, 0)
            mCameraManager = CameraManager(mAppContext)
            mCameraManager?.openCamera()
            mCameraManager?.setProcessFrameCallbacks(object : ProcessFrameThread.Callbacks {
                override fun onFrameProcessed(newBorders: Int) {
                    mMainHandler.sendFrameProcessed(newBorders)
                }

                override fun onFpsReport(report: String?) {
                    mMainHandler.sendFpsResport(report)
                }
            })
            mCameraManager?.setAutoFocusCallbacks(object : FocusMoveCallback {
                override fun onAutoFocusMoving(start: Boolean, camera: Camera?) {
                    mMainHandler.sendAutoFocusMoving(start, camera?.parameters?.focusMode)
                }

                override fun onAutoFocusComplete(success: Boolean, camera: Camera?) {
                    mMainHandler.sendAutoFocusComplete(success, camera?.parameters?.focusMode)
                }
            })
            val previewSize = mCameraManager?.currentPreviewSize
            mCameraPreviewWidth = previewSize?.width ?: 0
            mCameraPreviewHeight = previewSize?.height ?: 0
            mCameraRotation = mCameraManager?.calculateDataRotation() ?: 0
            mMainHandler.sendCameraOpened(mCameraManager?.camera?.parameters)
        } catch (e: Exception) {
            val looper = Looper.myLooper()
            looper?.quit()
            mEglCore?.release()
            mMainHandler.sendOpenCameraError(e)
            if (DBG) Log.d(TAG, "Thread finished. TID: " + currentThread().id)
            synchronized(mStartLock) {
                mReady = false
                mStartLock.notify()
            }
            return
        }
        try {
            Looper.loop()
        } catch (e: Throwable) {
            mMainHandler.sendRenderThreadError(e)
        } finally {
            if (DBG) Log.d(TAG, "looper quit")
            mCameraManager?.releaseCamera()
            releaseGl()
            mEglCore?.release()
            if (DBG) Log.d(TAG, "Thread finished. TID: " + currentThread().id)
            synchronized(mStartLock) { mReady = false }
        }
    }

    /**
     * Waits until the render thread is ready to receive messages.
     *
     *
     * Call from the UI thread.
     */
    fun waitUntilReady() {
        synchronized(mStartLock) {
            while (!mReady) {
                try {
                    mStartLock.wait()
                } catch (ie: InterruptedException) { /* not expected */
                }
            }
        }
    }

    /**
     * Shuts everything down.
     */
    private fun shutdown() {
        if (DBG) Log.d(TAG, "shutdown()")
        mCameraManager?.releaseCamera() // avoid "sending message to a Handler on a dead thread"
        Looper.myLooper()?.quit()
    }

    /**
     * Handles the surface-created callback from SurfaceView.  Prepares GLES and the Surface.
     */
    private fun surfaceAvailable(holder: SurfaceHolder, newSurface: Boolean) {
        if (DBG) Log.d(
            TAG,
            "surfaceAvailable() called with: holder = [$holder], newSurface = [$newSurface]"
        )
        mEglCore?.let {
            mWindowSurface = WindowSurface(it, holder, false)
        }
        mWindowSurface?.makeCurrent()

        // Create and configure the SurfaceTexture, which will receive frames from the
        // camera.  We set the textured rect's program to render from it.
        mTexProgram = Texture2dProgram()
        val textureId = mTexProgram?.createTextureObject() ?: 0
        mCameraTexture = SurfaceTexture(textureId)
        mRect.setTexture(textureId)
        if (!newSurface) {
            // This Surface was established on a previous run, so no surfaceChanged()
            // message is forthcoming.  Finish the surface setup now.
            //
            // We could also just call this unconditionally, and perhaps do an unnecessary
            // bit of reallocating if a surface-changed message arrives.
            mWindowSurfaceWidth = mWindowSurface?.width ?: 0
            mWindowSurfaceHeight = mWindowSurface?.height ?: 0
            finishSurfaceSetup()
        }
        mCameraTexture?.setOnFrameAvailableListener { // SurfaceTexture.OnFrameAvailableListener; runs on arbitrary thread
            handler?.sendFrameAvailable()
        }
    }

    /**
     * Handles incoming frame of data from the camera.
     */
    private fun frameAvailable() {
        mCameraTexture?.updateTexImage()
        draw()
    }

    /**
     * Releases most of the GL resources we currently hold (anything allocated by
     * surfaceAvailable()).
     *
     *
     * Does not release EglCore.
     */
    private fun releaseGl() {
        GlUtil.checkGlError("releaseGl start")
        if (mWindowSurface != null) {
            mWindowSurface?.release()
            mWindowSurface = null
        }
        if (mTexProgram != null) {
            mTexProgram?.release()
            mTexProgram = null
        }
        GlUtil.checkGlError("releaseGl done")
        mEglCore?.makeNothingCurrent()
    }

    /**
     * Handles the surfaceChanged message.
     *
     *
     * We always receive surfaceChanged() after surfaceCreated(), but surfaceAvailable()
     * could also be called with a Surface created on a previous run.  So this may not
     * be called.
     */
    fun surfaceChanged(width: Int, height: Int) {
        if (DBG) Log.d(TAG, "RenderThread surfaceChanged " + width + "x" + height)
        mWindowSurfaceWidth = width
        mWindowSurfaceHeight = height
        finishSurfaceSetup()
    }

    private fun orientationChanged(rotation: Int) {
        if (DBG) Log.d(TAG, "orientationChanged() called with: rotation = [$rotation]")
        mCameraRotation = rotation
        updateGeometry()
    }

    private fun freeze() {
        if (DBG) Log.d(TAG, "freeze()")
        mOnFreeze = true
    }

    private fun unfreeze() {
        if (DBG) Log.d(TAG, "unfreeze()")
        mOnFreeze = false
    }

    /**
     * Handles the surfaceDestroyed message.
     */
    private fun surfaceDestroyed() {

        // In practice this never appears to be called -- the activity is always paused
        // before the surface is destroyed.  In theory it could be called though.
        if (DBG) Log.d(TAG, "surfaceDestroyed()")
        releaseGl()
    }

    /**
     * Sets up anything that depends on the window size.
     *
     *
     * Open the camera (to set mCameraAspectRatio) before calling here.
     */
    private fun finishSurfaceSetup() {
        val width = mWindowSurfaceWidth
        val height = mWindowSurfaceHeight
        if (DBG) Log.d(
            TAG, "finishSurfaceSetup size=" + width + "x" + height +
                    " camera=" + mCameraPreviewWidth + "x" + mCameraPreviewHeight
        )

        // Use full window.
        GLES20.glViewport(0, 0, width, height)

        // Simple orthographic projection, with (0,0) in lower-left corner.
        Matrix.orthoM(
            mDisplayProjectionMatrix,
            0,
            0f,
            width.toFloat(),
            0f,
            height.toFloat(),
            -1f,
            1f
        )

        // Default position is center of screen.
        mPosX = width / 2.0f
        mPosY = height / 2.0f
        updateGeometry()

        // Ready to go, start the camera.
        if (DBG) Log.d(TAG, "starting camera preview")
        try {
            mCameraTexture?.let {
                mCameraManager?.startPreview(it)
            }
        } catch (ioe: IOException) {
            throw RuntimeException(ioe)
        }
    }

    /**
     * Updates the geometry of mRect, based on the size of the window and the current
     * values set by the UI.
     */
    private fun updateGeometry() {
        val viewWidth: Int
        val viewHeight: Int
        val newWidth: Int
        val newHeight: Int
        val previewWidth = mCameraPreviewWidth
        val previewHeight = mCameraPreviewHeight
        if (mCameraRotation % 180 == 0) {
            viewWidth = mWindowSurfaceWidth
            viewHeight = mWindowSurfaceHeight
        } else {
            viewWidth = mWindowSurfaceHeight
            viewHeight = mWindowSurfaceWidth
        }

        // Center crop
        if (previewWidth * viewHeight > previewHeight * viewWidth) {
            // Scale to height
            newWidth = (previewWidth * viewHeight / previewHeight.toFloat() + 0.5f).toInt()
            newHeight = viewHeight
        } else {
            // Scale to width
            newWidth = viewWidth
            newHeight = (previewHeight * viewWidth / previewWidth.toFloat() + 0.5f).toInt()
        }
        mRect.setScale(newWidth.toFloat(), newHeight.toFloat())
        mRect.setPosition(mPosX, mPosY)
        mRect.rotation = ((360 - mCameraRotation) % 360).toFloat()
    }

    /**
     * Draws the scene and submits the buffer.
     */
    private fun draw() {
        if (mOnFreeze) return
        GlUtil.checkGlError("draw start")
        GLES20.glClearColor(0.0f, 0.0f, 0.0f, 1.0f)
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
        mRect.draw(mTexProgram, mDisplayProjectionMatrix)
        mWindowSurface?.swapBuffers()
        GlUtil.checkGlError("draw done")
    }

    /**
     * Handler for RenderThread.  Used for messages sent from the UI thread to the render thread.
     *
     *
     * The object is created on the render thread, and the various "send" methods are called
     * from the UI thread.
     */
    class RenderHandler(rt: RenderThread) : Handler() {
        // This shouldn't need to be a weak ref, since we'll go away when the Looper quits,
        // but no real harm in it.
        private val mWeakRenderThread: WeakReference<RenderThread>

        /**
         * Call from render thread.
         */
        init {
            mWeakRenderThread = WeakReference(rt)
        }

        /**
         * Sends the "surface available" message.  If the surface was newly created (i.e.
         * this is called from surfaceCreated()), set newSurface to true.  If this is
         * being called during Activity startup for a previously-existing surface, set
         * newSurface to false.
         *
         *
         * The flag tells the caller whether or not it can expect a surfaceChanged() to
         * arrive very soon.
         *
         *
         * Call from UI thread.
         */
        fun sendSurfaceAvailable(holder: SurfaceHolder?, newSurface: Boolean) {
            sendMessage(
                obtainMessage(
                    MSG_SURFACE_AVAILABLE,
                    if (newSurface) 1 else 0, 0, holder
                )
            )
        }

        /**
         * Sends the "surface changed" message, forwarding what we got from the SurfaceHolder.
         *
         *
         * Call from UI thread.
         */
        fun sendSurfaceChanged(
            format: Int, width: Int,
            height: Int
        ) {
            // ignore format
            sendMessage(obtainMessage(MSG_SURFACE_CHANGED, width, height))
        }

        /**
         * Sends the "shutdown" message, which tells the render thread to halt.
         *
         *
         * Call from UI thread.
         */
        fun sendSurfaceDestroyed() {
            sendMessage(obtainMessage(MSG_SURFACE_DESTROYED))
        }

        /**
         * Sends the "shutdown" message, which tells the render thread to halt.
         *
         *
         * Call from UI thread.
         */
        fun sendShutdown() {
            sendMessage(obtainMessage(MSG_SHUTDOWN))
        }

        /**
         * Sends the "frame available" message.
         *
         *
         * Call from UI thread.
         */
        fun sendFrameAvailable() {
            sendMessage(obtainMessage(MSG_FRAME_AVAILABLE))
        }

        fun sendOrientationChanged(newRotation: Int) {
            sendMessage(obtainMessage(MSG_ORIENTATION_CHANGED, newRotation, 0))
        }

        fun sendUnfreeze() {
            sendMessage(obtainMessage(MSG_UNFREEZE))
        }

        fun sendFreeze() {
            sendMessage(obtainMessage(MSG_FREEZE))
        }

        fun sendRequestFocus() {
            sendMessage(obtainMessage(MSG_REQUEST_FOCUS))
        }

        fun sendPauseCamera() {
            sendMessage(obtainMessage(MSG_PAUSE_CAMERA))
        }

        fun sendResumeCamera() {
            sendMessage(obtainMessage(MSG_RESUME_CAMERA))
        }

        fun sendPauseProcessFrames() {
            sendMessage(obtainMessage(MSG_PAUSE_PROCESS_FRAMES))
        }

        fun sendResumeProcessFrames() {
            sendMessage(obtainMessage(MSG_RESUME_PROCESS_FRAMES))
        }

        /**
         * Sends the "redraw" message.  Forces an immediate redraw.
         *
         *
         * Call from UI thread.
         */
        fun sendRedraw() {
            sendMessage(obtainMessage(MSG_REDRAW))
        }

        fun sendToggleFlash() {
            sendMessage(obtainMessage(MSG_TOGGLE_FLASH))
        }

        // runs on RenderThread
        override fun handleMessage(msg: Message) {
            val what = msg.what
            //if (DBG) Log.d(TAG, "RenderHandler [" + this + "]: what=" + what);
            val renderThread = mWeakRenderThread.get()
            if (renderThread == null) {
                if (DBG) Log.w(TAG, "RenderHandler.handleMessage: weak ref is null")
                return
            }
            when (what) {
                MSG_SURFACE_AVAILABLE -> renderThread.surfaceAvailable(
                    msg.obj as SurfaceHolder,
                    msg.arg1 != 0
                )
                MSG_SURFACE_CHANGED -> renderThread.surfaceChanged(msg.arg1, msg.arg2)
                MSG_SURFACE_DESTROYED -> renderThread.surfaceDestroyed()
                MSG_SHUTDOWN -> renderThread.shutdown()
                MSG_FRAME_AVAILABLE -> renderThread.frameAvailable()
                MSG_REDRAW -> renderThread.draw()
                MSG_ORIENTATION_CHANGED -> renderThread.orientationChanged(msg.arg1)
                MSG_FREEZE -> renderThread.freeze()
                MSG_UNFREEZE -> renderThread.unfreeze()
                MSG_TOGGLE_FLASH -> renderThread.mCameraManager?.toggleFlash()
                MSG_REQUEST_FOCUS -> renderThread.mCameraManager?.requestFocus()
                MSG_PAUSE_CAMERA -> renderThread.mCameraManager?.pause()
                MSG_RESUME_CAMERA -> renderThread.mCameraManager?.resume()
                MSG_PAUSE_PROCESS_FRAMES -> renderThread.mCameraManager?.pauseProcessFrames()
                MSG_RESUME_PROCESS_FRAMES -> renderThread.mCameraManager?.resumeProcessFrames()
                else -> throw RuntimeException("unknown message $what")
            }
        }

        companion object {
            private const val MSG_SURFACE_AVAILABLE = 0
            private const val MSG_SURFACE_CHANGED = 1
            private const val MSG_SURFACE_DESTROYED = 2
            private const val MSG_SHUTDOWN = 3
            private const val MSG_FRAME_AVAILABLE = 4
            private const val MSG_ORIENTATION_CHANGED = 5
            private const val MSG_REDRAW = 9
            private const val MSG_PAUSE_CAMERA = 10
            private const val MSG_RESUME_CAMERA = 11
            private const val MSG_PAUSE_PROCESS_FRAMES = 12
            private const val MSG_RESUME_PROCESS_FRAMES = 14
            private const val MSG_TOGGLE_FLASH = 15
            private const val MSG_REQUEST_FOCUS = 16
            private const val MSG_FREEZE = 17
            private const val MSG_UNFREEZE = 18
        }
    }

    companion object {
        private val DBG = Constants.DEBUG
        private const val TAG = "RenderNCameraThread"
    }
}