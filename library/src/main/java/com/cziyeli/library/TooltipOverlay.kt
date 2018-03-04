package com.cziyeli.library

import android.content.Context
import android.graphics.*
import android.view.View

/**
 * Optional overlay to highlight everything but the view we are highlighting.
 */
class TooltipOverlay internal constructor(context: Context,
                                       private var mAnchorView: View?,
                                       private val highlightShape: Int,
                                       private val mOffset: Float
) : View(context) {
    private var bitmap: Bitmap? = null

    private var invalidated = true

    var anchorView: View?
        get() = mAnchorView
        set(anchorView) {
            this.mAnchorView = anchorView
            invalidate()
        }

    override fun dispatchDraw(canvas: Canvas) {
        if (invalidated || bitmap == null || bitmap!!.isRecycled)
            createWindowFrame()
        // The bitmap is checked again because of Android memory cleanup behavior. (See #42)
        if (bitmap != null && !bitmap!!.isRecycled)
            canvas.drawBitmap(bitmap!!, 0f, 0f, null)
    }

    private fun createWindowFrame() {
        val width = measuredWidth
        val height = measuredHeight
        if (width <= 0 || height <= 0)
            return

        if (bitmap != null && !bitmap!!.isRecycled)
            bitmap!!.recycle()
        bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)

        val osCanvas = Canvas(bitmap!!)

        val outerRectangle = RectF(0f, 0f, width.toFloat(), height.toFloat())

        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.color = Color.BLACK
        paint.isAntiAlias = true
        paint.alpha = resources.getInteger(mDefaultOverlayAlphaRes)
        osCanvas.drawRect(outerRectangle, paint)

        paint.color = Color.TRANSPARENT
        paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_OUT)

        val anchorRecr = Utils.calculateRectInWindow(mAnchorView)
        val overlayRecr = Utils.calculateRectInWindow(this)

        val left = anchorRecr.left - overlayRecr.left
        val top = anchorRecr.top - overlayRecr.top

        val rect = RectF(left - mOffset, top - mOffset, left + mAnchorView!!.measuredWidth.toFloat() + mOffset, top + mAnchorView!!.measuredHeight.toFloat() + mOffset)

        if (highlightShape == HIGHLIGHT_SHAPE_RECTANGULAR) {
            osCanvas.drawRect(rect, paint)
        } else {
            osCanvas.drawOval(rect, paint)
        }

        invalidated = false
    }

    override fun isInEditMode(): Boolean {
        return true
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        super.onLayout(changed, l, t, r, b)
        invalidated = true
    }

    companion object {
        val HIGHLIGHT_SHAPE_OVAL = 0
        val HIGHLIGHT_SHAPE_RECTANGULAR = 1
        private val mDefaultOverlayAlphaRes = R.integer.tooltip_overlay_alpha
    }
}
