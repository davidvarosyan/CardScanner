@file:Suppress("MemberVisibilityCanBePrivate")

package com.fastcredit.fcbank.scanner.sdk.camera.widget

import android.graphics.PointF
import android.graphics.Rect
import android.util.Log
import com.fastcredit.fcbank.scanner.sdk.camera.OrientationHelper.rotateRect
import com.fastcredit.fcbank.scanner.BuildConfig

internal class CardRectCoordsMapper {
    /**
     * Card rect (in camera coordinates)
     */
    private val mCardCameraRectRaw = Rect()

    /**
     * Camera resolution
     */
    private val mCameraPreviewSize =
        intArrayOf(DEFAULT_CAMERA_RESOLUTION[0], DEFAULT_CAMERA_RESOLUTION[1])

    /**
     * Camera rotation
     */
    private var mCameraRotation = DEFAULT_CAMERA_ROTATION

    /**
     * Card rect (in view coordinates)
     */
    val cardRect = Rect()
    private val mCardCameraRect = Rect()
    val cardNumberPos = PointF()
    val cardDatePos = PointF()
    val cardHolderPos = PointF()
    private var mViewWidth = 1280
    private var mViewHeight = 720
    private var mTranslateX = 0
    private var mTranslateY = 0
    private var mScale = 1f
    private var mCameraRectInitialized = false

    init {
        mCardCameraRectRaw.set(DEFAULT_CAMERA_RECT)
    }

    val cardNumberFontSize: Float
        get() = DEFAULT_CARD_NUMBER_FONT_SIZE * mScale
    val cardDateFontSize: Float
        get() = DEFAULT_CARD_DATE_FONT_SIZE * mScale
    val cardHolderFontSize: Float
        get() = DEFAULT_CARD_HOLDER_FONT_SIZE * mScale

    fun setViewSize(width: Int, height: Int): Boolean {
        return if (width != 0 && height != 0 && mViewWidth != width && mViewHeight != height) {
            mViewWidth = width
            mViewHeight = height
            if (!mCameraRectInitialized) refreshCameraDefaults()
            sync()
            true
        } else {
            false
        }
    }

    private fun refreshCameraDefaults() {
        if (mViewHeight > mViewWidth) {
            mCameraRotation = DEFAULT_CAMERA_ROTATION
            mCameraPreviewSize[0] = DEFAULT_CAMERA_RESOLUTION[0]
            mCameraPreviewSize[1] = DEFAULT_CAMERA_RESOLUTION[1]
            mCardCameraRectRaw.set(DEFAULT_CAMERA_RECT)
        } else {
            mCameraRotation = 0
            mCameraPreviewSize[0] = DEFAULT_CAMERA_RESOLUTION[0]
            mCameraPreviewSize[1] = DEFAULT_CAMERA_RESOLUTION[1]
            mCardCameraRectRaw.set(DEFAULT_CAMERA_RECT_LANDSCAPE)
        }
    }

    fun setCameraParameters(
        previewSizeWidth: Int,
        previewSizeHeight: Int,
        rotation: Int,
        cardFrame: Rect
    ): Boolean {
        val resolutionEq = (previewSizeWidth == mCameraPreviewSize[0]
                && previewSizeHeight == mCameraPreviewSize[1])
        val rotationEq = mCameraRotation == rotation
        val cardFrameEq = mCardCameraRectRaw == cardFrame
        if (resolutionEq && rotationEq && cardFrameEq && mCameraRectInitialized) {
            return false
        }
        mCameraPreviewSize[0] = previewSizeWidth
        mCameraPreviewSize[1] = previewSizeHeight
        mCameraRotation = rotation
        mCardCameraRectRaw.set(cardFrame)
        mCameraRectInitialized = true
        sync()
        return true
    }

    private fun sync() {
        refreshTransform()
        refreshCardRect()
        refreshCardTextPositions()
    }

    private fun refreshTransform() {
        val scale: Float
        var translateY = 0
        var translateX = 0
        val cameraHeight = cameraHeightRotated.toFloat()
        val cameraWidth = cameraWidthRotated.toFloat()

        // Center crop
        if (cameraWidth * mViewHeight > cameraHeight * mViewWidth) {
            scale = mViewHeight / cameraHeight
            translateX = ((mViewWidth - cameraWidth * scale) / 2f).toInt()
        } else {
            scale = mViewWidth / cameraWidth
            translateY = ((mViewHeight - cameraHeight * scale) / 2f).toInt()
        }
        mScale = scale
        mTranslateX = translateX
        mTranslateY = translateY
        rotateRect(
            mCardCameraRectRaw,
            mCameraPreviewSize[0],
            mCameraPreviewSize[1],
            mCameraRotation,
            mCardCameraRect
        )
        if (DBG) Log.d(
            TAG, "refreshTransform() widthXheight: " + mViewWidth + "x" + mViewHeight
                    + "; translateXY: [" + translateX + "," + mTranslateY + "], scale: " + mScale
        )
    }

    private fun refreshCardRect() {
        cardRect.left = (0.5f + mScale * mCardCameraRect.left).toInt() + mTranslateX
        cardRect.top = (0.5f + mScale * mCardCameraRect.top).toInt() + mTranslateY
        cardRect.right = (0.5f + mScale * mCardCameraRect.right).toInt() + mTranslateX
        cardRect.bottom = (0.5f + mScale * mCardCameraRect.bottom).toInt() + mTranslateY
    }

    private fun refreshCardTextPositions() {
        mapToViewCoordinates(DEFAULT_CARD_NUMBER_POS, cardNumberPos)
        mapToViewCoordinates(DEFAULT_CARD_DATE_POS, cardDatePos)
        mapToViewCoordinates(DEFAULT_CARD_HOLDER_POS, cardHolderPos)
    }

    fun mapToViewCoordinates(src: PointF, dst: PointF) {
        dst.x = mScale * src.x + cardRect.left
        dst.y = mScale * src.y + cardRect.top
    }

    private val cameraWidthRotated: Int
        get() = if (mCameraRotation == 0 || mCameraRotation == 180) mCameraPreviewSize[0] else mCameraPreviewSize[1]
    private val cameraHeightRotated: Int
        get() = if (mCameraRotation == 0 || mCameraRotation == 180) mCameraPreviewSize[1] else mCameraPreviewSize[0]

    companion object {
        private val DEFAULT_CAMERA_RESOLUTION = intArrayOf(1280, 720)
        private const val DEFAULT_CAMERA_ROTATION = 90
        private val DEFAULT_CAMERA_RECT = Rect(432, 30, 432 + 416, 30 + 660)
        private val DEFAULT_CAMERA_RECT_LANDSCAPE = Rect(310, 152, 970, 568)
        private val DEFAULT_CARD_NUMBER_POS = PointF(60f, 268f)
        private val DEFAULT_CARD_DATE_POS = PointF(289f, 321f)
        private val DEFAULT_CARD_HOLDER_POS = PointF(33f, 364f)
        private const val DEFAULT_CARD_NUMBER_FONT_SIZE = 40f
        private const val DEFAULT_CARD_DATE_FONT_SIZE = 27f
        private const val DEFAULT_CARD_HOLDER_FONT_SIZE = 27f
        private val DBG = BuildConfig.DEBUG
        private const val TAG = "CardRectCoordsMapper"
    }
}