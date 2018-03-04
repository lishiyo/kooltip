package com.cziyeli.library

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.ValueAnimator.INFINITE
import android.animation.ValueAnimator.REVERSE
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.util.Log
import android.view.*
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.DecelerateInterpolator
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
        // required
        val contextRef: WeakReference<Context>,
        val anchorView: View, // view to anchor to
        var contentText: String? = null, // text to show (if no custom view)
        val shouldShow: () -> Boolean, // predicate to determine whether to show
        val listener: KooltipListener? = null, // callbacks
        // default config (customizable)
        val gravity: Int = Gravity.TOP, // anchor to top of view
        val durationTimeMs: Long = DEFAULT_DURATION_TIME, // how long to show it
        val dismissOnTouchOutside: Boolean = false, // whether to dismiss on outside touch
        var shouldHighlight: Boolean = false, // if false, overlay is transparent
        var shouldAnimate: Boolean = true,
        // optional custom stuff
        var customView: View? = null, // custom view (optional, overrides text)
        var customAnimationStyle: Int? = null
): PopupWindow.OnDismissListener {
    private val TAG = Kooltip::class.java.simpleName
    companion object {
        const val DEFAULT_DURATION_TIME: Long = 100000 // show for 10 s
        const val DEFAULT_POPUP_WINDOW_STYLE = android.R.attr.popupWindowStyle
        private val mDefaultTextAppearanceRes = R.style.default_text_appearance
        private val mDefaultBackgroundColorRes = R.color.default_tooltip_background
        private val mDefaultTextColorRes = R.color.default_text_color
        private val mDefaultArrowColorRes = R.color.default_arrow_color
        private val mDefaultPaddingRes = R.dimen.default_tooltip_padding
        private val mDefaultAnimationPaddingRes = R.dimen.default_animation_padding
        private val mDefaultAnimationDurationRes = R.integer.tooltip_animation_duration
        private val mDefaultArrowWidthRes = R.dimen.default_arrow_width
        private val mDefaultArrowHeightRes = R.dimen.default_arrow_height
        private val mDefaultOverlayOffsetRes = R.dimen.default_overlay_offset

        fun create(contextRef: WeakReference<Context>,
                   anchorView: View, // view to anchor to
                   contentText: String? = null, // text to show (if no custom view)
                   shouldShow: () -> Boolean, // predicate to determine whether to show
                   listener: KooltipListener? = null, // callbacks
                   gravity: Int = Gravity.TOP, // anchor to top of view
                   durationTimeMs: Long = DEFAULT_DURATION_TIME, // how long to show it
                   dismissOnTouchOutside: Boolean = false, // whether to dismiss on outside touch
                   shouldHighlight: Boolean = false, // if false, overlay is transparent
                   animated: Boolean = true,
                   customView: View? = null, // custom view (optional, overrides text)
                   customAnimationStyle: Int? = null): Kooltip {
           return Kooltip(contextRef, anchorView, contentText, shouldShow, listener, gravity, durationTimeMs, dismissOnTouchOutside,
                   shouldHighlight, animated, customView, customAnimationStyle)
        }
    }

    val isValid: Boolean
        get() = !isDismissed && contextRef.get() != null && shouldShow()

    private val isShowing: Boolean
        get() = popupWindow.isShowing

    /** flag for whether we've dismissed already or not **/
    private var isDismissed: Boolean = false

    /** the PopupWindow showing this tooltip **/
    private val popupWindow: PopupWindow by lazy {
        val context = contextRef.get() ?: throw Throwable("null context")
        createPopupWindow(context)
    }
    /** ContentLayout + contentView are what show in the popup  **/
    private val contentLayout: View by lazy {
        val context = contextRef.get() ?: throw Throwable("null context")
        createContentLayout(context)
    }
    private val contentView: View by lazy {
        val context = contextRef.get() ?: throw Throwable("null context")
        createContentView(context)
    }
    /** The root frame layout of the anchor. **/
    private val rootView: ViewGroup? by lazy {
        Utils.findFrameLayout(anchorView)
    }

    private var arrowDirection: Int = Utils.tooltipGravityToArrowDirection(gravity)
    private val arrowColor = Utils.getColor(contextRef.get()!!, mDefaultArrowColorRes)
    private val arrowDrawable: ArrowDrawable by lazy { ArrowDrawable(arrowColor, arrowDirection) }
    private val arrowView: ImageView by lazy {
        val context = contextRef.get() ?: throw Throwable("null context")
        createArrowView(context)
    }

    private var animator: AnimatorSet? = null
    private val animationPadding: Float? = contextRef.get()?.resources?.getDimension(mDefaultAnimationPaddingRes)
    private val animationDuration: Int? = contextRef.get()?.resources?.getInteger(mDefaultAnimationDurationRes)
    private val maxTooltipWidth: Int? = contextRef.get()?.resources?.getDimension(R.dimen.default_tooltip_width)?.toInt()
    private val tooltipOffsetY: Int? = contextRef.get()?.resources?.getDimension(R.dimen.tooltip_offset_y)?.toInt()
    private val tooltipOffsetX: Int? = contextRef.get()?.resources?.getDimension(R.dimen.tooltip_offset_x)?.toInt()

    private val locationLayoutListener = object : ViewTreeObserver.OnGlobalLayoutListener {
        override fun onGlobalLayout() {
            if (!isValid) return

            if (contentView.width > maxTooltipWidth!!) {
                Utils.setWidth(contentView, maxTooltipWidth)
                popupWindow.update(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                return
            }

            Utils.removeOnGlobalLayoutListener(popupWindow.contentView, this)
            popupWindow.contentView.viewTreeObserver.addOnGlobalLayoutListener(arrowLayoutListener)
            val location = Utils.calculatePopupLocation(popupWindow, anchorView, gravity, tooltipOffsetX!!, tooltipOffsetY!!)
            popupWindow.isClippingEnabled = true
            popupWindow.update(location.x.toInt(), location.y.toInt(), popupWindow.width, popupWindow.height)
            popupWindow.contentView.requestLayout()
        }
    }

    // Optional overlay view if should highlight.
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
            rootView?.addView(view)
            view
        }
    }

    /**
     * Start animating the window.
     */
    private val animationLayoutListener = object : ViewTreeObserver.OnGlobalLayoutListener {
        override fun onGlobalLayout() {
            Utils.removeOnGlobalLayoutListener(popupWindow.contentView, this)
            if (!isValid) return
            if (shouldAnimate) startEnterAnimation()
            popupWindow.contentView.requestLayout()
        }
    }

    /**
     * Auto-dismiss when the rootview is no longer showing.
     */
    private val autoDismissLayoutListener = ViewTreeObserver.OnGlobalLayoutListener {
        if (rootView?.isShown == false) dismiss()
    }

    /**
     * Notify listeners the tooltip is now showing.
     */
    private val showLayoutListener = object : ViewTreeObserver.OnGlobalLayoutListener {
        override fun onGlobalLayout() {
            Utils.removeOnGlobalLayoutListener(popupWindow.contentView, this)
            if (!isValid) return
            listener?.onShow(this@Kooltip)
            contentLayout.visibility = View.VISIBLE
        }
    }

    private val arrowLayoutListener = object : ViewTreeObserver.OnGlobalLayoutListener {
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
                    x = if (newX + arrowView.width.toFloat() + x > contentViewRect.width()) {
                        contentViewRect.width() - arrowView.width.toFloat() - x
                    } else {
                        newX
                    }
                }
                y = arrowView.top.toFloat()
                y += if (arrowDirection == ArrowDrawable.BOTTOM) -1 else +1
            } else {
                y = contentLayout.paddingTop + Utils.pxFromDp(2f)
                val centerY = contentViewRect.height() / 2f - arrowView.height / 2f
                val newY = centerY - (contentViewRect.centerY() - anchorRect.centerY())
                if (newY > y) {
                    y = if (newY + arrowView.height.toFloat() + y > contentViewRect.height()) {
                        contentViewRect.height() - arrowView.height.toFloat() - y
                    } else {
                        newY
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

    // ==== PUBLIC API ===

    fun show() {
        if (isShowing || !isValid) return

        contentLayout.viewTreeObserver.addOnGlobalLayoutListener(locationLayoutListener)
        contentLayout.viewTreeObserver.addOnGlobalLayoutListener(autoDismissLayoutListener)

        rootView?.let {
            it.post {
                if (it.isShown) {
                    popupWindow.showAtLocation(rootView, Gravity.NO_GRAVITY, it.width, it.height)
//                    popupWindow.showAsDropDown(rootView, it.width, it.height, gravity)
                } else {
                    Log.e(TAG, "Tooltip cannot be shown, root view is invalid or has been closed.")
                }
            }

            it.postDelayed({
                dismiss()
            }, durationTimeMs)
        }
    }

    private fun startEnterAnimation() {
        if (!isValid) return

        val entranceAnim = ObjectAnimator.ofFloat(contentLayout, "alpha", 0f, 1f)
        entranceAnim.duration = 300
        entranceAnim.interpolator = DecelerateInterpolator()

        // float up/down or left/right
        val property = if (gravity == Gravity.TOP || gravity == Gravity.BOTTOM) "translationY" else "translationX"
        val anim1 = ObjectAnimator.ofFloat(contentLayout, property, -animationPadding!!, animationPadding)
        anim1.duration = animationDuration!!.toLong()
        anim1.interpolator = AccelerateDecelerateInterpolator()
        anim1.repeatCount = INFINITE
        anim1.repeatMode = REVERSE

        animator = AnimatorSet()
        animator?.apply {
            playSequentially(entranceAnim, anim1)
            start()
        }
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

        // clear all
        animator?.let {
            it.removeAllListeners()
            it.end()
            it.cancel()
        }
        animator = null

        if (rootView != null && shouldHighlight && tooltipOverlay != null) {
            rootView!!.removeView(tooltipOverlay)
        }

        Utils.removeOnGlobalLayoutListener(popupWindow.contentView, locationLayoutListener)
        Utils.removeOnGlobalLayoutListener(popupWindow.contentView, arrowLayoutListener)
        Utils.removeOnGlobalLayoutListener(popupWindow.contentView, showLayoutListener)
        Utils.removeOnGlobalLayoutListener(popupWindow.contentView, animationLayoutListener)
        Utils.removeOnGlobalLayoutListener(popupWindow.contentView, autoDismissLayoutListener)
    }

    private fun createPopupWindow(context: Context): PopupWindow {
        val popupWindow = PopupWindow(context, null, DEFAULT_POPUP_WINDOW_STYLE)
        popupWindow.setOnDismissListener(this)
        popupWindow.width = ViewGroup.LayoutParams.WRAP_CONTENT
        popupWindow.height = ViewGroup.LayoutParams.WRAP_CONTENT
        popupWindow.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        popupWindow.isOutsideTouchable = true
        popupWindow.isTouchable = true
        if (customAnimationStyle!= null) {
            popupWindow.animationStyle = customAnimationStyle!!
        }

        popupWindow.setTouchInterceptor(View.OnTouchListener { v, event ->
            if (!isValid) return@OnTouchListener false

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
        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.LOLLIPOP) {
            popupWindow.elevation = context.resources.getDimension(R.dimen.tooltip_elevation)
        }
        return popupWindow
    }

    private fun createContentView(context: Context): View {
        val view: View = when {
            customView == null && contentText != null -> {
                val tv = TextView(contextRef.get())
                Utils.setTextAppearance(tv, mDefaultTextAppearanceRes)
                tv.setBackgroundColor(Utils.getColor(context, mDefaultBackgroundColorRes))
                tv.setTextColor(Utils.getColor(context, mDefaultTextColorRes))
                tv.text = contentText
                tv
            }
            customView != null && contentText == null -> customView!!
            else -> throw Throwable("you need to pass either contentText or custom view")
        }

        val contentViewParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, 0f)
        contentViewParams.gravity = Gravity.CENTER
        view.layoutParams = contentViewParams
        val padding: Int = context.resources.getDimension(mDefaultPaddingRes).toInt()
        view.setPadding(padding, padding, padding, padding)
        view.background = context.resources.getDrawable(R.drawable.rounded_corners)
        view.setOnClickListener { listener?.onTapInside(this) }
        return view
    }

    /**
     * Default layout wrapping the text view and arrow.
     */
    private fun createContentLayout(context: Context): View {
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

    private fun createArrowView(context: Context): ImageView {
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
        return view
    }

    private fun checkParams() {
        if (contentText == null && customView == null) {
            throw Throwable("either contextText or a customView must be passed")
        }
        if (contextRef.get() == null) {
            throw Throwable("context reference now null")
        }
    }
}