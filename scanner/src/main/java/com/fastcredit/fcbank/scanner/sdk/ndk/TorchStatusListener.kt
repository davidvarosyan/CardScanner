package com.fastcredit.fcbank.scanner.sdk.ndk

import androidx.annotation.RestrictTo

@RestrictTo(RestrictTo.Scope.LIBRARY)
interface TorchStatusListener {
    fun onTorchStatusChanged(turnTorchOn: Boolean)
}