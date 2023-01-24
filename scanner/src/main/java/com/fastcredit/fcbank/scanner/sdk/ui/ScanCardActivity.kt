@file:Suppress("DEPRECATION", "PARAMETER_NAME_CHANGED_ON_OVERRIDE")

package com.fastcredit.fcbank.scanner.sdk.ui

//noinspection SuspiciousImport
import android.R
import android.content.Intent
import android.os.Bundle
import android.os.Parcelable
import android.util.Log
import android.view.WindowManager
import androidx.annotation.RestrictTo
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.fragment.app.Fragment
import com.fastcredit.fcbank.scanner.sdk.Card
import com.fastcredit.fcbank.scanner.sdk.ScanCardIntent
import com.fastcredit.fcbank.scanner.sdk.ScanCardIntent.CancelReason
import com.fastcredit.fcbank.scanner.sdk.camera.RecognitionAvailabilityChecker.doCheck
import com.fastcredit.fcbank.scanner.sdk.camera.RecognitionCoreUtils.isRecognitionCoreDeployRequired
import com.fastcredit.fcbank.scanner.sdk.camera.RecognitionUnavailableException
import com.fastcredit.fcbank.scanner.sdk.ui.ScanCardRequest.Companion.default

class ScanCardActivity : AppCompatActivity(), ScanCardFragment.InteractionListener,
    InitLibraryFragment.InteractionListener {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setFlags(
            WindowManager.LayoutParams.FLAG_SECURE,
            WindowManager.LayoutParams.FLAG_SECURE
        )
        delegate.onPostCreate(null)
        if (savedInstanceState == null) {
            val checkResult = doCheck(this)
            if (checkResult.isFailed
                && !checkResult.isFailedOnCameraPermission
            ) {
                onScanCardFailed(RecognitionUnavailableException(checkResult.message))
            } else {
                if (isRecognitionCoreDeployRequired(this)
                    || checkResult.isFailedOnCameraPermission
                ) {
                    showInitLibrary()
                } else {
                    showScanCard()
                }
            }
        }
    }

    private fun showInitLibrary() {
        val fragment: Fragment = InitLibraryFragment()
        supportFragmentManager.beginTransaction()
            .replace(R.id.content, fragment, InitLibraryFragment.TAG)
            .setCustomAnimations(0, 0)
            .commitNow()
    }

    private fun showScanCard() {
        val fragment: Fragment = ScanCardFragment()
        val args = Bundle(1)
        args.putParcelable(ScanCardIntent.KEY_SCAN_CARD_REQUEST, scanRequest)
        fragment.arguments = args
        supportFragmentManager.beginTransaction()
            .replace(R.id.content, fragment, ScanCardFragment.TAG)
            .setCustomAnimations(0, 0)
            .commitNow()
        ViewCompat.requestApplyInsets(findViewById(R.id.content))
    }

    @RestrictTo(RestrictTo.Scope.LIBRARY)
    override fun onScanCardFailed(e: Exception?) {
        Log.e(TAG, "Scan card failed", RuntimeException("onScanCardFinishedWithError()", e))
        setResult(ScanCardIntent.RESULT_CODE_ERROR)
        finish()
    }

    @RestrictTo(RestrictTo.Scope.LIBRARY)
    override fun onScanCardFinished(card: Card?, cardImage: ByteArray?) {
        val intent = Intent()
        intent.putExtra(ScanCardIntent.RESULT_PAYCARDS_CARD, card as Parcelable?)
        if (cardImage != null) intent.putExtra(ScanCardIntent.RESULT_CARD_IMAGE, cardImage)
        setResult(RESULT_OK, intent)
        finish()
    }

    override fun onInitLibraryFailed(e: Throwable?) {
        Log.e(TAG, "Init library failed", RuntimeException("onInitLibraryFailed()", e))
        setResult(ScanCardIntent.RESULT_CODE_ERROR)
        finish()
    }

    override fun onScanCardCanceled(@CancelReason actionId: Int) {
        val intent = Intent()
        intent.putExtra(ScanCardIntent.RESULT_CANCEL_REASON, actionId)
        setResult(RESULT_CANCELED, intent)
        finish()
    }

    @RestrictTo(RestrictTo.Scope.LIBRARY)
    override fun onInitLibraryComplete() {
        if (isFinishing) return
        showScanCard()
    }

    private val scanRequest: ScanCardRequest
        get() {
            var request =
                intent.getParcelableExtra<ScanCardRequest>(ScanCardIntent.KEY_SCAN_CARD_REQUEST)
            if (request == null) {
                request = default
            }
            return request
        }

    companion object {
        private const val TAG = "ScanCardActivity"
    }
}