package com.fastcredit.fcbank.scanner.sdk.ndk

import androidx.annotation.IntDef
import androidx.annotation.RestrictTo

@RestrictTo(RestrictTo.Scope.LIBRARY)
object RecognitionConstants {
    const val DETECTED_BORDER_TOP = 1
    const val DETECTED_BORDER_BOTTOM = 1 shl 1
    const val DETECTED_BORDER_LEFT = 1 shl 2
    const val DETECTED_BORDER_RIGHT = 1 shl 3
    const val RECOGNIZER_MODE_NUMBER = 1
    const val RECOGNIZER_MODE_DATE = 1 shl 1
    const val RECOGNIZER_MODE_NAME = 1 shl 2
    const val RECOGNIZER_MODE_GRAB_CARD_IMAGE = 1 shl 3
    const val WORK_AREA_ORIENTATION_UNKNOWN = 0
    const val WORK_AREA_ORIENTATION_PORTAIT = 1
    const val WORK_AREA_ORIENTATION_PORTAIT_UPSIDE_DOWN = 2
    const val WORK_AREA_ORIENTATION_LANDSCAPE_RIGHT = 3
    const val WORK_AREA_ORIENTATION_LANDSCAPE_LEFT = 4

    @Retention(AnnotationRetention.SOURCE)
    @IntDef(
        flag = true,
        value = [DETECTED_BORDER_TOP, DETECTED_BORDER_BOTTOM, DETECTED_BORDER_LEFT, DETECTED_BORDER_RIGHT]
    )
    annotation class DetectedBorderFlags

    @Retention(AnnotationRetention.SOURCE)
    @IntDef(
        flag = true,
        value = [RECOGNIZER_MODE_NUMBER, RECOGNIZER_MODE_DATE, RECOGNIZER_MODE_NAME, RECOGNIZER_MODE_GRAB_CARD_IMAGE]
    )
    annotation class RecognitionMode

    @Retention(AnnotationRetention.SOURCE)
    @IntDef(value = [WORK_AREA_ORIENTATION_UNKNOWN, WORK_AREA_ORIENTATION_PORTAIT, WORK_AREA_ORIENTATION_PORTAIT_UPSIDE_DOWN, WORK_AREA_ORIENTATION_LANDSCAPE_RIGHT, WORK_AREA_ORIENTATION_LANDSCAPE_LEFT])
    internal annotation class WorkAreaOrientation
}