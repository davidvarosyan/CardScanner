package com.fastcredit.fcbank.scanner.sdk.utils

import androidx.annotation.RestrictTo

@RestrictTo(RestrictTo.Scope.LIBRARY)
object CardUtils {
    @JvmStatic
    fun prettyPrintCardNumber(cardNumber: CharSequence?): String {
        if (cardNumber.isNullOrEmpty()) {
            return ""
        }

        val stringBuilder = StringBuilder(20)
        var i = 0
        val size = cardNumber.length
        while (i < size) {
            if (size == 16) {
                if (i != 0 && i % 4 == 0) {
                    stringBuilder.append('\u00a0')
                }
            } else if (size == 15) {
                if (i == 4 || i == 10) {
                    stringBuilder.append('\u00a0')
                }
            }
            stringBuilder.append(cardNumber[i])
            ++i
        }
        return stringBuilder.toString()
    }

    @JvmStatic
    fun getCardNumberRedacted(cardNumber: String?): String {
        if (null == cardNumber) {
            return ""
        }
        if (cardNumber.length == 16) {
            val beginNumber = cardNumber.substring(0, 6)
            val endNumber = cardNumber.substring(cardNumber.length - 2, cardNumber.length)
            val stringBuilder = StringBuilder("$beginNumber********$endNumber")
            stringBuilder.insert(4, " ")
            stringBuilder.insert(9, " ")
            stringBuilder.insert(14, " ")
            return stringBuilder.toString()
        } else if (cardNumber.length == 15) {
            val beginNumber = cardNumber.substring(0, 6)
            val endNumber = cardNumber.substring(cardNumber.length - 1, cardNumber.length)
            val stringBuilder = StringBuilder("$beginNumber********$endNumber")
            stringBuilder.insert(4, " ")
            stringBuilder.insert(11, " ")
            return stringBuilder.toString()
        }
        return ""
    }
}