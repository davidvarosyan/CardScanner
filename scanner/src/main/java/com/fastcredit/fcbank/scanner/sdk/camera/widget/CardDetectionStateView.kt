@file:Suppress("DEPRECATION")

package com.fastcredit.fcbank.scanner.sdk.camera.widget

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.*
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.text.TextUtils
import android.util.AttributeSet
import android.util.Log
import android.view.View
import com.fastcredit.fcbank.scanner.sdk.ndk.RecognitionConstants
import com.fastcredit.fcbank.scanner.sdk.ndk.RecognitionResult
import com.fastcredit.fcbank.scanner.sdk.utils.CardUtils.prettyPrintCardNumber
import com.fastcredit.fcbank.scanner.sdk.utils.Constants
import com.fastcredit.fcbank.scanner.sdk.utils.Fonts
import com.fastcredit.fcbank.scanner.R
import kotlin.math.roundToInt

/**
 * This view is overlaid on top of the camera preview. It adds the card rectangle and partial
 * transparency outside it
 */
@SuppressLint("UseCompatLoadingForDrawables")
class CardDetectionStateView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {
    @Volatile
    private var mDetectionState = 0

    @Volatile
    private var mRecognitionResultDate: String? = null

    @Volatile
    private var mRecognitionResultCardNumber: String? = null

    @Volatile
    private var mRecognitionResultHolder: String? = null
    private var mCardFrame: CardRectCoordsMapper
    private var mDisplayDensity = 0f
    private var mCardTypeface: Typeface? = null
    private val mCardRectInvalidation = Rect()
    private var mCornerPaddingLeft = 0f
    private var mCornerPaddingTop = 0f
    private var mCornerLineWidth = 0f
    private var mCornerRadius = 0f
    private var mCardGradientDrawable: Drawable
    private lateinit var mCornerTopLeftDrawable: BitmapDrawable
    private lateinit var mCornerTopRightDrawable: BitmapDrawable
    private lateinit var mCornerBottomLeftDrawable: BitmapDrawable
    private lateinit var mCornerBottomRightDrawable: BitmapDrawable
    private lateinit var mLineTopDrawable: BitmapDrawable
    private lateinit var mLineLeftDrawable: BitmapDrawable
    private lateinit var mLineRightDrawable: BitmapDrawable
    private lateinit var mLineBottomDrawable: BitmapDrawable
    private var mBackgroundPaint: Paint
    private var mCardNumberPaint: Paint
    private var mCardDatePaint: Paint
    private var mCardHolderPaint: Paint

    init {
        val density = resources.displayMetrics.density
        mDisplayDensity = density
        mCardFrame = CardRectCoordsMapper()
        val mBackgroundDrawableColor = context.resources.getColor(R.color.wocr_card_shadow_color)
        mCornerPaddingTop = density * RECT_CORNER_PADDING_TOP
        mCornerPaddingLeft = density * RECT_CORNER_PADDING_LEFT
        mCornerLineWidth = density * RECT_CORNER_LINE_STROKE_WIDTH
        mCornerRadius = density * RECT_CORNER_RADIUS
        mCardGradientDrawable = context.resources.getDrawable(R.drawable.wocr_frame_rect_gradient)
        initCornerDrawables(context)
        initLineDrawables(context)
        mBackgroundPaint = Paint()
        mBackgroundPaint.color = mBackgroundDrawableColor
        mCardTypeface = Fonts.getCardFont(context)
        mCardNumberPaint = createCardTextPaint()
        mCardDatePaint = createCardTextPaint()
        mCardHolderPaint = createCardTextPaint()
        if (isInEditMode) {
            mDetectionState = TOP_EDGE or BOTTOM_EDGE or LEFT_EDGE or RIGHT_EDGE
            mRecognitionResultCardNumber = prettyPrintCardNumber("1234567890123456")
            mRecognitionResultDate = "05/18"
            mRecognitionResultHolder = "CARDHOLDER NAME"
        }
    }

