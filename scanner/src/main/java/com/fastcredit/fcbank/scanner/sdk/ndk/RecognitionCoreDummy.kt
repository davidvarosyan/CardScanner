@file:Suppress("UNUSED_PARAMETER")

package com.fastcredit.fcbank.scanner.sdk.ndk

import android.graphics.Rect

internal class RecognitionCoreDummy : RecognitionCoreImpl {
    override val cardFrameRect = Rect(30, 432, 30 + 660, 432 + 416)
    fun deploy() {}
    override fun setStatusListener(listener: RecognitionStatusListener?) {}
    override fun setTorchStatus(isTurnedOn: Boolean) {}
    override fun setTorchListener(listener: TorchStatusListener?) {}
    override fun setRecognitionMode(mode: Int) {}
    override fun setDisplayConfiguration(configuration: DisplayConfiguration) {}
    override fun processFrameYV12(width: Int, height: Int, buffer: ByteArray?): Int {
        return 0
    }

    override fun resetResult() {}
    override var isIdle: Boolean
        get() = true
        set(isIdle) {}
}