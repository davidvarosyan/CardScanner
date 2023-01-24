@file:Suppress("UNUSED_PARAMETER")

package com.fastcredit.fcbank.scanner.sdk.utils

import android.content.Context
import android.graphics.Typeface

class Fonts private constructor(context: Context) {
    companion object {
        @Volatile
        private var sCardFont: Typeface? = null

        @JvmStatic
        fun getCardFont(context: Context): Typeface? {
            if (sCardFont == null) {
                synchronized(Fonts::class.java) {
                    if (sCardFont == null) sCardFont = Typeface.createFromAsset(
                        context.assets,
                        Constants.ASSETS_DIR + "/fonts/OCRAStd.otf"
                    )
                }
            }
            return sCardFont
        }
    }
}