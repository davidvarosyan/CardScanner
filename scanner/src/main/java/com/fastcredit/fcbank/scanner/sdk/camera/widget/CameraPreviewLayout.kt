@file:Suppress("unused")

package com.fastcredit.fcbank.scanner.sdk.camera.widget

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Rect
import android.util.AttributeSet
import android.util.Log
import android.view.Gravity
import android.view.SurfaceView
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.core.view.GravityCompat
import androidx.core.view.ViewCompat
import com.fastcredit.fcbank.scanner.sdk.utils.Constants
import com.fastcredit.fcbank.scanner.R
import kotlin.math.max
import kotlin.math.min

class CameraPreviewLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(
    context, attrs, defStyleAttr
) {
    var surfaceView: SurfaceView? = null
        private set
    var detectionStateOverlay: CardDetectionStateView? = null
        private set
    private var mWindowFocusChangedListener: OnWindowFocusChangedListener? = null
    private val mCardFrame: CardRectCoordsMapper = CardRectCoordsMapper()

    /**
     * These are used for computing child frames based on their gravity.
     */
    private val mTmpCard = Rect()
    private val mTmp = Rect()

    fun setOnWindowFocusChangedListener(listener: OnWindowFocusChangedListener?) {
        mWindowFocusChangedListener = listener
    }

    fun setCameraParameters(
        previewSizeWidth: Int,
        previewSizeHeight: Int,
        rotation: Int,
        cardFrame: Rect
    ) {
        if (DBG) Log.d(
            TAG,
            "setCameraParameters() called with: previewSizeWidth = [$previewSizeWidth], previewSizeHeight = [$previewSizeHeight], rotation = [$rotation], cardFrame = [$cardFrame]"
        )
        detectionStateOverlay?.setCameraParameters(
            previewSizeWidth,
            previewSizeHeight,
            rotation,
            cardFrame
        )
        val changed =
            mCardFrame.setCameraParameters(previewSizeWidth, previewSizeHeight, rotation, cardFrame)
        if (changed && !ViewCompat.isInLayout(this)) requestLayout()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (mCardFrame.setViewSize(w, h)) {
            if (!ViewCompat.isInLayout(this)) requestLayout()
        }
    }

    override fun onFinishInflate() {
        super.onFinishInflate()
        surfaceView = getChildAt(0) as SurfaceView
        detectionStateOverlay = getChildAt(1) as CardDetectionStateView
    }

    /**
     * Any layout manager that doesn't scroll will want this.
     */
    override fun shouldDelayChildPressedState(): Boolean {
        return false
    }

    override fun onWindowFocusChanged(hasWindowFocus: Boolean) {
        super.onWindowFocusChanged(hasWindowFocus)
        if (DBG) Log.d(
            TAG,
            "onWindowFocusChanged() called with: hasWindowFocus = [$hasWindowFocus]"
        )
        mWindowFocusChangedListener?.onWindowFocusChanged(
            this,
            hasWindowFocus
        )
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        val count = childCount
        val cardRect = mCardFrame.cardRect
        for (i in 0 until count) {
            val child = getChildAt(i)
            val lp = child.layoutParams as LayoutParams
            if (child.visibility == GONE
                || lp.cardGravity == LayoutParams.UNSPECIFIED_CARD_GRAVITY
            ) continue
            val layoutDirection = ViewCompat.getLayoutDirection(this)
            val childWidth = child.measuredWidth
            val childHeight = child.measuredHeight
            getChildRect(layoutDirection, cardRect, mTmp, lp, childWidth, childHeight)
            constrainChildRect(lp, mTmp, childWidth, childHeight)
            child.layout(mTmp.left, mTmp.top, mTmp.right, mTmp.bottom)
        }
    }

    @SuppressLint("RtlHardcoded")
    private fun getChildRect(
        layoutDirection: Int,
        cardRect: Rect, out: Rect, lp: LayoutParams, childWidth: Int, childHeight: Int
    ) {
        val absCardGravity = GravityCompat.getAbsoluteGravity(
            resolveGravity(lp.cardGravity),
            layoutDirection
        )
        val cardHgrav = absCardGravity and Gravity.HORIZONTAL_GRAVITY_MASK
        val cardVgrav = absCardGravity and Gravity.VERTICAL_GRAVITY_MASK
        val left: Int = when (cardHgrav) {
            Gravity.LEFT -> cardRect.left + lp.leftMargin
            Gravity.RIGHT -> cardRect.right - childWidth - lp.rightMargin
            Gravity.CENTER_HORIZONTAL -> cardRect.left + cardRect.width() / 2 - childWidth / 2 + lp.leftMargin - lp.rightMargin
            else -> cardRect.left + lp.leftMargin
        }
        val top: Int = when (cardVgrav) {
            Gravity.TOP -> cardRect.top - childHeight - lp.bottomMargin
            Gravity.BOTTOM -> cardRect.bottom + lp.topMargin
            Gravity.CENTER_VERTICAL -> cardRect.top + cardRect.height() / 2 - childHeight / 2 + lp.topMargin - lp.bottomMargin
            else -> cardRect.top - childHeight - lp.bottomMargin
        }
        out[left, top, left + childWidth] = top + childHeight
    }

    private fun constrainChildRect(lp: LayoutParams, out: Rect, childWidth: Int, childHeight: Int) {
        val width = width
        val height = height

        // Obey margins and padding
        val left = max(
            paddingLeft + lp.leftMargin,
            min(
                out.left,
                width - paddingRight - childWidth - lp.rightMargin
            )
        )
        val top = max(
            paddingTop + lp.topMargin,
            min(
                out.top,
                height - paddingBottom - childHeight - lp.bottomMargin
            )
        )
        out[left, top, left + childWidth] = top + childHeight
    }

    override fun generateLayoutParams(attrs: AttributeSet): LayoutParams {
        return LayoutParams(
            context, attrs
        )
    }

    override fun generateDefaultLayoutParams(): LayoutParams {
        return LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
    }

    override fun generateLayoutParams(p: ViewGroup.LayoutParams): ViewGroup.LayoutParams {
        return LayoutParams(p)
    }

    override fun checkLayoutParams(p: ViewGroup.LayoutParams): Boolean {
        return p is LayoutParams
    }

    class LayoutParams : FrameLayout.LayoutParams {
        /**
         * The gravity to apply with the View to which these layout parameters
         * are associated.
         */
        var cardGravity = UNSPECIFIED_CARD_GRAVITY

        constructor(c: Context, attrs: AttributeSet?) : super(c, attrs) {

            // Pull the layout param values from the layout XML during
            // inflation.  This is not needed if you don't care about
            // changing the layout behavior in XML.
            @SuppressLint("CustomViewStyleable") val a =
                c.obtainStyledAttributes(attrs, R.styleable.wocr_CameraPreviewLayout_Layout)
            if (a.hasValue(R.styleable.wocr_CameraPreviewLayout_Layout_wocr_layout_cardAlignGravity)) {
                cardGravity = a.getInt(
                    R.styleable.wocr_CameraPreviewLayout_Layout_wocr_layout_cardAlignGravity,
                    Gravity.CENTER
                )
            }
            a.recycle()
        }

        constructor(width: Int, height: Int) : super(width, height)
        constructor(source: ViewGroup.LayoutParams?) : super(source!!)

        companion object {
            const val UNSPECIFIED_CARD_GRAVITY = -1
        }
    }

    companion object {
        private const val TAG = "CameraPreviewLayout"
        private val DBG = Constants.DEBUG
        private fun resolveGravity(pGravity: Int): Int {
            var gravity = pGravity
            if (gravity and Gravity.HORIZONTAL_GRAVITY_MASK == Gravity.NO_GRAVITY) {
                gravity = gravity or GravityCompat.START
            }
            if (gravity and Gravity.VERTICAL_GRAVITY_MASK == Gravity.NO_GRAVITY) {
                gravity = gravity or Gravity.TOP
            }
            return gravity
        }
    }
}