package com.cziyeli.kooltip

import android.os.Bundle
import android.support.design.widget.BottomNavigationView
import android.support.v7.app.AppCompatActivity
import android.util.Log
import android.view.Gravity
import android.view.View
import com.cziyeli.library.Kooltip
import com.cziyeli.library.KooltipListener
import kotlinx.android.synthetic.main.activity_main.*
import java.lang.ref.WeakReference

class MainActivity : AppCompatActivity() {

    private val mOnNavigationItemSelectedListener = BottomNavigationView.OnNavigationItemSelectedListener { item ->
        when (item.itemId) {
            R.id.navigation_home -> {
                target.setText(R.string.title_home)
                return@OnNavigationItemSelectedListener true
            }
            R.id.navigation_dashboard -> {
                target.setText(R.string.title_dashboard)
                return@OnNavigationItemSelectedListener true
            }
            R.id.navigation_notifications -> {
                target.setText(R.string.title_notifications)
                return@OnNavigationItemSelectedListener true
            }
        }
        false
    }

    private var currentTapsCount: Int = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        navigation.setOnNavigationItemSelectedListener(mOnNavigationItemSelectedListener)
        show_tooltips_btn.setOnClickListener {
            showTooltip(target)
        }
    }

    private val kooltipListener: KooltipListener = object : KooltipListener {
        override fun onShow(tooltip: Kooltip) {
            Log.i("connie", "onShow!")
        }

        override fun onDismiss(tooltip: Kooltip) {
            Log.i("connie", "onDismiss!")
        }

        override fun onTapInside(tooltip: Kooltip) {
            Log.i("connie", "onTapInside!")
            currentTapsCount++
        }
    }

    private fun showTooltip(view: View) {
        val topTooltip = Kooltip.create(
                contextRef = WeakReference(this),
                anchorView = view,
                contentText = "this is pretty kool",
                shouldShow = { currentTapsCount < 4 },
                listener = kooltipListener,
                gravity = Gravity.TOP
        )
        val leftTooltip = Kooltip.create(
                contextRef = WeakReference(this),
                anchorView = view,
                contentText = "this is pretty kool",
                shouldShow = { currentTapsCount < 4 },
                listener = kooltipListener,
                gravity = Gravity.START
        )
        val rightTooltip = Kooltip.create(
                contextRef = WeakReference(this),
                anchorView = view,
                contentText = "this is pretty kool",
                shouldShow = { currentTapsCount < 4 },
                listener = kooltipListener,
                gravity = Gravity.END
        )
        val bottomTooltip = Kooltip.create(
                contextRef = WeakReference(this),
                anchorView = view,
                contentText = "this is pretty kool",
                shouldShow = { currentTapsCount < 4 },
                listener = kooltipListener,
                gravity = Gravity.BOTTOM
        )

        // show all four
        topTooltip.show()
        leftTooltip.show()
        rightTooltip.show()
        bottomTooltip.show()
    }
}
