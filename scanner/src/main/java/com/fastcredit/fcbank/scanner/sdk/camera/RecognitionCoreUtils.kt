@file:Suppress("unused")

package com.fastcredit.fcbank.scanner.sdk.camera

import android.content.Context
import androidx.annotation.RestrictTo
import com.fastcredit.fcbank.scanner.sdk.ndk.RecognitionCore
import java.util.concurrent.atomic.AtomicBoolean

@RestrictTo(RestrictTo.Scope.LIBRARY)
object RecognitionCoreUtils {
    private val deployRecognitionCoreActive = AtomicBoolean()

    @JvmStatic
    fun isRecognitionCoreDeployRequired(context: Context): Boolean {
        return !(!RecognitionAvailabilityChecker.isDeviceHasCamera(context)
                || !RecognitionAvailabilityChecker.isDeviceNewEnough(context)
                || RecognitionCore.isInitialized)
    }

    @JvmStatic
    fun deployRecognitionCoreSync(context: Context) {
        if (!isRecognitionCoreDeployRequired(context)) return
        try {
            RecognitionCore.deploy(context)
        } catch (e: Throwable) {
            // IGNORE
        }
    }

    fun startDeployRecognitionCore(context: Context) {
        if (!isRecognitionCoreDeployRequired(context)) return
        if (deployRecognitionCoreActive.get()) return
        val appContext = context.applicationContext
        object : Thread() {
            override fun run() {
                super.run()
                if (deployRecognitionCoreActive.compareAndSet(false, true)) {
                    try {
                        RecognitionCore.deploy(appContext)
                    } catch (e: Throwable) {
                        // IGNORE
                    }
                    deployRecognitionCoreActive.set(true)
                }
            }
        }.start()
    }

    fun isScanCardSupported(context: Context): Boolean {
        val checkResult = RecognitionAvailabilityChecker.doCheck(context)
        return (checkResult.isPassed
                || checkResult.isAdditionalCheckRequired
                || checkResult.isFailedOnCameraPermission)
    }
}