package com.fastcredit.fcbank.scanner.sdk.ndk

import androidx.annotation.IntRange
import com.fastcredit.fcbank.scanner.sdk.ndk.RecognitionConstants.WorkAreaOrientation

interface DisplayConfiguration {
    @get:WorkAreaOrientation
    val nativeDisplayRotation: Int

    @IntRange(from = 0, to = 360)
    fun getPreprocessFrameRotation(width: Int, height: Int): Int
}