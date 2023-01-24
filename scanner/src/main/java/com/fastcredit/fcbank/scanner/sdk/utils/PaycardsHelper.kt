@file:Suppress("unused")

package com.fastcredit.fcbank.scanner.sdk.utils

import android.content.Context
import com.fastcredit.fcbank.scanner.sdk.camera.RecognitionAvailabilityChecker.doCheck
import com.fastcredit.fcbank.scanner.sdk.camera.RecognitionCoreUtils.deployRecognitionCoreSync
import com.fastcredit.fcbank.scanner.sdk.camera.RecognitionCoreUtils.isRecognitionCoreDeployRequired
import java.util.concurrent.atomic.AtomicBoolean

object PaycardsHelper {
    private val deployRecognitionCoreActive = AtomicBoolean()
    fun isScanCardSupported(context: Context?): Boolean {
        val checkResult = doCheck(
            context!!
        )
        return (checkResult.isPassed
                || checkResult.isAdditionalCheckRequired
                || checkResult.isFailedOnCameraPermission)
    }

    fun startDeployRecognitionCore(context: Context) {
        if (!isRecognitionCoreDeployRequired(context)) return
        if (deployRecognitionCoreActive.get()) return
        val appContext = context.applicationContext
        object : Thread() {
            override fun run() {
                super.run()
                if (deployRecognitionCoreActive.compareAndSet(false, true)) {
                    deployRecognitionCoreSync(appContext)
                    deployRecognitionCoreActive.set(true)
                }
            }
        }.start()
    }
}