    private fun createCardTextPaint(): Paint {
        val paint =
            Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG or Paint.LINEAR_TEXT_FLAG)
        paint.typeface = mCardTypeface
        paint.color = Color.WHITE
        paint.setShadowLayer(6f, 3.0f, 3.0f, Color.BLACK)
        paint.textSize = 12 * mDisplayDensity
        return paint
    }

    private fun initCornerDrawables(context: Context) {
        mCornerTopLeftDrawable =
            context.resources.getDrawable(R.drawable.wocr_card_frame_rect_corner_top_left) as BitmapDrawable
        val m = Matrix()
        val bitmap = mCornerTopLeftDrawable.bitmap
        m.setRotate(90f)
        mCornerTopRightDrawable = BitmapDrawable(
            context.resources,
            Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, m, true)
        )
        m.setRotate(180f)
        mCornerBottomRightDrawable = BitmapDrawable(
            context.resources,
            Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, m, true)
        )
        m.setRotate(270f)
        mCornerBottomLeftDrawable = BitmapDrawable(
            context.resources,
            Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, m, true)
        )
    }

    private fun initLineDrawables(context: Context) {
        mLineTopDrawable =
            context.resources.getDrawable(R.drawable.wocr_card_frame_rect_line_top) as BitmapDrawable
        val m = Matrix()
        val bitmap = mLineTopDrawable.bitmap
        m.setRotate(90f)
        mLineRightDrawable = BitmapDrawable(
            context.resources,
            Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, m, true)
        )
        m.setRotate(180f)
        mLineBottomDrawable = BitmapDrawable(
            context.resources,
            Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, m, true)
        )
        m.setRotate(270f)
        mLineLeftDrawable = BitmapDrawable(
            context.resources,
            Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, m, true)
        )
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (DBG) Log.d("CameraActivity", "onSizeChanged w,h: $w,$h")
        val changed = mCardFrame.setViewSize(w, h)
        if (changed) refreshCardRectCoords()
    }

    override fun onDraw(canvas: Canvas) {
        if (mCardGradientDrawable.bounds.width() == 0) return
        drawBackground(canvas)
        drawCorners(canvas)
        drawRecognitionResult(canvas)
    }

    private fun drawBackground(canvas: Canvas) {
        val rect = mCardFrame.cardRect
        // top
        canvas.drawRect(0f, 0f, width.toFloat(), rect.top.toFloat(), mBackgroundPaint)
        // bottom
        canvas.drawRect(
            0f,
            rect.bottom.toFloat(),
            width.toFloat(),
            height.toFloat(),
            mBackgroundPaint
        )
        // left
        canvas.drawRect(
            0f,
            rect.top.toFloat(),
            rect.left.toFloat(),
            rect.bottom.toFloat(),
            mBackgroundPaint
        )
        // right
        canvas.drawRect(
            rect.right.toFloat(),
            rect.top.toFloat(),
            width.toFloat(),
            rect.bottom.toFloat(),
            mBackgroundPaint
        )
    }

    private fun drawCorners(canvas: Canvas) {
        val detectionState = mDetectionState
        mCardGradientDrawable.draw(canvas)
        mCornerTopLeftDrawable.draw(canvas)
        mCornerTopRightDrawable.draw(canvas)
        mCornerBottomLeftDrawable.draw(canvas)
        mCornerBottomRightDrawable.draw(canvas)

        // Detected edges
        if (0 != detectionState and TOP_EDGE) {
            mLineTopDrawable.draw(canvas)
        }
        if (0 != detectionState and LEFT_EDGE) {
            mLineLeftDrawable.draw(canvas)
        }
        if (0 != detectionState and RIGHT_EDGE) {
            mLineRightDrawable.draw(canvas)
        }
        if (0 != detectionState and BOTTOM_EDGE) {
            mLineBottomDrawable.draw(canvas)
        }
    }

    private fun drawRecognitionResult(canvas: Canvas) {
        val resultDate = mRecognitionResultDate
        val resultNumber = mRecognitionResultCardNumber
        val resultHolder = mRecognitionResultHolder
        if (!resultNumber.isNullOrEmpty()) {
            canvas.drawText(
                resultNumber,
                mCardFrame.cardNumberPos.x,
                mCardFrame.cardNumberPos.y,
                mCardNumberPaint
            )
        }

        if (!resultDate.isNullOrEmpty()) {
            canvas.drawText(
                resultDate,
                mCardFrame.cardDatePos.x,
                mCardFrame.cardDatePos.y,
                mCardDatePaint
            )
        }
        if (!resultHolder.isNullOrEmpty()) {
            canvas.drawText(
                resultHolder,
                mCardFrame.cardHolderPos.x,
                mCardFrame.cardHolderPos.y,
                mCardHolderPaint
            )
        }
    }

    private fun refreshCardRectCoords() {
        refreshCardRectInvalidation()
        refreshDrawableBounds()
        refreshTextSize()
    }

    private fun refreshCardRectInvalidation() {
        val cardRect = mCardFrame.cardRect
        val border = (0.5f + mCornerPaddingLeft).toInt() + (0.5f + mCornerLineWidth / 2f).toInt()
        mCardRectInvalidation.left = cardRect.left - border
        mCardRectInvalidation.top = cardRect.top - border
        mCardRectInvalidation.right = cardRect.right + border
        mCardRectInvalidation.bottom = cardRect.bottom + border
    }

    private fun refreshDrawableBounds() {
        val cardRect = mCardFrame.cardRect
        mCardGradientDrawable.bounds = cardRect
        val rectWidth = mCornerTopLeftDrawable.intrinsicWidth
        val rectHeight = mCornerTopLeftDrawable.intrinsicHeight
        val cornerStroke = (0.5f + mCornerLineWidth / 2f).toInt()
        val left1 = (cardRect.left - mCornerPaddingLeft - cornerStroke).roundToInt()
        val left2 = (cardRect.right - rectWidth + mCornerPaddingLeft + cornerStroke).roundToInt()
        val top1 = (cardRect.top - mCornerPaddingTop - cornerStroke).roundToInt()
        val top2 = (cardRect.bottom - rectHeight + mCornerPaddingTop + cornerStroke).roundToInt()

        // Corners
        mCornerTopLeftDrawable.setBounds(left1, top1, left1 + rectWidth, top1 + rectHeight)
        mCornerTopRightDrawable.setBounds(left2, top1, left2 + rectWidth, top1 + rectWidth)
        mCornerBottomLeftDrawable.setBounds(left1, top2, left1 + rectWidth, top2 + rectHeight)
        mCornerBottomRightDrawable.setBounds(left2, top2, left2 + rectWidth, top2 + rectHeight)

        // Lines
        val offset = mCornerRadius.toInt()
        mLineTopDrawable.setBounds(
            left1 + offset,
            top1,
            left2 + rectWidth - offset,
            top1 + mLineTopDrawable.intrinsicHeight
        )
        mLineLeftDrawable.setBounds(
            left1, top1 + offset,
            left1 + mLineLeftDrawable.intrinsicWidth, top2 + rectHeight - offset
        )
        mLineRightDrawable.setBounds(
            left2 + rectWidth - mLineRightDrawable.intrinsicWidth,
            top1 + offset,
            left2 + rectWidth,
            top2 + rectHeight - offset
        )
        mLineBottomDrawable.setBounds(
            left1 + offset,
            top2 + rectHeight - mLineBottomDrawable.intrinsicHeight,
            left2 + rectWidth - offset,
            top2 + rectHeight
        )
    }

    private fun refreshTextSize() {
        mCardNumberPaint.textSize = mCardFrame.cardNumberFontSize
        mCardDatePaint.textSize = mCardFrame.cardDateFontSize
        mCardHolderPaint.textSize = mCardFrame.cardHolderFontSize
    }

    @Synchronized
    fun setDetectionState(detectionState: Int) {
        if (mDetectionState != detectionState) {
            mDetectionState = detectionState
            postInvalidate(
                mCardRectInvalidation.left,
                mCardRectInvalidation.top,
                mCardRectInvalidation.right,
                mCardRectInvalidation.bottom
            )
        }
    }

    @Synchronized
    fun setRecognitionResult(result: RecognitionResult) {
        if (DBG) Log.d(TAG, "setRecognitionResult() called with: result = [$result]")
        mRecognitionResultCardNumber = if (!TextUtils.isEmpty(result.number)) {
            prettyPrintCardNumber(result.number)
        } else {
            null
        }
        mRecognitionResultDate = if (!result.date.isNullOrEmpty()) {
            result.date.substring(0, 2) + '/' + result.date.substring(2)
        } else {
            null
        }
        mRecognitionResultHolder = result.name
        postInvalidate(
            mCardRectInvalidation.left,
            mCardRectInvalidation.top,
            mCardRectInvalidation.right,
            mCardRectInvalidation.bottom
        )
    }

    fun setCameraParameters(
        previewSizeWidth: Int,
        previewSizeHeight: Int,
        rotation: Int,
        cardFrame: Rect
    ) {
        val changed = mCardFrame.setCameraParameters(
            previewSizeWidth,
            previewSizeHeight,
            rotation,
            cardFrame
        )
        if (changed) {
            refreshCardRectCoords()
            invalidate()
        }
    }

    companion object {
        private val DBG = Constants.DEBUG
        private const val TAG = "CardDetectionStateView"
        private const val RECT_CORNER_PADDING_LEFT = 1f
        private const val RECT_CORNER_PADDING_TOP = 1f
        private const val RECT_CORNER_LINE_STROKE_WIDTH = 5f
        private const val RECT_CORNER_RADIUS = 8f
        private const val TOP_EDGE = RecognitionConstants.DETECTED_BORDER_TOP
        private const val BOTTOM_EDGE = RecognitionConstants.DETECTED_BORDER_BOTTOM
        private const val LEFT_EDGE = RecognitionConstants.DETECTED_BORDER_LEFT
        private const val RIGHT_EDGE = RecognitionConstants.DETECTED_BORDER_RIGHT
    }
}