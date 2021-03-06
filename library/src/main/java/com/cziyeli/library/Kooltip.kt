package com.cziyeli.library

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.ValueAnimator.INFINITE
import android.animation.ValueAnimator.REVERSE
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.support.annotation.ColorRes
import android.support.annotation.StyleRes
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

/**
 * Implement to be informed of popup events.
 */
interface KooltipListener {
    /**
     * Called when the tooltip first shows.
     */
    fun onShow(tooltip: Kooltip)

    /**
     * Called when the tooltip is dismissed.
     */
    fun onDismiss(tooltip: Kooltip)

    /**
     * Called when the is a tap inside the tooltip (use this to do a CTA).
     */
    fun onTapInside(tooltip: Kooltip)
}

/**
 * A simple tooltip that shows a 'levitating' popupwindow
 */
class Kooltip(
        // required
        private val contextRef: WeakReference<Context>,
        private val anchorViewRef: WeakReference<View>, // view to anchor to
        private var contentText: String? = null, // text to show (if no custom view)
        private var shouldShow: (() -> Boolean)?, // predicate to determine whether to show
        private var listener: KooltipListener? = null, // callbacks
        private var onDismissListener: PopupWindow.OnDismissListener? = null,
        // default config (customizable)
        val gravity: Int = Gravity.TOP, // anchor to top of view
        private val durationTimeMs: Long = DEFAULT_DURATION_TIME, // how long to show it
        private val dismissOnTouchOutside: Boolean = false, // whether to dismiss on outside touch
        private var shouldHighlight: Boolean = false, // if false, overlay is transparent
        private var shouldAnimate: Boolean = true,
        @ColorRes private var backgroundColorRes: Int = R.color.default_tooltip_background,
        @ColorRes private var textColorRes: Int = R.color.default_text_color,
        @StyleRes private var textAppearanceRes: Int = R.style.default_text_appearance,
        // optional custom stuff
        private var customView: View? = null, // custom view (optional, overrides text)
        private var customAnimationStyle: Int? = null
): PopupWindow.OnDismissListener {
    private val TAG = "kooltip"
    companion object {
        const val DEFAULT_DURATION_TIME: Long = 30000 // show for 30s
        const val DEFAULT_POPUP_WINDOW_STYLE = android.R.attr.popupWindowStyle

        fun create(contextRef: WeakReference<Context>,
                   anchorViewRef: WeakReference<View>, // view to anchor to
                   contentText: String? = null, // text to show (if no custom view)
                   shouldShow: () -> Boolean, // predicate to determine whether to show
                   listener: KooltipListener? = null, // callbacks
                   onDismissListener: PopupWindow.OnDismissListener? = null, // separate listener
                   gravity: Int = Gravity.TOP, // anchor to top of view
                   durationTimeMs: Long = DEFAULT_DURATION_TIME, // how long to show it
                   dismissOnTouchOutside: Boolean = false, // whether to dismiss on outside touch
                   shouldHighlight: Boolean = false, // if false, overlay is transparent
                   animated: Boolean = true,
                   @ColorRes backgroundColorRes: Int = R.color.default_tooltip_background,
                   @ColorRes textColorRes: Int = R.color.default_text_color,
                   @StyleRes textAppearance: Int = R.style.default_text_appearance,
                   customView: View? = null, // custom view (optional, overrides text)
                   customAnimationStyle: Int? = null): Kooltip {
            return Kooltip(contextRef, anchorViewRef, contentText, shouldShow, listener, onDismissListener, gravity, durationTimeMs,
                    dismissOnTouchOutside, shouldHighlight, animated, backgroundColorRes, textColorRes, textAppearance, customView, customAnimationStyle)
        }
    }

    /**
     * Whether this is valid to interact with and show.
     */
    private val isShowable: Boolean
        get() = !isDismissed && contextRef.get() != null && popupWindow != null && anchorView != null && shouldShow?.invoke() == true

    private val isShowing: Boolean
        get() = popupWindow?.isShowing == true

    /** Flag for whether we've dismissed already.**/
    private var isDismissed: Boolean = false

    /**
     * The anchor view
     */
    private val anchorView: View?
        get() = anchorViewRef.get()

    /** Weak reference to the PopupWindow showing this tooltip **/
    private var popupWindowRef: WeakReference<AnchoredPopupWindow>
    private val popupWindow: AnchoredPopupWindow?
        get() = popupWindowRef.get()

    /** Container wrapping the content view and the arrow.  **/

    private lateinit var contentLayout: View
    /**
     * The main default content view showing the text.
     */
    private lateinit var contentView: View
    /** (Optional) The root frame layout of the anchor, used for figuring out highlight. **/
    private val rootView: ViewGroup? by lazy {
        Utils.findFrameLayout(anchorView)
    }

    private var arrowDirection: Int = Utils.tooltipGravityToArrowDirection(gravity)
    private val arrowColor = Utils.getColor(contextRef.get()!!, R.color.default_arrow_color)
    private val arrowDrawable: ArrowDrawable by lazy { ArrowDrawable(arrowColor, arrowDirection) }
    private lateinit var arrowView: ImageView

    private var animator: AnimatorSet? = null
    private val animationPadding: Float? = contextRef.get()?.resources?.getDimension(R.dimen.default_animation_padding)
    private val animationDuration: Int? = contextRef.get()?.resources?.getInteger(R.integer.tooltip_animation_duration)
    private val maxTooltipWidth: Int? = contextRef.get()?.resources?.getDimension(R.dimen.max_tooltip_width)?.toInt()
    private val tooltipOffsetY: Int? = contextRef.get()?.resources?.getDimension(R.dimen.tooltip_offset_y)?.toInt()
    private val tooltipOffsetX: Int? = contextRef.get()?.resources?.getDimension(R.dimen.tooltip_offset_x)?.toInt()

    /**
     * Location listener repositions the popup window to the anchor
     * (otherwise, the top-left of the popup window is used as anchor point
     * which may cover the anchor).
     */
    private val locationLayoutListener = object : ViewTreeObserver.OnGlobalLayoutListener {
        override fun onGlobalLayout() {
            if (!isShowable) return

            if (contentView.width > maxTooltipWidth!!) {
                Utils.setWidth(contentView, maxTooltipWidth)
                popupWindow?.update(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                return
            }

            Utils.removeOnGlobalLayoutListener(popupWindow?.contentView!!, this)
            popupWindow?.apply {
                contentView.viewTreeObserver.addOnGlobalLayoutListener(arrowLayoutListener)
                val location = Utils.calculatePopupLocation(this, anchorView!!, gravity, tooltipOffsetX!!, tooltipOffsetY!!)
                isClippingEnabled = true
                Log.i(TAG, "locationLayoutListener -- x, y: ${location.x}, ${location.y} w, h: $width, $height")
                // update with real positions
                update(location.x.toInt(), location.y.toInt(), width, height)
                contentView.requestLayout()
            }
        }
    }

    /**
     * Start animating the window.
     */
    private val animationLayoutListener = object : ViewTreeObserver.OnGlobalLayoutListener {
        override fun onGlobalLayout() {
            if (!isShowable) return
            Utils.removeOnGlobalLayoutListener(popupWindow!!.contentView, this)
            if (shouldAnimate) {
                startEnterAnimation()
                popupWindow?.contentView?.requestLayout()
            }
        }
    }

    /**
     * Auto-dismiss when the rootview is no longer showing.
     */
    private val autoDismissLayoutListener = ViewTreeObserver.OnGlobalLayoutListener {
        if (anchorView == null || anchorView?.isShown == false || anchorView?.isAttachedToWindow == false) {
            dismiss()
        }
    }

    /**
     * Notify listeners the tooltip is now showing.
     */
    private val showLayoutListener = object : ViewTreeObserver.OnGlobalLayoutListener {
        override fun onGlobalLayout() {
            if (!isShowable) return
            Utils.removeOnGlobalLayoutListener(popupWindow!!.contentView, this)
            listener?.onShow(this@Kooltip)
            contentLayout.visibility = View.VISIBLE
        }
    }

    /**
     * Detect the arrow layout and trigger the animation.
     */
    private val arrowLayoutListener = object : ViewTreeObserver.OnGlobalLayoutListener {
        override fun onGlobalLayout() {
            if (!isShowable) return
            val popup = popupWindow!!
            Utils.removeOnGlobalLayoutListener(popup.contentView, this)
            val contentViewRect = Utils.calculateRectOnScreen(contentLayout) ?: return
            val anchorRect = Utils.calculateRectOnScreen(anchorView) ?: return

            // attach animation and shown listeners to the contentview
            popup.contentView.viewTreeObserver.addOnGlobalLayoutListener(animationLayoutListener)
            popup.contentView.viewTreeObserver.addOnGlobalLayoutListener(showLayoutListener)

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

    // Optional overlay view if this should highlight the anchor.
    private val tooltipOverlay: TooltipOverlay? by lazy {
        if (!shouldHighlight || !isShowable) {
            null
        } else {
            val anchorView = anchorView!!
            val view = TooltipOverlay(
                    anchorView.context,
                    anchorView,
                    HIGHLIGHT_SHAPE_RECTANGULAR,
                    anchorView.context.resources.getDimension(R.dimen.default_overlay_offset))
            view.layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            rootView?.addView(view)
            view
        }
    }

    init {
        checkParams()
        // create all the necessary views
        val context = contextRef.get() ?: throw Throwable("null context")
        val popupWindow = createPopupWindow(context)
        popupWindowRef = WeakReference(popupWindow)
        contentView = createContentView(context)
        arrowView = createArrowView(context)
        contentLayout = createContentLayout(context, contentView, arrowView)

        if (isShowable) {
            popupWindow.contentView = contentLayout
        }
    }

    // ==== PUBLIC API ===

    fun show() {
        if (isShowing || !isShowable) return

        // Reposition the popup window correctly once its content layout is drawn.
        contentLayout.viewTreeObserver.addOnGlobalLayoutListener(locationLayoutListener)
        // Auto-dismiss the popup if the anchor view is detached.
        contentLayout.viewTreeObserver.addOnGlobalLayoutListener(autoDismissLayoutListener)

        anchorView?.let {
            it.post {
                if (it.isShown && popupWindow != null) {
                    val aboveY = -(it.measuredHeight + tooltipOffsetY!!)
                    val location = Utils.calculatePopupLocation(popupWindow!!, anchorView!!, gravity, tooltipOffsetX!!, tooltipOffsetY!!)
                    Log.i(TAG, "SHOW -- anchor width, height: ${it.width}, ${it.height} " +
                            "-- offsetY: $aboveY, offsetX: ${-tooltipOffsetX} -- LOCATION: $location")
                    popupWindow?.showAtLocationAnchored(it, gravity, it.width, it.height, tooltipOffsetX, tooltipOffsetY)
                } else {
                    Log.e(TAG, "Tooltip cannot be shown -- anchorView shown: ${it.isShown} -- popupWindow: $popupWindow")
                }
            }
            it.postDelayed({
                dismiss()
            }, durationTimeMs)
        }
    }

    fun dismiss() {
        if (popupWindow?.isShowing == true) {
            popupWindow?.dismiss()
        }
    }

    override fun onDismiss() {
        isDismissed = true

        // notify listeners
        listener?.onDismiss(this)
        onDismissListener?.onDismiss()

        listener = null
        onDismissListener = null
        shouldShow = null

        // clear all animations and views
        animator?.let {
            it.removeAllListeners()
            it.end()
            it.cancel()
        }
        animator = null

        if (shouldHighlight && rootView != null && tooltipOverlay != null) {
            rootView!!.removeView(tooltipOverlay)
        }

        Utils.removeOnGlobalLayoutListener(contentView, locationLayoutListener)
        Utils.removeOnGlobalLayoutListener(contentView, arrowLayoutListener)
        Utils.removeOnGlobalLayoutListener(contentView, showLayoutListener)
        Utils.removeOnGlobalLayoutListener(contentView, animationLayoutListener)
        Utils.removeOnGlobalLayoutListener(contentView, autoDismissLayoutListener)
    }

    /**
     * Start the levitating animation upon showing.
     */
    private fun startEnterAnimation() {
        if (!isShowable) return

        // entrance fade-in
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

    private fun createPopupWindow(context: Context): AnchoredPopupWindow {
        val popupWindow = AnchoredPopupWindow(context, null, DEFAULT_POPUP_WINDOW_STYLE)
        popupWindow.setOnDismissListener(this)
        popupWindow.width = ViewGroup.LayoutParams.WRAP_CONTENT
        popupWindow.height = ViewGroup.LayoutParams.WRAP_CONTENT
        popupWindow.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        popupWindow.isOutsideTouchable = true // deliver events to window behind
        popupWindow.isTouchable = true
        if (customAnimationStyle!= null) {
            popupWindow.animationStyle = customAnimationStyle!!
        }

        // intercept touches
        popupWindow.setTouchInterceptor(View.OnTouchListener { _, event ->
            if (!isShowable) return@OnTouchListener false

//            val x = event.x.toInt()
//            val y = event.y.toInt()

            when {
            // not dismissing on touch outside and touched outside
//                (!dismissOnTouchOutside && event.action == MotionEvent.ACTION_DOWN
//                        && (x < 0 || x >= contentLayout.measuredWidth || y < 0 || y >= contentLayout.measuredHeight)) -> {
//                    return@OnTouchListener false
//                }
            // not dismissing on touch outside and outside
                (!dismissOnTouchOutside && event.action == MotionEvent.ACTION_OUTSIDE) -> {
                    return@OnTouchListener true
                }
            // dismissing out touch outside
//                (event.action == MotionEvent.ACTION_DOWN && dismissOnTouchOutside) -> {
//                    dismiss()
//                    return@OnTouchListener false // pass through
//                }
                else -> return@OnTouchListener false
            }
        })
        popupWindow.isClippingEnabled = false
        popupWindow.isFocusable = false
        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.LOLLIPOP) {
            popupWindow.elevation = context.resources.getDimension(R.dimen.tooltip_elevation)
        }
        return popupWindow
    }

    /**
     * Create the default main content view, setting the given text.
     */
    private fun createContentView(context: Context): View {
        val view: View = when {
            customView == null && contentText != null -> {
                val tv = TextView(contextRef.get())
                tv.setBackgroundColor(Utils.getColor(context, backgroundColorRes))
                tv.setTextColor(Utils.getColor(context, textColorRes))
                Utils.setTextAppearance(tv, textAppearanceRes)
                tv.text = contentText
                tv
            }
            customView != null && contentText == null -> customView!!
            else -> throw Throwable("you need to pass either contentText or custom view")
        }

        val contentViewParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, 0f)
        contentViewParams.gravity = Gravity.CENTER
        view.layoutParams = contentViewParams
        val padding: Int = context.resources.getDimension(R.dimen.default_tooltip_padding).toInt()
        view.setPadding(padding, padding, padding, padding)
        view.background = context.resources.getDrawable(R.drawable.rounded_corners)
        view.setOnClickListener { listener?.onTapInside(this) }

        return view
    }

    /**
     * Default layout wrapping the content view and arrow.
     */
    private fun createContentLayout(context: Context, contentView: View, arrowView: View): View {
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

        // start as invisible until we've drawn in the right position
        linearLayout.visibility = View.INVISIBLE
        return linearLayout
    }

    private fun createArrowView(context: Context): ImageView {
        val view = ImageView(context)
        view.setImageDrawable(arrowDrawable)
        val arrowWidth = context.resources.getDimension(R.dimen.default_arrow_width)
        val arrowHeight = context.resources.getDimension(R.dimen.default_arrow_height)
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