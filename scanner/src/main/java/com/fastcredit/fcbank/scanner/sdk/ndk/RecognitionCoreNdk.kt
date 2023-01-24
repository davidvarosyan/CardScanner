@file:Suppress("MemberVisibilityCanBePrivate", "unused")

package com.fastcredit.fcbank.scanner.sdk.ndk

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.util.Log
import androidx.annotation.Keep
import androidx.annotation.WorkerThread
import com.fastcredit.fcbank.scanner.sdk.ndk.RecognitionConstants.DetectedBorderFlags
import com.fastcredit.fcbank.scanner.sdk.ndk.RecognitionConstants.RecognitionMode
import java.io.IOException

internal class RecognitionCoreNdk private constructor(appContext: Context) : RecognitionCoreImpl {
    private val mAppContext: Context
    private val mMainThreadHandler: Handler
    override val cardFrameRect = Rect(30, 432, 30 + 660, 432 + 416)
    private var mDisplayConfiguration: DisplayConfiguration = DisplayConfigurationImpl()
    private var mStatusListener: RecognitionStatusListener? = null
    private var mTorchStatusListener: TorchStatusListenerHandler? = null

    init {
        nativeInit()
        mAppContext = appContext.applicationContext
        try {
            deploy()
        } catch (e: IOException) {
            Log.e("CardRecognizerCore", "initialization failed", e)
        }
        mMainThreadHandler = Handler(Looper.getMainLooper(), Handler.Callback { msg ->
            when (msg.what) {
                MESSAGE_RESULT_RECEIVED -> {
                    if (mStatusListener != null) {
                        val result = msg.obj as RecognitionResult
                        mStatusListener?.onRecognitionComplete(result)
                    }
                    return@Callback true
                }
                MESSAGE_CARD_IMAGE_RECEIVED -> {
                    if (mStatusListener != null) {
                        val bitmap = msg.obj as Bitmap
                        mStatusListener?.onCardImageReceived(bitmap)
                    }
                    return@Callback true
                }
            }
            false
        })
    }

    private class TorchStatusListenerHandler(looper: Looper?, listener: TorchStatusListener) :
        Handler(looper!!) {
        val mListener: TorchStatusListener = listener

        fun sendStatusChanged(turnTorchOn: Boolean) {
            removeMessages(MESSAGE_TORCH_STATUS_CHANGED)
            sendMessage(
                Message.obtain(
                    this, MESSAGE_TORCH_STATUS_CHANGED, if (turnTorchOn) 1 else 0, 0
                )
            )
        }

        fun stop() {
            removeMessages(MESSAGE_TORCH_STATUS_CHANGED)
        }

        override fun handleMessage(msg: Message) {
            when (msg.what) {
                MESSAGE_TORCH_STATUS_CHANGED -> mListener.onTorchStatusChanged(msg.arg1 != 0)
            }
            super.handleMessage(msg)
        }

        companion object {
            private const val MESSAGE_TORCH_STATUS_CHANGED = 3
        }
    }

    @Throws(IOException::class)
    fun deploy() {
        val dataHelper = NeuroDataHelper(mAppContext)
        dataHelper.unpackAssets()
        nativeSetDataPath(dataHelper.dataBasePath.absolutePath)
        nativeDeploy()
    }

    override fun setStatusListener(listener: RecognitionStatusListener?) {
        mStatusListener = listener
    }

    override fun setTorchStatus(isTurnedOn: Boolean) {
        nativeSetTorchStatus(isTurnedOn)
    }

    override fun setTorchListener(listener: TorchStatusListener?) {
        synchronized(this) {
            if (mTorchStatusListener != null && mTorchStatusListener?.mListener === listener) {
                return
            }
            if (mTorchStatusListener != null) {
                mTorchStatusListener?.stop()
                mTorchStatusListener = null
            }
            if (listener != null) {
                mTorchStatusListener = TorchStatusListenerHandler(Looper.myLooper(), listener)
            }
        }
    }

    @Synchronized
    override fun setRecognitionMode(@RecognitionMode mode: Int) {
        nativeSetRecognitionMode(mode)
    }

