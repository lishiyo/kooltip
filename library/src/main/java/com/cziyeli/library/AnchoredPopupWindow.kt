package com.cziyeli.library

import android.content.Context
import android.util.AttributeSet
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.ViewTreeObserver
import android.widget.PopupWindow
import java.lang.ref.WeakReference

/**
 * A custom [PopupWindow] that scrolls with its anchor (we use this instead of [PopupWindow#showAsDropdown]
 * because that cannot readjust its offsets upon scrolling, and we don't know our initial offsets until
 * the content layout has been drawn).
 *
 * Created by connieli on 3/4/18.
 */
class AnchoredPopupWindow @JvmOverloads constructor(
        context: Context,
        attrs: AttributeSet? = null,
        defStyle: Int = 0,
        defStyleRes: Int = 0
) : PopupWindow(context, attrs, defStyle, defStyleRes) {
    private val TAG = "kooltip"
    private var mAnchorRef: WeakReference<View>? = null
    private val mAnchor: View?
            get() = if (mAnchorRef != null && mAnchorRef?.get() != null) mAnchorRef?.get() else null

    private val onScrollChangedListener = ViewTreeObserver.OnScrollChangedListener {
        this.alignToAnchor()
    }
    // auto-dismiss when anchor is detached
    private var onAnchorDetachedListener: View.OnAttachStateChangeListener? = object : View.OnAttachStateChangeListener {
        override fun onViewDetachedFromWindow(v: View?) {
            Log.i(TAG, "view detached!")
            v?.run {
                dismiss()
            }
        }

        override fun onViewAttachedToWindow(v: View?) {
            Log.i(TAG, "view attached!")
        }

    }

    private var gravity: Int = Gravity.TOP
    private var startingX: Int = 0
    private var startingY: Int = 0
    private var offsetX: Int = 0
    private var offsetY: Int = 0

    fun showAtLocationAnchored(anchor: View,
                               gravity: Int = Gravity.TOP,
                               x: Int = 0,
                               y: Int = 0,
                               offsetX: Int = 0,
                               offsetY: Int = 0,
                               anchorDetachedListener: View.OnAttachStateChangeListener? = onAnchorDetachedListener
    ) {
        super.showAtLocation(anchor, gravity, x, y)

        // attach scroll listeners
        val vto = anchor.viewTreeObserver
        vto?.addOnScrollChangedListener(onScrollChangedListener)
        anchorDetachedListener?.let {
            onAnchorDetachedListener = it
            anchor.addOnAttachStateChangeListener(it)
        }
//        val anchorRoot = anchor.rootView
//        anchorRoot.addOnAttachStateChangeListener(mOnAnchorRootDetachedListener)
//        anchorRoot.addOnLayoutChangeListener(mOnLayoutChangeListener)

        mAnchorRef = WeakReference(anchor)
        this.gravity = gravity
        startingX = x
        startingY = y
        this.offsetX = offsetX
        this.offsetY = offsetY
    }

    override fun dismiss() {
        super.dismiss()
        val anchor = if (mAnchor != null) mAnchor else null
        anchor?.let {
            val vto = anchor.viewTreeObserver
            vto.removeOnScrollChangedListener(onScrollChangedListener)
            anchor.removeOnAttachStateChangeListener(onAnchorDetachedListener)
        }

        mAnchorRef = null
    }

    private fun alignToAnchor() {
        val anchor = if (mAnchor != null) mAnchor else null
        if (anchor != null && anchor.isAttachedToWindow) {
            val location = Utils.calculatePopupLocation(this, anchor, gravity, offsetX, offsetY)
            update(location.x.toInt(), location.y.toInt(), width, height)
        }
    }

}
