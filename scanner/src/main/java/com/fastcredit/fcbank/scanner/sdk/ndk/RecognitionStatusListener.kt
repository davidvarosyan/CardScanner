package com.fastcredit.fcbank.scanner.sdk.ndk

import android.graphics.Bitmap
import androidx.annotation.RestrictTo

@RestrictTo(RestrictTo.Scope.LIBRARY)
interface RecognitionStatusListener {
    fun onRecognitionComplete(result: RecognitionResult)
    fun onCardImageReceived(bitmap: Bitmap)
}