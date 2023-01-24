@file:Suppress("DEPRECATION", "unused", "MemberVisibilityCanBePrivate")

package com.fastcredit.fcbank.scanner.sdk.camera

import android.graphics.Rect
import android.hardware.Camera
import android.hardware.Camera.CameraInfo
import android.util.Log
import androidx.annotation.RestrictTo
import com.fastcredit.fcbank.scanner.sdk.utils.Constants
import java.util.*
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

@RestrictTo(RestrictTo.Scope.LIBRARY)
object CameraConfigurationUtils {
    private const val MAX_EXPOSURE_COMPENSATION = 1.5f
    private const val MIN_EXPOSURE_COMPENSATION = 0.0f
    private val DBG = Constants.DEBUG
    private const val TAG = "CameraConfig"
    private const val ENABLE_EXPOSURE = false
    private const val ENABLE_METERING = false
    fun createCamera(): Camera? {
        val numberOfCameras = Camera.getNumberOfCameras()
        val cameraInfo = CameraInfo()
        for (i in 0 until numberOfCameras) {
            Camera.getCameraInfo(i, cameraInfo)
            if (CameraInfo.CAMERA_FACING_BACK == cameraInfo.facing) {
                return Camera.open(i)
            }
        }
        return if (0 < numberOfCameras) {
            Camera.open(0)
        } else null
    }

    fun isFlashSupported(camera: Camera): Boolean {
        val params = camera.parameters
        return params.supportedFlashModes != null
    }

    fun setFlashLight(camera: Camera?, enableFlash: Boolean): Boolean {
        camera?.let {
            val params = it.parameters
            val flashes = params.supportedFlashModes
            if (null != flashes) {
                if (enableFlash) {
                    if (flashes.contains(Camera.Parameters.FLASH_MODE_TORCH)) {
                        params.flashMode = Camera.Parameters.FLASH_MODE_TORCH
                    } else if (flashes.contains(Camera.Parameters.FLASH_MODE_ON)) {
                        params.flashMode = Camera.Parameters.FLASH_MODE_ON
                    } else {
                        return false
                    }
                } else {
                    params.flashMode = Camera.Parameters.FLASH_MODE_OFF
                }
            }
            setBestExposure(params, enableFlash)
            camera.parameters = params
            return enableFlash
        }

        return false
    }

    private val FOCUS_LIST = listOf(
        Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE,
        Camera.Parameters.FOCUS_MODE_AUTO,
        Camera.Parameters.FOCUS_MODE_MACRO,
        Camera.Parameters.FOCUS_MODE_EDOF
    )

    @JvmOverloads
    fun initAutoFocus(cameraParameters: Camera.Parameters, enableContinuousFocus: Boolean = true) {
        /*
        // FLASH_MODE_TORCH does not work with SCENE_MODE_BARCODE
        final List<String> supportedSceneModes = cameraParameters.getSupportedSceneModes();
        if (null != supportedSceneModes) {
            String resultSceneMode = null;
            if (supportedSceneModes.contains(Camera.Parameters.SCENE_MODE_BARCODE)) {
                resultSceneMode = Camera.Parameters.SCENE_MODE_BARCODE;
            }
            if (null != resultSceneMode) {
                cameraParameters.setSceneMode(resultSceneMode);
                return true;
            }
        } */
        val supportedFocusModes = cameraParameters.supportedFocusModes ?: return
        val focusList: MutableList<String> = ArrayList(FOCUS_LIST)
        if (!enableContinuousFocus) {
            focusList.remove(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE)
            focusList.add(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE)
        }
        for (focusMode in focusList) {
            if (supportedFocusModes.contains(focusMode)) {
                cameraParameters.focusMode = focusMode
                break
            }
        }
    }

    fun initWhiteBalance(parameters: Camera.Parameters) {
        val whiteBalance = parameters.supportedWhiteBalance
        if (whiteBalance != null && whiteBalance.contains(Camera.Parameters.WHITE_BALANCE_AUTO)) {
            parameters.whiteBalance = Camera.Parameters.WHITE_BALANCE_AUTO
        }
    }

    fun setBestExposure(parameters: Camera.Parameters, lightOn: Boolean) {
        if (!ENABLE_EXPOSURE) return
        val minExposure = parameters.minExposureCompensation
        val maxExposure = parameters.maxExposureCompensation
        val step = parameters.exposureCompensationStep
        if ((minExposure != 0 || maxExposure != 0) && step > 0.0f) {
            // Set low when light is on
            val targetCompensation =
                if (lightOn) MIN_EXPOSURE_COMPENSATION else MAX_EXPOSURE_COMPENSATION
            var compensationSteps = (targetCompensation / step).roundToInt()
            val actualCompensation = step * compensationSteps
            // Clamp value:
            compensationSteps = max(min(compensationSteps, maxExposure), minExposure)
            if (parameters.exposureCompensation == compensationSteps) {
                if (DBG) Log.i(
                    "CameraConfig",
                    "Exposure compensation already set to $compensationSteps / $actualCompensation"
                )
            } else {
                if (DBG) Log.i(
                    "CameraConfig",
                    "Setting exposure compensation to $compensationSteps / $actualCompensation"
                )
                parameters.exposureCompensation = compensationSteps
            }
        } else {
            if (DBG) Log.i("CameraConfig", "Camera does not support exposure compensation")
        }
    }

    fun setMetering(parameters: Camera.Parameters) {
        if (!ENABLE_METERING) return
        if (parameters.isVideoStabilizationSupported) {
            parameters.videoStabilization = false
        }
        setFocusArea(parameters)
        setMeteringArea(parameters)
    }

    fun setFocusArea(parameters: Camera.Parameters) {
        if (parameters.maxNumFocusAreas > 0) {
            Log.i(TAG, "Old focus areas: " + toString(parameters.focusAreas))
            val cardArea = buildCardArea()
            Log.i(TAG, "Setting focus area to : " + toString(cardArea))
            parameters.focusAreas = cardArea
        } else {
            Log.i(TAG, "Device does not support focus areas")
        }
    }

    fun setMeteringArea(parameters: Camera.Parameters) {
        if (parameters.maxNumMeteringAreas > 0) {
            Log.i(TAG, "Old metering areas: " + parameters.meteringAreas)
            val cardArea = buildCardArea()
            Log.i(TAG, "Setting metering area to : " + toString(cardArea))
            parameters.meteringAreas = cardArea
        } else {
            Log.i(TAG, "Device does not support metering areas")
        }
    }

    private fun buildCardArea(): List<Camera.Area> {
        //Rect rect = new Rect(-917, 32, 917, 325);
        val rect = Rect(-10, -10, 10, 10)
        return listOf(Camera.Area(rect, 1))
    }

    private fun toString(areas: Iterable<Camera.Area>?): String? {
        if (areas == null) {
            return null
        }
        val result = StringBuilder()
        for (area in areas) {
            result.append(area.rect).append(':').append(area.weight).append(' ')
        }
        return result.toString()
    }
}