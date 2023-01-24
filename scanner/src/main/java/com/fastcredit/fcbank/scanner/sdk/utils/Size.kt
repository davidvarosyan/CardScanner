package com.fastcredit.fcbank.scanner.sdk.utils

import kotlin.math.max
import kotlin.math.min

class Size(val width: Int, val height: Int) : Comparable<Size> {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || javaClass != other.javaClass) return false
        val size = other as Size
        return if (width != size.width) false else height == size.height
    }

    override fun hashCode(): Int {
        var result = width
        result = 31 * result + height
        return result
    }

    override fun compareTo(other: Size): Int {
        if (max(width, height) > max(other.width, other.height) &&
            min(width, height) > min(other.width, other.height)
        ) {
            return -1
        } else if (max(width, height) < max(other.width, other.height) &&
            min(width, height) < min(other.width, other.height)
        ) {
            return 1
        }
        return 0
    }

    override fun toString(): String {
        return width.toString() + "x" + height
    }
}