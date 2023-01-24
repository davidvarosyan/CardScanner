@file:Suppress("DEPRECATION")

package com.fastcredit.fcbank.scanner.sdk.ui

import android.content.Context
import android.content.pm.ActivityInfo
import android.graphics.Bitmap
import android.graphics.Color
import android.hardware.Camera
import android.media.AudioManager
import android.media.SoundPool
import android.os.Bundle
import android.text.SpannableString
import android.text.method.LinkMovementMethod
import android.text.style.URLSpan
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.Animation
import android.widget.TextView
import androidx.annotation.RestrictTo
import androidx.fragment.app.Fragment
import com.fastcredit.fcbank.scanner.sdk.Card
import com.fastcredit.fcbank.scanner.sdk.ScanCardIntent
import com.fastcredit.fcbank.scanner.sdk.ScanCardIntent.CancelReason
import com.fastcredit.fcbank.scanner.sdk.camera.ScanManager
import com.fastcredit.fcbank.scanner.sdk.camera.widget.CameraPreviewLayout
import com.fastcredit.fcbank.scanner.sdk.ndk.RecognitionConstants.RECOGNIZER_MODE_DATE
import com.fastcredit.fcbank.scanner.sdk.ndk.RecognitionConstants.RECOGNIZER_MODE_GRAB_CARD_IMAGE
import com.fastcredit.fcbank.scanner.sdk.ndk.RecognitionConstants.RECOGNIZER_MODE_NAME
import com.fastcredit.fcbank.scanner.sdk.ndk.RecognitionConstants.RECOGNIZER_MODE_NUMBER
import com.fastcredit.fcbank.scanner.sdk.ndk.RecognitionResult
import com.fastcredit.fcbank.scanner.sdk.ui.ScanCardRequest.Companion.default
import com.fastcredit.fcbank.scanner.sdk.ui.views.ProgressBarIndeterminate
import com.fastcredit.fcbank.scanner.sdk.utils.Constants
import com.fastcredit.fcbank.scanner.R
import java.io.ByteArrayOutputStream

