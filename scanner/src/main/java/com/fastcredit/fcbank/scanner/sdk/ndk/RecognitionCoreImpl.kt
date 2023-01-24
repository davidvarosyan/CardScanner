package com.fastcredit.fcbank.scanner.sdk.ndk

import android.graphics.Rect
import androidx.annotation.RestrictTo
import com.fastcredit.fcbank.scanner.sdk.ndk.RecognitionConstants.DetectedBorderFlags
import com.fastcredit.fcbank.scanner.sdk.ndk.RecognitionConstants.RecognitionMode

@RestrictTo(RestrictTo.Scope.LIBRARY)
interface RecognitionCoreImpl {
    fun setStatusListener(listener: RecognitionStatusListener?)
    fun setTorchStatus(isTurnedOn: Boolean)
    fun setTorchListener(listener: TorchStatusListener?)
    fun setRecognitionMode(@RecognitionMode mode: Int)
    fun setDisplayConfiguration(configuration: DisplayConfiguration)
    val cardFrameRect: Rect

    @DetectedBorderFlags
    fun processFrameYV12(width: Int, height: Int, buffer: ByteArray?): Int
    fun resetResult()
    var isIdle: Boolean
}