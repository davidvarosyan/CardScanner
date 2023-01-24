package com.fastcredit.fcbank.scanner.sdk.ndk

import android.content.Context
import android.graphics.Rect
import android.util.Log
import androidx.annotation.RestrictTo
import com.fastcredit.fcbank.scanner.sdk.ndk.RecognitionConstants.DetectedBorderFlags
import com.fastcredit.fcbank.scanner.sdk.ndk.RecognitionConstants.RecognitionMode
import java.io.IOException

@RestrictTo(RestrictTo.Scope.LIBRARY)
class RecognitionCore {
    val isDeviceSupported: Boolean
        get() = sImpl !is RecognitionCoreDummy

    fun setStatusListener(listener: RecognitionStatusListener?) {
        sImpl.setStatusListener(listener)
    }

    fun setTorchStatus(isTurnedOn: Boolean) {
        sImpl.setTorchStatus(isTurnedOn)
    }

    fun setTorchListener(listener: TorchStatusListener?) {
        sImpl.setTorchListener(listener)
    }

    fun setRecognitionMode(@RecognitionMode mode: Int) {
        sImpl.setRecognitionMode(mode)
    }

    fun setDisplayConfiguration(configuration: DisplayConfiguration) {
        sImpl.setDisplayConfiguration(configuration)
    }

    val cardFrameRect: Rect
        get() = sImpl.cardFrameRect

    @DetectedBorderFlags
    fun processFrameYV12(width: Int, height: Int, buffer: ByteArray?): Int {
        return sImpl.processFrameYV12(width, height, buffer)
    }

    fun resetResult() {
        sImpl.resetResult()
    }

    var isIdle: Boolean
        get() = sImpl.isIdle
        set(isIdle) {
            sImpl.isIdle = isIdle
        }

    companion object {
        @Volatile
        private var sInstance: RecognitionCore? = null

        @Volatile
        private var sImpl: RecognitionCoreImpl = RecognitionCoreDummy()
        @JvmStatic
        fun getInstance(context: Context): RecognitionCore {
            try {
                deploy(context)
            } catch (e: IOException) {
                Log.e("RecognitionCore", "initialization failed", e)
            } catch (e: UnsatisfiedLinkError) {
                Log.e("RecognitionCore", "initialization failed", e)
            }
            return sInstance!!
        }

        @Throws(IOException::class, UnsatisfiedLinkError::class)
        fun deploy(context: Context) {
            if (sInstance == null) {
                synchronized(RecognitionCore::class.java) {
                    if (sInstance == null) {
                        try {
                            val ndkImpl = RecognitionCoreNdk.getInstance(context.applicationContext)
                            ndkImpl.deploy()
                            sImpl = ndkImpl
                        } finally {
                            sInstance = RecognitionCore()
                        }
                    }
                }
            }
        }

        val isInitialized: Boolean
            get() {
                synchronized(RecognitionCore::class.java) { return sInstance != null }
            }
    }
}