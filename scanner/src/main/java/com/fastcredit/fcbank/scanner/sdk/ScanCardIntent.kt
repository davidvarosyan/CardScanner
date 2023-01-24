@file:Suppress("unused")

package com.fastcredit.fcbank.scanner.sdk

import android.app.Activity
import android.content.Context
import android.content.Intent
import androidx.annotation.IntDef
import androidx.annotation.RestrictTo
import com.fastcredit.fcbank.scanner.sdk.ui.ScanCardActivity
import com.fastcredit.fcbank.scanner.sdk.ui.ScanCardRequest

object ScanCardIntent {
    const val RESULT_CODE_ERROR = Activity.RESULT_FIRST_USER
    const val RESULT_PAYCARDS_CARD = "RESULT_PAYCARDS_CARD"
    const val RESULT_CARD_IMAGE = "RESULT_CARD_IMAGE"
    const val RESULT_CANCEL_REASON = "RESULT_CANCEL_REASON"
    const val BACK_PRESSED = 1
    const val ADD_MANUALLY_PRESSED = 2

    @RestrictTo(RestrictTo.Scope.LIBRARY)
    const val KEY_SCAN_CARD_REQUEST =
        "com.fastcredit.fcbank.scanner.ScanCardActivity.SCAN_CARD_REQUEST"

    @Retention(AnnotationRetention.SOURCE)
    @IntDef(value = [BACK_PRESSED, ADD_MANUALLY_PRESSED])
    annotation class CancelReason
    class Builder(private val mContext: Context) {
        private var mEnableSound = ScanCardRequest.DEFAULT_ENABLE_SOUND
        private var mScanExpirationDate = ScanCardRequest.DEFAULT_SCAN_EXPIRATION_DATE
        private var mScanCardHolder = ScanCardRequest.DEFAULT_SCAN_CARD_HOLDER
        private var mGrabCardImage = ScanCardRequest.DEFAULT_GRAB_CARD_IMAGE

        /**
         * Scan expiration date. Default: **true**
         */
        fun setScanExpirationDate(scanExpirationDate: Boolean): Builder {
            mScanExpirationDate = scanExpirationDate
            return this
        }

        /**
         * Scan expiration date. Default: **true**
         */
        fun setScanCardHolder(scanCardHolder: Boolean): Builder {
            mScanCardHolder = scanCardHolder
            return this
        }

        /**
         * Enables or disables sounds in the library.<Br></Br>
         * Default: **enabled**
         */
        fun setSoundEnabled(enableSound: Boolean): Builder {
            mEnableSound = enableSound
            return this
        }

        /**
         * Defines if the card image will be captured.
         * @param enable Defines if the card image will be captured. Default: **false**
         */
        fun setSaveCard(enable: Boolean): Builder {
            mGrabCardImage = enable
            return this
        }

        fun build(): Intent {
            val intent = Intent(mContext, ScanCardActivity::class.java)
            val request = ScanCardRequest(
                mEnableSound, mScanExpirationDate,
                mScanCardHolder, mGrabCardImage
            )
            intent.putExtra(KEY_SCAN_CARD_REQUEST, request)
            return intent
        }
    }
}