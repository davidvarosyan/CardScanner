package com.fastcredit.fcbank.scanner.sdk.utils

import androidx.annotation.RestrictTo
import com.fastcredit.fcbank.scanner.BuildConfig

@RestrictTo(RestrictTo.Scope.LIBRARY)
object Constants {
    @JvmField
    val DEBUG = BuildConfig.DEBUG
    const val ASSETS_DIR = "cardrecognizer"
    const val MODEL_DIR = "cardrecognizer/model"
    const val NEURO_DATA_VERSION = 9
    const val PAYCARDS_URL = "https://pay.cards"
}