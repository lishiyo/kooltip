KoolTip
--------

Super simple tooltip library with a little levitating animation:
![demo gif](screenshots/basic.gif "Demo")


Why not just use a generic `PopupWindow`? Kooltip's main benefit is the ability to follow the anchor view as it scrolls (for example, when 
it's a recyclerview item!) - while the usual `PopupWindow#showAsDropdown` is capable of this, it requires you to know your PopupWindow's 
width/height in advance, to set offsets that do not cover the anchor view. Kooltip allows for `wrap-content` PopupWindows that 
auto-adjust to the desired gravity and track the anchor view as it scrolls.
 
### Usage

```kotlin
// create the tooltip
val tooltip = Kooltip.create(

		// required
		contextRef = WeakReference(this),
		anchorViewRef = WeakReference(view), // view to anchor to
		contentText = text, // text to show (if no custom view)
		shouldShow = { true }, // predicate to determine whether to show
		
		// default config - customizations
		gravity = Gravity.TOP,
		durationTimeMs = DEFAULT_DURATION_TIME,
		dismissOnTouchOutside = false, // whether to dismiss on outside touch
		shouldHighlight = false, // whether to highlight the anchor view (TODO) 
		shouldAnimate = true, // whether to show the floating animation
		@ColorRes backgroundColorRes = R.color.default_tooltip_background,
		textColorRes = R.color.default_text_color,
		textAppearanceRes = R.style.default_text_appearance,
		
		// optional custom stuff
		listener = kooltipListener, // callbacks for on dismiss
		customView = null, // custom view for the popup window, overrides text view
		customAnimationStyle = null // custom animation, overrides default
)

// now show it
tooltip.show()
```