    @Synchronized
    override fun setDisplayConfiguration(configuration: DisplayConfiguration) {
        mDisplayConfiguration = configuration
        nativeSetOrientation(mDisplayConfiguration.nativeDisplayRotation)
        nativeCalcWorkingArea(1280, 720, 32, cardFrameRect)
    }

    @DetectedBorderFlags
    @Synchronized
    override fun processFrameYV12(width: Int, height: Int, buffer: ByteArray?): Int {
        val orientation = mDisplayConfiguration.getPreprocessFrameRotation(width, height)
        return if (orientation == -1) 0 else nativeProcessFrameYV12(
            width, height, orientation, buffer
        )
    }

    override fun resetResult() {
        nativeResetResult()
    }

    override var isIdle: Boolean
        get() = nativeIsIdle()
        set(isIdle) {
            nativeSetIdle(isIdle)
        }

    @Throws(Throwable::class)
    protected fun finalize() {
        nativeDestroy()
    }

    external fun nativeSetDataPath(path: String?)
    external fun nativeDeploy()
    external fun nativeSetRecognitionMode(@RecognitionMode recognitionMode: Int)
    external fun nativeSetIdle(idle: Boolean)
    external fun nativeSetTorchStatus(isTurnedOn: Boolean)
    external fun nativeIsIdle(): Boolean
    external fun nativeCalcWorkingArea(
        frameWidth: Int, frameHeight: Int, captureAreaWidth: Int, dstRect: Rect?
    )

    external fun nativeInit()
    external fun nativeDestroy()
    external fun nativeSetOrientation(workAreaOrientation: Int)
    external fun nativeResetResult()

    @DetectedBorderFlags
    external fun nativeProcessFrameYV12(
        width: Int, height: Int, rotation: Int, buffer: ByteArray?
    ): Int

    companion object {
        private const val MESSAGE_RESULT_RECEIVED = 1
        private const val MESSAGE_CARD_IMAGE_RECEIVED = 2

        @SuppressLint("StaticFieldLeak")
        @Volatile
        private var sInstance: RecognitionCoreNdk? = null
        fun getInstance(context: Context): RecognitionCoreNdk {
            if (sInstance == null) sInstance = RecognitionCoreNdk(context.applicationContext)
            return sInstance!!
        }

        init {
            System.loadLibrary("c++_shared")
            System.loadLibrary("cardrecognizer")
        }

        // Called from native thread.
        @Keep
        @WorkerThread
        @JvmStatic
        private fun onRecognitionResultReceived(
            isFirst: Boolean,
            isFinal: Boolean,
            number: String,
            date: String,
            name: String,
            nameRaw: String,
            cardImage: Bitmap,
            numberRectX: Int,
            numberRectY: Int,
            numberRectWidth: Int,
            numberRectHeight: Int
        ) {
            if (sInstance == null) return
            val numberRect: Rect? = if (numberRectWidth != 0 && numberRectHeight != 0) {
                Rect(
                    numberRectX,
                    numberRectY,
                    numberRectX + numberRectWidth,
                    numberRectY + numberRectHeight
                )
            } else {
                null
            }
            val result = RecognitionResult.Builder().setIsFirst(isFirst).setIsFinal(isFinal)
                .setNumber(number).setName(name).setDate(date).setNameRaw(nameRaw)
                .setNumberImageRect(numberRect).setCardImage(cardImage).build()

            sInstance?.let {
                val msg = Message.obtain(it.mMainThreadHandler, MESSAGE_RESULT_RECEIVED, result)
                msg.sendToTarget()
            }
        }

        // Called from native thread.
        @Keep
        @JvmStatic
        @WorkerThread
        private fun onCardImageReceived(cardImage: Bitmap) {
            sInstance?.let {
                val msg = Message.obtain(
                    it.mMainThreadHandler, MESSAGE_CARD_IMAGE_RECEIVED, cardImage
                )
                msg.sendToTarget()
            }
        }

        @Keep
        @JvmStatic
        @WorkerThread // Called from native thread.
        private fun onTorchStatusChanged(status: Boolean) {
            /// of turn torch on
            synchronized(RecognitionCoreNdk::class.java) {
                sInstance?.let {
                    synchronized(it) {
                        it.mTorchStatusListener?.sendStatusChanged(status)
                    }
                }

            }
        }

    }
}