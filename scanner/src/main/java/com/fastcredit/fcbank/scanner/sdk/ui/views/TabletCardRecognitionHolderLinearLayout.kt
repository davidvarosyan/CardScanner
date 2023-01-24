package com.fastcredit.fcbank.scanner.sdk.ui.views

import android.content.Context
import android.content.res.Configuration
import android.util.AttributeSet
import android.view.View
import android.widget.LinearLayout
import com.fastcredit.fcbank.scanner.R

class TabletCardRecognitionHolderLinearLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {
    private var mSurfaceView: View? = null


    override fun onFinishInflate() {
        super.onFinishInflate()
        mSurfaceView = findViewById(R.id.wocr_card_recognition_view)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        val surfaceWidth = mSurfaceView?.measuredWidth ?: 0
        val surfaceHeight = mSurfaceView?.measuredHeight ?: 0
        val resultWidth: Int
        val resultHeight: Int
        if (Configuration.ORIENTATION_LANDSCAPE == resources.configuration.orientation) {
            resultWidth = (surfaceHeight * 1.3f).toInt()
            resultHeight = surfaceHeight
        } else {
            resultWidth = surfaceWidth
            resultHeight = (surfaceWidth * 1.1f).toInt()
        }
        super.onMeasure(
            MeasureSpec.makeMeasureSpec(resultWidth, MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(resultHeight, MeasureSpec.EXACTLY)
        )
    }
}