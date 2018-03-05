package com.cziyeli.library

import android.content.Context
import android.content.res.Resources
import android.graphics.PointF
import android.graphics.RectF
import android.os.Build
import android.support.annotation.ColorRes
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.widget.FrameLayout
import android.widget.PopupWindow

object Utils {

    /**
     * Verify if the first child of the rootView is a FrameLayout.
     * Used for cases where the Tooltip is created inside a Dialog or DialogFragment.
     *
     * @param anchorView
     * @return FrameLayout or anchorView.getRootView()
     */
    fun findFrameLayout(anchorView: View?): ViewGroup? {
        anchorView?.let {
            var rootView = it.rootView as ViewGroup
            if (rootView.childCount == 1 && rootView.getChildAt(0) is FrameLayout) {
                rootView = rootView.getChildAt(0) as ViewGroup
            }
            return rootView
        }

        return null
    }

    /**
     * Convert the gravity to the direction of the tooltip arrow.
     */
    fun tooltipGravityToArrowDirection(tooltipGravity: Int): Int {
        return when (tooltipGravity) {
            Gravity.START, Gravity.LEFT -> ArrowDrawable.RIGHT
            Gravity.END, Gravity.RIGHT -> ArrowDrawable.LEFT
            Gravity.TOP -> ArrowDrawable.BOTTOM
            Gravity.BOTTOM -> ArrowDrawable.TOP
            Gravity.CENTER -> ArrowDrawable.TOP
            else -> throw IllegalArgumentException("Gravity must have be CENTER, START, END, TOP or BOTTOM.")
        }
    }

    /**
     * Calculate the location of the popup given the anchor view's current position
     * and the target gravity.
     */
    fun calculatePopupLocation(popupWindow: PopupWindow,
                               anchorView: View,
                               gravity: Int,
                               tooltipOffsetX: Int = 0,
                               tooltipOffsetY: Int = 0
    ): PointF {
        val location = PointF()
        val anchorRect = Utils.calculateRectInWindow(anchorView)
        val anchorCenter = PointF(anchorRect.centerX(), anchorRect.centerY())

        when (gravity) {
            Gravity.START -> { // to left of anchor
                location.x = anchorRect.left - popupWindow.contentView.width.toFloat() - tooltipOffsetX
                location.y = anchorCenter.y - popupWindow.contentView.height / 2f
            }
            Gravity.END -> { // to right of anchor
                location.x = anchorRect.right + tooltipOffsetX
                location.y = anchorCenter.y - popupWindow.contentView.height / 2f
            }
            Gravity.TOP -> { // to top of anchor
                location.x = anchorCenter.x - popupWindow.contentView.width / 2f
                location.y = anchorRect.top - popupWindow.contentView.height.toFloat() - tooltipOffsetY
            }
            Gravity.BOTTOM -> { // to bottom of anchor
                location.x = anchorCenter.x - popupWindow.contentView.width / 2f
                location.y = anchorRect.bottom + tooltipOffsetY
            }
            Gravity.CENTER -> {
                location.x = anchorCenter.x - popupWindow.contentView.width / 2f
                location.y = anchorCenter.y - popupWindow.contentView.height / 2f
            }
            else -> throw IllegalArgumentException("Gravity must have be CENTER, START, END, TOP or BOTTOM.")
        }

        return location
    }

    /**
     * Calculate the location of the view on the screen, or null if not on screen.
     */
    fun calculateRectOnScreen(view: View?): RectF? {
        view?.apply {
            val location = IntArray(2)
            view.getLocationOnScreen(location)
            return RectF(location[0].toFloat(),
                    location[1].toFloat(),
                    (location[0] + view.measuredWidth).toFloat(),
                    (location[1] + view.measuredHeight).toFloat())
        }
        return null
    }

    /**
     * Calculate the location of the view in the window, or null if not in window.
     */
    fun calculateRectInWindow(view: View?): RectF {
        view?.apply {
            val location = IntArray(2)
            view.getLocationInWindow(location)
            return RectF(location[0].toFloat(),
                    location[1].toFloat(),
                    (location[0] + view.measuredWidth).toFloat(),
                    (location[1] + view.measuredHeight).toFloat())
        }
        return RectF()
    }

    fun pxFromDp(dp: Float): Float {
        return dp * Resources.getSystem().displayMetrics.density
    }

    fun setWidth(view: View, width: Int) {
        var params: ViewGroup.LayoutParams? = view.layoutParams
        if (params == null) {
            params = ViewGroup.LayoutParams(width, view.height)
        } else {
            params.width = width
        }
        view.layoutParams = params
    }

    fun setX(view: View, x: Int) {
        val marginParams = getOrCreateMarginLayoutParams(view)
        marginParams.leftMargin = x - view.left
        view.layoutParams = marginParams
    }

    fun setY(view: View, y: Int) {
        val marginParams = getOrCreateMarginLayoutParams(view)
        marginParams.topMargin = y - view.top
        view.layoutParams = marginParams
    }

    private fun getOrCreateMarginLayoutParams(view: View): ViewGroup.MarginLayoutParams {
        val lp = view.layoutParams
        return if (lp != null) {
            lp as? ViewGroup.MarginLayoutParams ?: ViewGroup.MarginLayoutParams(lp)
        } else {
            ViewGroup.MarginLayoutParams(view.width, view.height)
        }
    }

    fun removeOnGlobalLayoutListener(view: View, listener: ViewTreeObserver.OnGlobalLayoutListener) {
        view.viewTreeObserver.removeOnGlobalLayoutListener(listener)
    }

    fun getColor(context: Context, @ColorRes colorRes: Int): Int {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            context.getColor(colorRes)
        } else {
            context.resources.getColor(colorRes)
        }
    }

}
