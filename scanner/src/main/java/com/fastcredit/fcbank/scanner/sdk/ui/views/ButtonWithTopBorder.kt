package com.fastcredit.fcbank.scanner.sdk.ui.views

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatButton

class ButtonWithTopBorder @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : AppCompatButton(context, attrs, defStyleAttr) {
    private var mTopLinePaint: Paint = Paint()

    init {
        mTopLinePaint.strokeWidth = resources.displayMetrics.density
        mTopLinePaint.style = Paint.Style.STROKE
        mTopLinePaint.color = 0x61ffffff
    }


    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawLine(0f, 0f, width.toFloat(), 0f, mTopLinePaint)
    }
}