@file:Suppress("unused", "DEPRECATION")

package com.fastcredit.fcbank.scanner.sdk.ndk

import android.graphics.Bitmap
import android.graphics.Rect
import android.os.Parcel
import android.os.Parcelable
import androidx.annotation.RestrictTo

@RestrictTo(RestrictTo.Scope.LIBRARY)
class RecognitionResult : Parcelable {
    val number: String?
    val date: String?
    val name: String?
    val nameRaw: String?
    val numberImageRect: Rect?
    val cardImage: Bitmap?
    val isFirst: Boolean
    val isFinal: Boolean

    constructor(
        number: String?,
        name: String?,
        date: String?,
        numberImageRect: Rect?,
        nameRaw: String?,
        cardImage: Bitmap?,
        isFirst: Boolean,
        isFinal: Boolean
    ) {
        this.number = number
        this.name = name
        this.date = date
        this.nameRaw = nameRaw
        this.cardImage = cardImage
        this.numberImageRect = numberImageRect
        this.isFirst = isFirst
        this.isFinal = isFinal
    }

    private constructor(builder: Builder) {
        cardImage = builder.cardImage
        number = builder.number
        date = builder.date
        name = builder.name
        nameRaw = builder.nameRaw
        numberImageRect = builder.numberImageRect
        isFirst = builder.isFirst
        isFinal = builder.isFinal
    }

    fun newBuilder(): Builder {
        return Builder(this)
    }

    val cardImageWidth: Int
        get() = cardImage?.width ?: 0
    val cardImageHeight: Int
        get() = cardImage?.height ?: 0

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || javaClass != other.javaClass) return false
        val that = other as RecognitionResult
        if (isFirst != that.isFirst) return false
        if (isFinal != that.isFinal) return false
        if (if (number != null) number != that.number else that.number != null) return false
        if (if (date != null) date != that.date else that.date != null) return false
        if (if (name != null) name != that.name else that.name != null) return false
        if (if (nameRaw != null) nameRaw != that.nameRaw else that.nameRaw != null) return false
        if (if (numberImageRect != null) numberImageRect != that.numberImageRect else that.numberImageRect != null) return false
        return if (cardImage != null) cardImage == that.cardImage else that.cardImage == null
    }

    override fun hashCode(): Int {
        var result = number?.hashCode() ?: 0
        result = 31 * result + (date?.hashCode() ?: 0)
        result = 31 * result + (name?.hashCode() ?: 0)
        result = 31 * result + (nameRaw?.hashCode() ?: 0)
        result = 31 * result + (numberImageRect?.hashCode() ?: 0)
        result = 31 * result + (cardImage?.hashCode() ?: 0)
        result = 31 * result + if (isFirst) 1 else 0
        result = 31 * result + if (isFinal) 1 else 0
        return result
    }

    override fun describeContents(): Int {
        return 0
    }

    override fun writeToParcel(dest: Parcel, flags: Int) {
        dest.writeInt(if (isFirst) 1 else 0)
        dest.writeInt(if (isFinal) 1 else 0)
        dest.writeString(number)
        dest.writeString(date)
        dest.writeString(name)
        dest.writeString(nameRaw)
        dest.writeParcelable(numberImageRect, 0)
        dest.writeParcelable(cardImage, 0)
    }

    private constructor(`in`: Parcel) {
        isFirst = `in`.readInt() != 0
        isFinal = `in`.readInt() != 0
        number = `in`.readString()
        date = `in`.readString()
        name = `in`.readString()
        nameRaw = `in`.readString()
        numberImageRect = `in`.readParcelable(Rect::class.java.classLoader)
        cardImage = `in`.readParcelable(Bitmap::class.java.classLoader)
    }

    class Builder {
        var isFirst = true
        var isFinal = true
        var cardImage: Bitmap? = null
        var number: String? = null
        var date: String? = null
        var name: String? = null
        var nameRaw: String? = null
        var numberImageRect: Rect? = null

        constructor()
        constructor(copy: RecognitionResult) {
            isFirst = copy.isFirst
            isFinal = copy.isFinal
            cardImage = copy.cardImage
            number = copy.number
            date = copy.date
            name = copy.name
            nameRaw = copy.nameRaw
            numberImageRect = copy.numberImageRect
        }

        fun setCardImage(`val`: Bitmap?): Builder {
            cardImage = `val`
            return this
        }

        fun setNumber(`val`: String?): Builder {
            number = `val`
            return this
        }

        fun setDate(`val`: String?): Builder {
            date = `val`
            return this
        }

        fun setName(`val`: String?): Builder {
            name = `val`
            return this
        }

        fun setNameRaw(`val`: String?): Builder {
            nameRaw = `val`
            return this
        }

        fun setNumberImageRect(`val`: Rect?): Builder {
            numberImageRect = `val`
            return this
        }

        fun setIsFinal(`val`: Boolean): Builder {
            isFinal = `val`
            return this
        }

        fun setIsFirst(`val`: Boolean): Builder {
            isFirst = `val`
            return this
        }

        fun build(): RecognitionResult {
            return RecognitionResult(this)
        }
    }

    companion object {
        private val sEmpty = Builder().setIsFirst(true).build()
        fun empty(): RecognitionResult {
            return sEmpty
        }

        @JvmField
        val CREATOR: Parcelable.Creator<RecognitionResult?> =
            object : Parcelable.Creator<RecognitionResult?> {
                override fun createFromParcel(source: Parcel): RecognitionResult {
                    return RecognitionResult(source)
                }

                override fun newArray(size: Int): Array<RecognitionResult?> {
                    return arrayOfNulls(size)
                }
            }
    }
}