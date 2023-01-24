package com.fastcredit.fcbank.scanner.sdk.ui

import android.os.Parcel
import android.os.Parcelable
import androidx.annotation.RestrictTo

@RestrictTo(RestrictTo.Scope.LIBRARY)
class ScanCardRequest : Parcelable {
    val isSoundEnabled: Boolean
    val isScanExpirationDateEnabled: Boolean
    val isScanCardHolderEnabled: Boolean
    val isGrabCardImageEnabled: Boolean

    constructor(
        enableSound: Boolean,
        scanExpirationDate: Boolean,
        scanCardHolder: Boolean,
        grabCardImage: Boolean
    ) {
        isSoundEnabled = enableSound
        isScanExpirationDateEnabled = scanExpirationDate
        isScanCardHolderEnabled = scanCardHolder
        isGrabCardImageEnabled = grabCardImage
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || javaClass != other.javaClass) return false
        val that = other as ScanCardRequest
        if (isSoundEnabled != that.isSoundEnabled) return false
        if (isScanExpirationDateEnabled != that.isScanExpirationDateEnabled) return false
        return if (isScanCardHolderEnabled != that.isScanCardHolderEnabled) false else isGrabCardImageEnabled == that.isGrabCardImageEnabled
    }

    override fun hashCode(): Int {
        var result = if (isSoundEnabled) 1 else 0
        result = 31 * result + if (isScanExpirationDateEnabled) 1 else 0
        result = 31 * result + if (isScanCardHolderEnabled) 1 else 0
        result = 31 * result + if (isGrabCardImageEnabled) 1 else 0
        return result
    }

    override fun describeContents(): Int {
        return 0
    }

    override fun writeToParcel(dest: Parcel, flags: Int) {
        dest.writeByte(if (isSoundEnabled) 1.toByte() else 0.toByte())
        dest.writeByte(if (isScanExpirationDateEnabled) 1.toByte() else 0.toByte())
        dest.writeByte(if (isScanCardHolderEnabled) 1.toByte() else 0.toByte())
        dest.writeByte(if (isGrabCardImageEnabled) 1.toByte() else 0.toByte())
    }

    private constructor(`in`: Parcel) {
        isSoundEnabled = `in`.readByte().toInt() != 0
        isScanExpirationDateEnabled = `in`.readByte().toInt() != 0
        isScanCardHolderEnabled = `in`.readByte().toInt() != 0
        isGrabCardImageEnabled = `in`.readByte().toInt() != 0
    }

    companion object {
        const val DEFAULT_ENABLE_SOUND = true
        const val DEFAULT_SCAN_EXPIRATION_DATE = true
        const val DEFAULT_SCAN_CARD_HOLDER = true
        const val DEFAULT_GRAB_CARD_IMAGE = false
        val default = ScanCardRequest(
            DEFAULT_ENABLE_SOUND,
            DEFAULT_SCAN_EXPIRATION_DATE,
            DEFAULT_SCAN_CARD_HOLDER,
            DEFAULT_GRAB_CARD_IMAGE
        )

        @JvmField
        val CREATOR: Parcelable.Creator<ScanCardRequest?> =
            object : Parcelable.Creator<ScanCardRequest?> {
                override fun createFromParcel(source: Parcel): ScanCardRequest {
                    return ScanCardRequest(source)
                }

                override fun newArray(size: Int): Array<ScanCardRequest?> {
                    return arrayOfNulls(size)
                }
            }
    }
}