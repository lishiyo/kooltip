package com.cziyeli.library

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.util.Log
import android.view.*
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.TextView
import com.cziyeli.library.TooltipOverlay.Companion.HIGHLIGHT_SHAPE_RECTANGULAR
import java.lang.ref.WeakReference

interface KooltipListener {
    fun onShow(tooltip: Kooltip)

    fun onDismiss(tooltip: Kooltip)

    // tapped inside -> cta
    fun onTapInside(tooltip: Kooltip)
}

@SuppressLint("ClickableViewAccessibility")
class Kooltip(
        val contextRef: WeakReference<Context>,
        val anchorView: View, // view to anchor to
        var contentText: String? = null, // text to show (if no custom view)
        val gravity: Int = Gravity.TOP, // anchor to top of view
        val shouldShow: () -> Boolean, // predicate to determine whether to show
        val durationTimeMs: Int = DEFAULT_DURATION_TIME, // how long to show it
        val dismissOnTouchOutside: Boolean = false, // whether to dismiss on outside touch
        val listener: KooltipListener? = null, // callbacks
        // custom stuff
        var customView: View? = null, // custom view (optional)
        var customAnimationStyle: Int? = null,
        var shouldHighlight: Boolean = false, // if false, overlay is transparent
        var animated: Boolean = true
): PopupWindow.OnDismissListener {
    private val TAG = Kooltip::class.java.simpleName
    companion object {
        const val DEFAULT_DURATION_TIME = 10000 // show for 10 s
        const val DEFAULT_POPUP_WINDOW_STYLE = android.R.attr.popupWindowStyle
        private val mDefaultTextAppearanceRes = R.style.simpletooltip_default
        private val mDefaultBackgroundColorRes = R.color.simpletooltip_background
        private val mDefaultTextColorRes = R.color.simpletooltip_text
        private val mDefaultArrowColorRes = R.color.simpletooltip_arrow
        private val mDefaultMarginRes = R.dimen.simpletooltip_margin
        private val mDefaultPaddingRes = R.dimen.simpletooltip_padding
        private val mDefaultAnimationPaddingRes = R.dimen.simpletooltip_animation_padding
        private val mDefaultAnimationDurationRes = R.integer.tooltip_animation_duration
        private val mDefaultArrowWidthRes = R.dimen.simpletooltip_arrow_width
        private val mDefaultArrowHeightRes = R.dimen.simpletooltip_arrow_height
        private val mDefaultOverlayOffsetRes = R.dimen.simpletooltip_overlay_offset
    }

    val isValid: Boolean
        get() = !isDismissed && contextRef.get() != null

    val isShowing: Boolean
        get() = popupWindow.isShowing

    private var isDismissed: Boolean = false

    private val popupWindow: PopupWindow by lazy {
        val context = contextRef.get() ?: throw Throwable("null context")
        createPopupWindow(context)
    }
    private val contentLayout: View by lazy {
        val context = contextRef.get() ?: throw Throwable("null context")
        createContentLayout(context)
    }
    private val contentView: View by lazy {
        val context = contextRef.get() ?: throw Throwable("null context")
        val view: View = if (customView == null) {
            val tv = TextView(contextRef.get())
            Utils.setTextAppearance(tv, mDefaultTextAppearanceRes)
            tv.setBackgroundColor(Utils.getColor(context, mDefaultBackgroundColorRes))
            tv.setTextColor(Utils.getColor(context, mDefaultTextColorRes))
            tv
        } else {
            customView!!
        }

        val contentViewParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, 0f)
        contentViewParams.gravity = Gravity.CENTER
        view.layoutParams = contentViewParams
        val padding: Int = context.resources.getDimension(mDefaultPaddingRes).toInt()
        view.setPadding(padding, padding, padding, padding)

        view
    }
    private val rootView: ViewGroup? by lazy {
        Utils.findFrameLayout(anchorView)
    }

    private var arrowDirection: Int = Utils.tooltipGravityToArrowDirection(gravity)
    private val arrowColor = Utils.getColor(contextRef.get()!!, mDefaultArrowColorRes)
    private val arrowDrawable: ArrowDrawable by lazy { ArrowDrawable(arrowColor, arrowDirection) }
    private val arrowView: ImageView by lazy {
        val context = contextRef.get()!!
        val view = ImageView(context)
        view.setImageDrawable(arrowDrawable)
        val arrowWidth = context.resources.getDimension(mDefaultArrowWidthRes)
        val arrowHeight = context.resources.getDimension(mDefaultArrowHeightRes)
        val arrowLayoutParams: LinearLayout.LayoutParams = when (arrowDirection) {
            ArrowDrawable.TOP, ArrowDrawable.BOTTOM -> LinearLayout.LayoutParams(arrowWidth.toInt(), arrowHeight.toInt(), 0f)
            else -> LinearLayout.LayoutParams(arrowHeight.toInt(), arrowWidth.toInt(), 0f)
        }

        arrowLayoutParams.gravity = Gravity.CENTER
        view.layoutParams = arrowLayoutParams

        view
    }

    private var animator: AnimatorSet? = null
    private val animationPadding: Float? = contextRef.get()?.resources?.getDimension(mDefaultAnimationPaddingRes)
    private val animationDuration: Float? = contextRef.get()?.resources?.getDimension(mDefaultAnimationDurationRes)
    private val tooltipWidth: Int? = contextRef.get()?.resources?.getDimension(R.dimen.tooltip_width)?.toInt()
    private val tooltipOffsetY: Int? = contextRef.get()?.resources?.getDimension(R.dimen.tooltip_offset_y)?.toInt()
    private val tooltipOffsetX: Int? = contextRef.get()?.resources?.getDimension(R.dimen.tooltip_offset_x)?.toInt()

    private val locationLayoutListener = object : ViewTreeObserver.OnGlobalLayoutListener {
        override fun onGlobalLayout() {
            if (!isValid) return

            if (tooltipWidth!! > 0 && contentView.width != tooltipWidth) {
                Utils.setWidth(contentView, tooltipWidth)
                popupWindow.update(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                return
            }

            Utils.removeOnGlobalLayoutListener(popupWindow.contentView, this)
            popupWindow.contentView.viewTreeObserver.addOnGlobalLayoutListener(mArrowLayoutListener)
            val location = Utils.calculatePopupLocation(popupWindow, anchorView, gravity, tooltipOffsetX!!, tooltipOffsetY!!)
            popupWindow.isClippingEnabled = true
            popupWindow.update(location.x.toInt(), location.y.toInt(), popupWindow.width, popupWindow.height)
            popupWindow.contentView.requestLayout()
        }
    }

    private val tooltipOverlay: TooltipOverlay? by lazy {
        if (!shouldHighlight || !isValid) {
            null
        } else {
            val view = TooltipOverlay(
                    anchorView.context,
                    anchorView,
                    HIGHLIGHT_SHAPE_RECTANGULAR,
                    anchorView.context.resources.getDimension(mDefaultOverlayOffsetRes))
            view.layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
//            view.setOnTouchListener({ v, event -> false })
            rootView?.addView(view)
            view
        }
    }

    private val animationLayoutListener = object : ViewTreeObserver.OnGlobalLayoutListener {
        override fun onGlobalLayout() {
            Utils.removeOnGlobalLayoutListener(popupWindow.contentView, this)
            if (!isValid) return
            if (animated) startAnimation()
            popupWindow.contentView.requestLayout()
        }
    }

    /**
     * Dismiss
     */
    private val autoDismissLayoutListener = ViewTreeObserver.OnGlobalLayoutListener {
        if (rootView?.isShown == true) dismiss()
    }

    private val showLayoutListener = object : ViewTreeObserver.OnGlobalLayoutListener {
        override fun onGlobalLayout() {
            Utils.removeOnGlobalLayoutListener(popupWindow.contentView, this)
            if (!isValid) return
            listener?.onShow(this@Kooltip)
            contentLayout.visibility = View.VISIBLE
        }
    }

    private val mArrowLayoutListener = object : ViewTreeObserver.OnGlobalLayoutListener {
        override fun onGlobalLayout() {
            if (!isValid) return

            val popup = popupWindow

            Utils.removeOnGlobalLayoutListener(popup.contentView, this)

            popup.contentView.viewTreeObserver.addOnGlobalLayoutListener(animationLayoutListener)
            popup.contentView.viewTreeObserver.addOnGlobalLayoutListener(showLayoutListener)
            val anchorRect = Utils.calculateRectOnScreen(anchorView)
            val contentViewRect = Utils.calculateRectOnScreen(contentLayout)
            var x: Float
            var y: Float
            if (arrowDirection == ArrowDrawable.TOP || arrowDirection == ArrowDrawable.BOTTOM) {
                x = contentLayout.paddingLeft + Utils.pxFromDp(2f)
                val centerX = contentViewRect.width() / 2f - arrowView.width / 2f
                val newX = centerX - (contentViewRect.centerX() - anchorRect.centerX())
                if (newX > x) {
                    if (newX + arrowView.width.toFloat() + x > contentViewRect.width()) {
                        x = contentViewRect.width() - arrowView.width.toFloat() - x
                    } else {
                        x = newX
                    }
                }
                y = arrowView.top.toFloat()
                y += if (arrowDirection == ArrowDrawable.BOTTOM) -1 else +1
            } else {
                y = contentLayout.paddingTop + Utils.pxFromDp(2f)
                val centerY = contentViewRect.height() / 2f - arrowView.height / 2f
                val newY = centerY - (contentViewRect.centerY() - anchorRect.centerY())
                if (newY > y) {
                    if (newY + arrowView.height.toFloat() + y > contentViewRect.height()) {
                        y = contentViewRect.height() - arrowView.height.toFloat() - y
                    } else {
                        y = newY
                    }
                }
                x = arrowView.left.toFloat()
                x += if (arrowDirection == ArrowDrawable.RIGHT) -1 else +1
            }
            Utils.setX(arrowView, x.toInt())
            Utils.setY(arrowView, y.toInt())
            popup.contentView.requestLayout()
        }
    }

    init {
        checkParams()
        if (isValid) {
            popupWindow.contentView = contentLayout
        }
    }

    // PUBLIC API

    fun show() {
        contentLayout.viewTreeObserver.addOnGlobalLayoutListener(locationLayoutListener)
        contentLayout.viewTreeObserver.addOnGlobalLayoutListener(autoDismissLayoutListener)

        rootView?.let {
            it.post {
                if (it.isShown) {
                    popupWindow.showAtLocation(rootView, Gravity.NO_GRAVITY, it.width, it.height)
                } else {
                    Log.e(TAG, "Tooltip cannot be shown, root view is invalid or has been closed.")
                }
            }

        }
    }

    private fun startAnimation() {
        if (!isValid) return

        val property = if (gravity == Gravity.TOP || gravity == Gravity.BOTTOM) "translationY" else "translationX"

        val anim1 = ObjectAnimator.ofFloat(contentLayout, property, -animationPadding!!, animationPadding)
        anim1.duration = animationDuration!!.toLong()
        anim1.interpolator = AccelerateDecelerateInterpolator()

        val anim2 = ObjectAnimator.ofFloat(contentLayout, property, animationPadding, -animationPadding)
        anim2.duration = animationDuration.toLong()
        anim2.interpolator = AccelerateDecelerateInterpolator()

        animator = AnimatorSet()
        animator?.apply {
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    if (!isValid && isShowing) {
                        animation.start()
                    }
                }
            })
            playSequentially(anim1, anim2)
            start()
        }
    }

    private fun checkParams() {
        if (contentText == null && customView == null) {
            throw Throwable("either contextText or a customView must be passed")
        }
        if (contextRef.get() == null) {
            throw Throwable("context reference now null")
        }
    }

    private fun createPopupWindow(context: Context): PopupWindow {
        val popupWindow = PopupWindow(context, null, DEFAULT_POPUP_WINDOW_STYLE)
        popupWindow.setOnDismissListener(this)
        popupWindow.width = ViewGroup.LayoutParams.WRAP_CONTENT
        popupWindow.height = ViewGroup.LayoutParams.WRAP_CONTENT
        popupWindow.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        popupWindow.isOutsideTouchable = true
        popupWindow.isTouchable = true
        popupWindow.setTouchInterceptor(View.OnTouchListener { v, event ->
            val x = event.x.toInt()
            val y = event.y.toInt()

            when {
                (!dismissOnTouchOutside && event.action == MotionEvent.ACTION_DOWN
                        && (x < 0 || x >= contentLayout.measuredWidth || y < 0 || y >= contentLayout.measuredHeight)) -> {
                    return@OnTouchListener true
                }
                (!dismissOnTouchOutside && event.action == MotionEvent.ACTION_OUTSIDE) -> {
                    return@OnTouchListener true
                }
                (event.action == MotionEvent.ACTION_DOWN && dismissOnTouchOutside) -> {
                    dismiss()
                    return@OnTouchListener true
                }
            }

            false
        })
        popupWindow.isClippingEnabled = false
        popupWindow.isFocusable = true
        return popupWindow
    }

    private fun createContentLayout(context: Context): View {
        // create layout
        val linearLayout = LinearLayout(context)
        linearLayout.layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        linearLayout.orientation = if (arrowDirection == ArrowDrawable.LEFT || arrowDirection == ArrowDrawable.RIGHT)
            LinearLayout.HORIZONTAL else LinearLayout.VERTICAL
        animationPadding?.let {
            linearLayout.setPadding(it.toInt(), it.toInt(), it.toInt(), it.toInt())
        }

        when (arrowDirection) {
            ArrowDrawable.BOTTOM, ArrowDrawable.RIGHT -> {
                linearLayout.addView(contentView)
                linearLayout.addView(arrowView)
            } else -> {
            linearLayout.addView(arrowView)
            linearLayout.addView(contentView)
        }
        }

        linearLayout.visibility = View.INVISIBLE
        return linearLayout
    }

    private fun dismiss() {
        if (popupWindow.isShowing) {
            popupWindow.dismiss()
        }
    }

    override fun onDismiss() {
        isDismissed = true

        // notify listeners
        listener?.onDismiss(this)

        // delete all
        animator?.let {
            it.removeAllListeners()
            it.end()
            it.cancel()
        }
        animator = null

    }
}