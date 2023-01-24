@file:Suppress("DEPRECATION")

package com.fastcredit.fcbank.scanner.sdk.ui

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.AsyncTask
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.RestrictTo
import androidx.fragment.app.Fragment
import com.fastcredit.fcbank.scanner.R
import com.fastcredit.fcbank.scanner.sdk.ScanCardIntent
import com.fastcredit.fcbank.scanner.sdk.ScanCardIntent.CancelReason
import com.fastcredit.fcbank.scanner.sdk.camera.RecognitionAvailabilityChecker.doCheck
import com.fastcredit.fcbank.scanner.sdk.camera.RecognitionCoreUtils.deployRecognitionCoreSync
import com.fastcredit.fcbank.scanner.sdk.camera.RecognitionUnavailableException
import com.fastcredit.fcbank.scanner.sdk.camera.widget.CameraPreviewLayout
import com.fastcredit.fcbank.scanner.sdk.ndk.RecognitionCore.Companion.getInstance
import java.lang.ref.WeakReference

@RestrictTo(RestrictTo.Scope.LIBRARY)
class InitLibraryFragment : Fragment() {
    private var mListener: InteractionListener? = null
    private var mProgressBar: View? = null
    private var mCameraPreviewLayout: CameraPreviewLayout? = null
    private var mMainContent: ViewGroup? = null
    private var mFlashButton: View? = null
    private var mDeployCoreTask: DeployCoreTask? = null
    override fun onAttach(context: Context) {
        super.onAttach(context)
        mListener = try {
            activity as InteractionListener?
        } catch (ex: ClassCastException) {
            throw RuntimeException("Parent must implement " + ScanCardFragment.InteractionListener::class.java.simpleName)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val root = inflater.inflate(R.layout.wocr_fragment_scan_card, container, false)
        mMainContent = root.findViewById(R.id.wocr_main_content)
        mProgressBar = root.findViewById(R.id.wocr_progress_bar)
        mCameraPreviewLayout = root.findViewById(R.id.wocr_card_recognition_view)
        mFlashButton = root.findViewById(R.id.wocr_iv_flash_id)
        val enterManuallyButton = root.findViewById<View>(R.id.wocr_tv_enter_card_number_id)
        enterManuallyButton.visibility = View.VISIBLE
        enterManuallyButton.setOnClickListener {
            mListener?.onScanCardCanceled(
                ScanCardIntent.ADD_MANUALLY_PRESSED
            )
        }
        return root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        mProgressBar?.visibility = View.GONE
        mMainContent?.visibility = View.VISIBLE
        mCameraPreviewLayout?.visibility = View.VISIBLE
        mCameraPreviewLayout?.surfaceView?.visibility = View.GONE
        mCameraPreviewLayout?.setBackgroundColor(Color.BLACK)
        mFlashButton?.visibility = View.GONE
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        val checkResult = doCheck(
            requireContext()
        )
        if (checkResult.isFailedOnCameraPermission) {
            if (savedInstanceState == null) {
                requestPermissions(
                    arrayOf(Manifest.permission.CAMERA),
                    REQUEST_CAMERA_PERMISSION_CODE
                )
            }
        } else {
            subscribeToInitCore()
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            REQUEST_CAMERA_PERMISSION_CODE -> {
                if (grantResults.isNotEmpty()
                    && grantResults[0] == PackageManager.PERMISSION_GRANTED
                ) {
                    subscribeToInitCore()
                } else {
                    mListener?.onInitLibraryFailed(
                        RecognitionUnavailableException(RecognitionUnavailableException.ERROR_NO_CAMERA_PERMISSION)
                    )
                }
                return
            }
            else -> {}
        }
    }

    private fun subscribeToInitCore() {
        mProgressBar?.visibility = View.VISIBLE
        mDeployCoreTask?.cancel(false)
        mDeployCoreTask = DeployCoreTask(this)
        mDeployCoreTask?.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR)
    }

    override fun onStop() {
        super.onStop()
        if (mDeployCoreTask != null) {
            mDeployCoreTask?.cancel(false)
            mDeployCoreTask = null
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        mProgressBar = null
    }

    override fun onDetach() {
        super.onDetach()
        mListener = null
    }

    interface InteractionListener {
        fun onScanCardCanceled(@CancelReason actionId: Int)
        fun onInitLibraryFailed(e: Throwable?)
        fun onInitLibraryComplete()
    }

    private class DeployCoreTask(parent: InitLibraryFragment) :
        AsyncTask<Void?, Void?, Throwable?>() {
        private val fragmentRef: WeakReference<InitLibraryFragment>

        @SuppressLint("StaticFieldLeak")
        private val appContext: Context

        init {
            fragmentRef = WeakReference(parent)
            appContext = parent.requireContext().applicationContext
        }

        @Deprecated("Deprecated in Java")
        override fun doInBackground(vararg voids: Void?): Throwable? {
            return try {
                val checkResult = doCheck(appContext)
                if (checkResult.isFailed) {
                    throw RecognitionUnavailableException()
                }
                deployRecognitionCoreSync(appContext)
                if (!getInstance(appContext).isDeviceSupported) {
                    throw RecognitionUnavailableException()
                }
                null
            } catch (e: RecognitionUnavailableException) {
                e
            }
        }

        @Deprecated("Deprecated in Java")
        override fun onPostExecute(lastError: Throwable?) {
            super.onPostExecute(lastError)
            val fragment = fragmentRef.get()
            if (fragment?.mProgressBar == null || fragment.mListener == null) return
            fragment.mProgressBar?.visibility = View.GONE
            if (lastError == null) {
                fragment.mListener?.onInitLibraryComplete()
            } else {
                fragment.mListener?.onInitLibraryFailed(lastError)
            }
        }
    }

    companion object {
        const val TAG = "InitLibraryFragment"
        private const val REQUEST_CAMERA_PERMISSION_CODE = 1
    }
}