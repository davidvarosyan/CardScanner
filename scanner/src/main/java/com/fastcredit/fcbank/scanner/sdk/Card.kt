@file:Suppress("MemberVisibilityCanBePrivate")

package com.fastcredit.fcbank.scanner.sdk

import android.os.Parcel
import android.os.Parcelable
import com.fastcredit.fcbank.scanner.sdk.utils.CardUtils.getCardNumberRedacted
import java.io.Serializable

open class Card : Serializable, Parcelable {
    /**
     * @return card number (only digits)
     */
    val cardNumber: String?

    /**
     * @return card holder name
     */
    val cardHolderName: String?

    /**
     * @return card expiration date in "MM/yy" format
     */
    val expirationDate: String?

    constructor(number: String?, holder: String?, expirationDate: String?) {
        cardNumber = number
        cardHolderName = holder
        this.expirationDate = expirationDate
    }

    val cardNumberRedacted: String
        get() = getCardNumberRedacted(cardNumber)

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || javaClass != other.javaClass) return false
        val card = other as Card
        if (if (cardNumber != null) cardNumber != card.cardNumber else card.cardNumber != null) return false
        if (if (cardHolderName != null) cardHolderName != card.cardHolderName else card.cardHolderName != null) return false
        return if (expirationDate != null) expirationDate == card.expirationDate else card.expirationDate == null
    }

    override fun hashCode(): Int {
        var result = cardNumber?.hashCode() ?: 0
        result = 31 * result + (cardHolderName?.hashCode() ?: 0)
        result = 31 * result + (expirationDate?.hashCode() ?: 0)
        return result
    }

    override fun toString(): String {
        return "Card{" +
                "mCardNumber='" + cardNumberRedacted + '\'' +
                ", mCardHolder='" + cardHolderName + '\'' +
                ", mExpirationDate='" + expirationDate + '\'' +
                '}'
    }

    override fun describeContents(): Int {
        return 0
    }

    override fun writeToParcel(dest: Parcel, flags: Int) {
        dest.writeString(cardNumber)
        dest.writeString(cardHolderName)
        dest.writeString(expirationDate)
    }

    protected constructor(`in`: Parcel) {
        cardNumber = `in`.readString()
        cardHolderName = `in`.readString()
        expirationDate = `in`.readString()
    }

    companion object {
        private const val serialVersionUID = 1L

        @JvmField
        val CREATOR: Parcelable.Creator<Card?> = object : Parcelable.Creator<Card?> {
            override fun createFromParcel(source: Parcel): Card {
                return Card(source)
            }

            override fun newArray(size: Int): Array<Card?> {
                return arrayOfNulls(size)
            }
        }
    }
}