@RestrictTo(RestrictTo.Scope.LIBRARY)
class ScanCardFragment : Fragment() {
    private var mCameraPreviewLayout: CameraPreviewLayout? = null
    private var mProgressBar: ProgressBarIndeterminate? = null
    private var mMainContent: ViewGroup? = null
    private var mFlashButton: View? = null
    private var mScanManager: ScanManager? = null
    private var mSoundPool: SoundPool? = null
    private var mCapturedSoundId = -1
    private var mListener: InteractionListener? = null
    private var mRequest: ScanCardRequest? = null
    override fun onAttach(context: Context) {
        super.onAttach(context)
        try {
            mListener = activity as InteractionListener?
        } catch (ex: ClassCastException) {
            throw RuntimeException("Parent must implement " + InteractionListener::class.java.simpleName)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mRequest = null
        mRequest = arguments?.getParcelable(ScanCardIntent.KEY_SCAN_CARD_REQUEST)
        if (mRequest == null) mRequest = default
    }

    override fun onCreateAnimation(transit: Int, enter: Boolean, nextAnim: Int): Animation {
        if (Constants.DEBUG) Log.d(
            TAG,
            "onCreateAnimation() called with: transit = [$transit], enter = [$enter], nextAnim = [$nextAnim]"
        )
        // SurfaceView is hard to animate
        val a: Animation = object : Animation() {}
        a.duration = 0
        return a
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View? {
        val root = inflater.inflate(R.layout.wocr_fragment_scan_card, container, false)
        mProgressBar = root.findViewById(R.id.wocr_progress_bar)
        mCameraPreviewLayout = root.findViewById(R.id.wocr_card_recognition_view)
        mMainContent = root.findViewById(R.id.wocr_main_content)
        mFlashButton = root.findViewById(R.id.wocr_iv_flash_id)
        initView(root)
        showMainContent()
        mProgressBar?.visibility = View.VISIBLE
        return root
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        if (!isTablet) {
            requireActivity().requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        } else {
            mCameraPreviewLayout?.setBackgroundColor(Color.BLACK)
        }
        var recognitionMode: Int = RECOGNIZER_MODE_NUMBER
        mRequest?.let {
            if (it.isScanCardHolderEnabled) {
                recognitionMode = recognitionMode or RECOGNIZER_MODE_NAME
            }
            if (it.isScanExpirationDateEnabled) {
                recognitionMode = recognitionMode or RECOGNIZER_MODE_DATE
            }
            if (it.isGrabCardImageEnabled) {
                recognitionMode = recognitionMode or RECOGNIZER_MODE_GRAB_CARD_IMAGE
            }
        }

        mCameraPreviewLayout?.let {
            mScanManager = ScanManager(recognitionMode,
                requireActivity(),
                it,
                object : ScanManager.Callbacks {
                    private var mLastCardImage: ByteArray? = null
                    override fun onCameraOpened(cameraParameters: Camera.Parameters?) {
                        val isFlashSupported =
                            ((cameraParameters?.supportedFlashModes != null && cameraParameters.supportedFlashModes.isNotEmpty()))
                        if (view == null) {
                            return
                        }
                        mProgressBar?.hideSlow()
                        mCameraPreviewLayout?.setBackgroundDrawable(null)
                        if (mFlashButton != null) {
                            mFlashButton?.visibility =
                                if (isFlashSupported) View.VISIBLE else View.GONE
                        }
                        innitSoundPool()
                    }

                    override fun onOpenCameraError(exception: Exception?) {
                        mProgressBar?.hideSlow()
                        hideMainContent()
                        finishWithError(exception)
                    }

                    override fun onRecognitionComplete(result: RecognitionResult?) {
                        if (result?.isFirst == true) {
                            mScanManager?.freezeCameraPreview()
                            playCaptureSound()
                        }
                        if (result?.isFinal == true) {
                            val date: String? = if (result.date.isNullOrEmpty()) {
                                null
                            } else {
                                result.date.substring(0, 2) + '/' + result.date.substring(2)
                            }
                            val card = Card(
                                result.number, result.name, date
                            )
                            val cardImage = mLastCardImage
                            mLastCardImage = null
                            finishWithResult(card, cardImage)
                        }
                    }

                    override fun onCardImageReceived(bitmap: Bitmap?) {
                        mLastCardImage = compressCardImage(bitmap)
                    }

                    override fun onFpsReport(report: String?) {}
                    override fun onAutoFocusMoving(start: Boolean, cameraFocusMode: String?) {}
                    override fun onAutoFocusComplete(success: Boolean, cameraFocusMode: String?) {}
                    private fun compressCardImage(img: Bitmap?): ByteArray? {
                        val result: ByteArray?
                        val stream = ByteArrayOutputStream()
                        result =
                            if (img?.compress(Bitmap.CompressFormat.JPEG, 80, stream) == true) {
                                stream.toByteArray()
                            } else {
                                null
                            }
                        return result
                    }
                })

        }
    }

    override fun onResume() {
        super.onResume()
        mScanManager?.onResume()
    }

    override fun onPause() {
        super.onPause()
        mScanManager?.onPause()
    }

    override fun onDestroy() {
        super.onDestroy()
        if (mSoundPool != null) {
            mSoundPool?.release()
            mSoundPool = null
        }
        mCapturedSoundId = -1
    }

    private fun innitSoundPool() {
        if (mRequest?.isSoundEnabled == true) {
            mSoundPool = SoundPool(1, AudioManager.STREAM_SYSTEM, 0)
            mCapturedSoundId = mSoundPool?.load(activity, R.raw.wocr_capture_card, 0) ?: -1
        }
    }

    private fun initView(view: View) {
        view.findViewById<View>(R.id.wocr_tv_enter_card_number_id).setOnClickListener { v ->
            if (v.isEnabled) {
                v.isEnabled = false
                mListener?.onScanCardCanceled(ScanCardIntent.ADD_MANUALLY_PRESSED)
            }
        }
        mFlashButton?.setOnClickListener { mScanManager?.toggleFlash() }
        val paycardsLink = view.findViewById<View>(R.id.wocr_powered_by_paycards_link) as TextView
        val link = SpannableString(getText(R.string.wocr_powered_by_pay_cards))
        link.setSpan(
            URLSpan(Constants.PAYCARDS_URL),
            0,
            link.length,
            SpannableString.SPAN_INCLUSIVE_EXCLUSIVE
        )
        paycardsLink.text = link
        paycardsLink.movementMethod = LinkMovementMethod.getInstance()
    }

    private fun showMainContent() {
        mMainContent?.visibility = View.VISIBLE
        mCameraPreviewLayout?.visibility = View.VISIBLE
    }

    private fun hideMainContent() {
        mMainContent?.visibility = View.INVISIBLE
        mCameraPreviewLayout?.visibility = View.INVISIBLE
    }

    private fun finishWithError(exception: Exception?) {
        mListener?.onScanCardFailed(exception)
    }

    private fun finishWithResult(card: Card, cardImage: ByteArray?) {
        mListener?.onScanCardFinished(card, cardImage)
    }

    private val isTablet: Boolean
        get() = resources.getBoolean(R.bool.wocr_is_tablet)

    private fun playCaptureSound() {
        if (mCapturedSoundId >= 0) mSoundPool?.play(mCapturedSoundId, 1f, 1f, 0, 0, 1f)
    }

    interface InteractionListener {
        fun onScanCardCanceled(@CancelReason cancelReason: Int)
        fun onScanCardFailed(e: Exception?)
        fun onScanCardFinished(card: Card?, cardImage: ByteArray?)
    }

    companion object {
        const val TAG = "ScanCardFragment"
    }
}