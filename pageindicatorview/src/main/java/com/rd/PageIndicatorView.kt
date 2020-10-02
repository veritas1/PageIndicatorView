package com.rd

import android.annotation.SuppressLint
import android.annotation.TargetApi
import android.content.Context
import android.database.DataSetObserver
import android.graphics.Canvas
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.Parcelable
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.View.OnTouchListener
import android.view.ViewGroup
import android.view.ViewParent
import androidx.core.text.TextUtilsCompat
import androidx.core.view.ViewCompat
import androidx.viewpager.widget.PagerAdapter
import androidx.viewpager.widget.ViewPager
import androidx.viewpager.widget.ViewPager.OnAdapterChangeListener
import androidx.viewpager.widget.ViewPager.OnPageChangeListener
import com.rd.animation.type.AnimationType
import com.rd.animation.type.ScaleAnimation
import com.rd.draw.controller.DrawController.ClickListener
import com.rd.draw.data.Indicator
import com.rd.draw.data.Orientation
import com.rd.draw.data.PositionSavedState
import com.rd.draw.data.RtlMode
import com.rd.utils.CoordinatesUtils
import com.rd.utils.DensityUtils
import com.rd.utils.IdUtils

class PageIndicatorView : View, OnPageChangeListener, IndicatorManager.Listener,
    OnAdapterChangeListener, OnTouchListener {
    private var manager: IndicatorManager? = null
    private var setObserver: DataSetObserver? = null
    private var viewPager: ViewPager? = null
    private var isInteractionEnabled = false

    constructor(context: Context?) : super(context) {
        init(null)
    }

    constructor(context: Context?, attrs: AttributeSet?) : super(context, attrs) {
        init(attrs)
    }

    constructor(context: Context?, attrs: AttributeSet?, defStyleAttr: Int) : super(
        context,
        attrs,
        defStyleAttr
    ) {
        init(attrs)
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    constructor(
        context: Context?,
        attrs: AttributeSet?,
        defStyleAttr: Int,
        defStyleRes: Int
    ) : super(context, attrs, defStyleAttr, defStyleRes) {
        init(attrs)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        findViewPager(parent)
    }

    override fun onDetachedFromWindow() {
        unRegisterSetObserver()
        super.onDetachedFromWindow()
    }

    public override fun onSaveInstanceState(): Parcelable? {
        val indicator = manager!!.indicator()
        val positionSavedState = PositionSavedState(super.onSaveInstanceState())
        positionSavedState.selectedPosition = indicator.selectedPosition
        positionSavedState.selectingPosition = indicator.selectingPosition
        positionSavedState.lastSelectedPosition = indicator.lastSelectedPosition
        return positionSavedState
    }

    public override fun onRestoreInstanceState(state: Parcelable) {
        if (state is PositionSavedState) {
            val indicator = manager!!.indicator()
            val positionSavedState = state
            indicator.selectedPosition = positionSavedState.selectedPosition
            indicator.selectingPosition = positionSavedState.selectingPosition
            indicator.lastSelectedPosition = positionSavedState.lastSelectedPosition
            super.onRestoreInstanceState(positionSavedState.superState)
        } else {
            super.onRestoreInstanceState(state)
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val pair = manager!!.drawer().measureViewSize(widthMeasureSpec, heightMeasureSpec)
        setMeasuredDimension(pair.first, pair.second)
    }

    override fun onDraw(canvas: Canvas) {
        manager!!.drawer().draw(canvas)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        manager!!.drawer().touch(event)
        return true
    }

    override fun onTouch(v: View, event: MotionEvent): Boolean {
        if (!manager!!.indicator().isFadeOnIdle) return false
        when (event.action) {
            MotionEvent.ACTION_DOWN -> stopIdleRunnable()
            MotionEvent.ACTION_UP -> startIdleRunnable()
        }
        return false
    }

    override fun onIndicatorUpdated() {
        invalidate()
    }

    override fun onPageScrolled(position: Int, positionOffset: Float, positionOffsetPixels: Int) {
        onPageScroll(position, positionOffset)
    }

    override fun onPageSelected(position: Int) {
        onPageSelect(position)
    }

    override fun onPageScrollStateChanged(state: Int) {
        if (state == ViewPager.SCROLL_STATE_IDLE) {
            manager!!.indicator().isInteractiveAnimation = isInteractionEnabled
        }
    }

    override fun onAdapterChanged(
        viewPager: ViewPager,
        oldAdapter: PagerAdapter?,
        newAdapter: PagerAdapter?
    ) {
        if (manager!!.indicator().isDynamicCount) {
            if (oldAdapter != null && setObserver != null) {
                oldAdapter.unregisterDataSetObserver(setObserver!!)
                setObserver = null
            }
            registerSetObserver()
        }
        updateState()
    }
    /**
     * Return number of circle indicators
     */
    /**
     * Set static number of circle indicators to be displayed.
     *
     * @param count total count of indicators.
     */
    var count: Int
        get() = manager!!.indicator().count
        set(count) {
            if (count >= 0 && manager!!.indicator().count != count) {
                manager!!.indicator().count = count
                updateVisibility()
                requestLayout()
            }
        }

    /**
     * Dynamic count will automatically update number of circle indicators
     * if [ViewPager] page count updates on run-time. If new count will be bigger than current count,
     * selected circle will stay as it is, otherwise it will be set to last one.
     * Note: works if [ViewPager] set and already have it's adapter. See [.setViewPager].
     *
     * @param dynamicCount boolean value to add/remove indicators dynamically.
     */
    fun setDynamicCount(dynamicCount: Boolean) {
        manager!!.indicator().isDynamicCount = dynamicCount
        if (dynamicCount) {
            registerSetObserver()
        } else {
            unRegisterSetObserver()
        }
    }

    /**
     * Fade on idle will make [PageIndicatorView] [View.INVISIBLE] if [ViewPager] is not interacted
     * in time equal to [Indicator.idleDuration]. Take care when setting [PageIndicatorView] alpha
     * manually if this is true. Alpha is used to manage fading and appearance of [PageIndicatorView] and value you provide
     * will be overridden when [PageIndicatorView] enters or leaves idle state.
     *
     * @param fadeOnIdle boolean value to hide [PageIndicatorView] when [ViewPager] is idle
     */
    fun setFadeOnIdle(fadeOnIdle: Boolean) {
        manager!!.indicator().isFadeOnIdle = fadeOnIdle
        if (fadeOnIdle) {
            startIdleRunnable()
        } else {
            stopIdleRunnable()
        }
    }

    /**
     * Set radius in px of each circle indicator. Default value is [Indicator.DEFAULT_RADIUS_DP].
     * Note: make sure you set circle Radius, not a Diameter.
     *
     * @param radiusPx radius of circle in px.
     */
    fun setRadius(radiusPx: Float) {
        var radiusPx = radiusPx
        if (radiusPx < 0) {
            radiusPx = 0f
        }
        manager!!.indicator().radius = radiusPx.toInt()
        invalidate()
    }
    /**
     * Return radius of each circle indicators in px. If custom radius is not set, return
     * default value [Indicator.DEFAULT_RADIUS_DP].
     */
    /**
     * Set radius in dp of each circle indicator. Default value is [Indicator.DEFAULT_RADIUS_DP].
     * Note: make sure you set circle Radius, not a Diameter.
     *
     * @param radiusDp radius of circle in dp.
     */
    var radius: Int
        get() = manager!!.indicator().radius
        set(radiusDp) {
            var radiusDp = radiusDp
            if (radiusDp < 0) {
                radiusDp = 0
            }
            val radiusPx = DensityUtils.dpToPx(radiusDp)
            manager!!.indicator().radius = radiusPx
            invalidate()
        }

    /**
     * Set padding in px between each circle indicator. Default value is [Indicator.DEFAULT_PADDING_DP].
     *
     * @param paddingPx padding between circles in px.
     */
    fun setPadding(paddingPx: Float) {
        var paddingPx = paddingPx
        if (paddingPx < 0) {
            paddingPx = 0f
        }
        manager!!.indicator().padding = paddingPx.toInt()
        invalidate()
    }
    /**
     * Return padding in px between each circle indicator. If custom padding is not set,
     * return default value [Indicator.DEFAULT_PADDING_DP].
     */
    /**
     * Set padding in dp between each circle indicator. Default value is [Indicator.DEFAULT_PADDING_DP].
     *
     * @param paddingDp padding between circles in dp.
     */
    var padding: Int
        get() = manager!!.indicator().padding
        set(paddingDp) {
            var paddingDp = paddingDp
            if (paddingDp < 0) {
                paddingDp = 0
            }
            val paddingPx = DensityUtils.dpToPx(paddingDp)
            manager!!.indicator().padding = paddingPx
            invalidate()
        }
    /**
     * Returns scale factor values used in [AnimationType.SCALE] animation.
     * Defines size of unselected indicator circles in comparing to selected one.
     * Minimum and maximum values are [ScaleAnimation.MAX_SCALE_FACTOR] and [ScaleAnimation.MIN_SCALE_FACTOR].
     * See also [ScaleAnimation.DEFAULT_SCALE_FACTOR].
     *
     * @return float value that indicate scale factor.
     */
    /**
     * Set scale factor used in [AnimationType.SCALE] animation.
     * Defines size of unselected indicator circles in comparing to selected one.
     * Minimum and maximum values are [ScaleAnimation.MAX_SCALE_FACTOR] and [ScaleAnimation.MIN_SCALE_FACTOR].
     * See also [ScaleAnimation.DEFAULT_SCALE_FACTOR].
     *
     * @param factor float value in range between 0 and 1.
     */
    var scaleFactor: Float
        get() = manager!!.indicator().scaleFactor
        set(factor) {
            var factor = factor
            if (factor > ScaleAnimation.MAX_SCALE_FACTOR) {
                factor = ScaleAnimation.MAX_SCALE_FACTOR
            } else if (factor < ScaleAnimation.MIN_SCALE_FACTOR) {
                factor = ScaleAnimation.MIN_SCALE_FACTOR
            }
            manager!!.indicator().scaleFactor = factor
        }

    /**
     * Set stroke width in px to set while [AnimationType.FILL] is selected.
     * Default value is [FillAnimation.DEFAULT_STROKE_DP]
     *
     * @param strokePx stroke width in px.
     */
    fun setStrokeWidth(strokePx: Float) {
        var strokePx = strokePx
        val radiusPx = manager!!.indicator().radius
        if (strokePx < 0) {
            strokePx = 0f
        } else if (strokePx > radiusPx) {
            strokePx = radiusPx.toFloat()
        }
        manager!!.indicator().stroke = strokePx.toInt()
        invalidate()
    }

    /**
     * Set stroke width in dp to set while [AnimationType.FILL] is selected.
     * Default value is [FillAnimation.DEFAULT_STROKE_DP]
     *
     * @param strokeDp stroke width in dp.
     */
    fun setStrokeWidth(strokeDp: Int) {
        var strokePx = DensityUtils.dpToPx(strokeDp)
        val radiusPx = manager!!.indicator().radius
        if (strokePx < 0) {
            strokePx = 0
        } else if (strokePx > radiusPx) {
            strokePx = radiusPx
        }
        manager!!.indicator().stroke = strokePx
        invalidate()
    }

    /**
     * Return stroke width in px if [AnimationType.FILL] is selected, 0 otherwise.
     */
    val strokeWidth: Int
        get() = manager!!.indicator().stroke
    /**
     * Return color of selected circle indicator. If custom unselected color
     * is not set, return default color [ColorAnimation.DEFAULT_SELECTED_COLOR].
     */
    /**
     * Set color of selected state to circle indicator. Default color is [ColorAnimation.DEFAULT_SELECTED_COLOR].
     *
     * @param color color selected circle.
     */
    var selectedColor: Int
        get() = manager!!.indicator().selectedColor
        set(color) {
            manager!!.indicator().selectedColor = color
            invalidate()
        }
    /**
     * Return color of unselected state of each circle indicator. If custom unselected color
     * is not set, return default color [ColorAnimation.DEFAULT_UNSELECTED_COLOR].
     */
    /**
     * Set color of unselected state to each circle indicator. Default color [ColorAnimation.DEFAULT_UNSELECTED_COLOR].
     *
     * @param color color of each unselected circle.
     */
    var unselectedColor: Int
        get() = manager!!.indicator().unselectedColor
        set(color) {
            manager!!.indicator().unselectedColor = color
            invalidate()
        }

    /**
     * Automatically hide (View.INVISIBLE) PageIndicatorView while indicator count is <= 1.
     * Default is true.
     *
     * @param autoVisibility auto hide indicators.
     */
    fun setAutoVisibility(autoVisibility: Boolean) {
        if (!autoVisibility) {
            visibility = VISIBLE
        }
        manager!!.indicator().isAutoVisibility = autoVisibility
        updateVisibility()
    }

    /**
     * Set orientation for indicator, one of HORIZONTAL or VERTICAL.
     * Default is HORIZONTAL.
     *
     * @param orientation an orientation to display page indicators.
     */
    fun setOrientation(orientation: Orientation?) {
        if (orientation != null) {
            manager!!.indicator().orientation = orientation
            requestLayout()
        }
    }

    /**
     * Sets time in millis after which [ViewPager] is considered idle.
     * If [Indicator.fadeOnIdle] is true, [PageIndicatorView] will
     * fade away after entering idle state and appear when it is left.
     *
     * @param duration time in millis after which [ViewPager] is considered idle
     */
    fun setIdleDuration(duration: Long) {
        manager!!.indicator().idleDuration = duration
        if (manager!!.indicator().isFadeOnIdle) {
            startIdleRunnable()
        } else {
            stopIdleRunnable()
        }
    }
    /**
     * Return animation duration time in milliseconds. If custom duration is not set,
     * return default duration time [BaseAnimation.DEFAULT_ANIMATION_TIME].
     */
    /**
     * Set animation duration time in millisecond. Default animation duration time is [BaseAnimation.DEFAULT_ANIMATION_TIME].
     * (Won't affect on anything unless [.setAnimationType] is specified
     * and [.setInteractiveAnimation] is false).
     *
     * @param duration animation duration time.
     */
    var animationDuration: Long
        get() = manager!!.indicator().animationDuration
        set(duration) {
            manager!!.indicator().animationDuration = duration
        }

    /**
     * Set animation type to perform while selecting new circle indicator.
     * Default animation type is [AnimationType.NONE].
     *
     * @param type type of animation, one of [AnimationType]
     */
    fun setAnimationType(type: AnimationType?) {
        manager!!.onValueUpdated(null)
        if (type != null) {
            manager!!.indicator().animationType = type
        } else {
            manager!!.indicator().animationType = AnimationType.NONE
        }
        invalidate()
    }

    /**
     * Interactive animation will animate indicator smoothly
     * from position to position based on user's current swipe progress.
     * (Won't affect on anything unless [.setViewPager] is specified).
     *
     * @param isInteractive value of animation to be interactive or not.
     */
    fun setInteractiveAnimation(isInteractive: Boolean) {
        manager!!.indicator().isInteractiveAnimation = isInteractive
        isInteractionEnabled = isInteractive
    }

    /**
     * Set [ViewPager] to add [ViewPager.OnPageChangeListener] and automatically
     * handle selecting new indicators (and interactive animation effect if it is enabled).
     *
     * @param pager instance of [ViewPager] to work with
     */
    @SuppressLint("ClickableViewAccessibility")
    fun setViewPager(pager: ViewPager?) {
        releaseViewPager()
        if (pager == null) {
            return
        }
        viewPager = pager
        viewPager!!.addOnPageChangeListener(this)
        viewPager!!.addOnAdapterChangeListener(this)
        viewPager!!.setOnTouchListener(this)
        manager!!.indicator().viewPagerId = viewPager!!.id
        setDynamicCount(manager!!.indicator().isDynamicCount)
        updateState()
    }

    /**
     * Release [ViewPager] and stop handling events of [ViewPager.OnPageChangeListener].
     */
    fun releaseViewPager() {
        if (viewPager != null) {
            viewPager!!.removeOnPageChangeListener(this)
            viewPager!!.removeOnAdapterChangeListener(this)
            viewPager = null
        }
    }

    /**
     * Specify to display PageIndicatorView with Right to left layout or not.
     * One of [RtlMode]: Off (Left to right), On (Right to left)
     * or Auto (handle this mode automatically based on users language preferences).
     * Default is Off.
     *
     * @param mode instance of [RtlMode]
     */
    fun setRtlMode(mode: RtlMode?) {
        val indicator = manager!!.indicator()
        if (mode == null) {
            indicator.rtlMode = RtlMode.Off
        } else {
            indicator.rtlMode = mode
        }
        if (viewPager == null) {
            return
        }
        val selectedPosition = indicator.selectedPosition
        var position = selectedPosition
        if (isRtl) {
            position = indicator.count - 1 - selectedPosition
        } else if (viewPager != null) {
            position = viewPager!!.currentItem
        }
        indicator.lastSelectedPosition = position
        indicator.selectingPosition = position
        indicator.selectedPosition = position
        invalidate()
    }
    /**
     * Return position of currently selected circle indicator.
     */
    /**
     * Set specific circle indicator position to be selected. If position < or > total count,
     * accordingly first or last circle indicator will be selected.
     *
     * @param position position of indicator to select.
     */
    var selection: Int
        get() = manager!!.indicator().selectedPosition
        set(position) {
            var position = position
            val indicator = manager!!.indicator()
            position = adjustPosition(position)
            if (position == indicator.selectedPosition || position == indicator.selectingPosition) {
                return
            }
            indicator.isInteractiveAnimation = false
            indicator.lastSelectedPosition = indicator.selectedPosition
            indicator.selectingPosition = position
            indicator.selectedPosition = position
            manager!!.animate().basic()
        }

    /**
     * Set specific circle indicator position to be selected without any kind of animation. If position < or > total count,
     * accordingly first or last circle indicator will be selected.
     *
     * @param position position of indicator to select.
     */
    fun setSelected(position: Int) {
        val indicator = manager!!.indicator()
        val animationType = indicator.animationType
        indicator.animationType = AnimationType.NONE
        selection = position
        indicator.animationType = animationType
    }

    /**
     * Clears selection of all indicators
     */
    fun clearSelection() {
        //TODO check
        val indicator = manager!!.indicator()
        indicator.isInteractiveAnimation = false
        indicator.lastSelectedPosition = Indicator.COUNT_NONE
        indicator.selectingPosition = Indicator.COUNT_NONE
        indicator.selectedPosition = Indicator.COUNT_NONE
        manager!!.animate().basic()
    }

    /**
     * Set progress value in range [0 - 1] to specify state of animation while selecting new circle indicator.
     *
     * @param selectingPosition selecting position with specific progress value.
     * @param progress          float value of progress.
     */
    fun setProgress(selectingPosition: Int, progress: Float) {
        var selectingPosition = selectingPosition
        var progress = progress
        val indicator = manager!!.indicator()
        if (!indicator.isInteractiveAnimation) {
            return
        }
        val count = indicator.count
        if (count <= 0 || selectingPosition < 0) {
            selectingPosition = 0
        } else if (selectingPosition > count - 1) {
            selectingPosition = count - 1
        }
        if (progress < 0) {
            progress = 0f
        } else if (progress > 1) {
            progress = 1f
        }
        if (progress == 1f) {
            indicator.lastSelectedPosition = indicator.selectedPosition
            indicator.selectedPosition = selectingPosition
        }
        indicator.selectingPosition = selectingPosition
        manager!!.animate().interactive(progress)
    }

    fun setClickListener(listener: ClickListener?) {
        manager!!.drawer().setClickListener(listener)
    }

    private fun init(attrs: AttributeSet?) {
        setupId()
        initIndicatorManager(attrs)
        if (manager!!.indicator().isFadeOnIdle) {
            startIdleRunnable()
        }
    }

    private fun setupId() {
        if (id == NO_ID) {
            id = IdUtils.generateViewId()
        }
    }

    private fun initIndicatorManager(attrs: AttributeSet?) {
        manager = IndicatorManager(this)
        manager!!.drawer().initAttributes(context, attrs)
        val indicator = manager!!.indicator()
        indicator.paddingLeft = paddingLeft
        indicator.paddingTop = paddingTop
        indicator.paddingRight = paddingRight
        indicator.paddingBottom = paddingBottom
        isInteractionEnabled = indicator.isInteractiveAnimation
    }

    private fun registerSetObserver() {
        if (setObserver != null || viewPager == null || viewPager!!.adapter == null) {
            return
        }
        setObserver = object : DataSetObserver() {
            override fun onChanged() {
                updateState()
            }
        }
        try {
            viewPager!!.adapter!!.registerDataSetObserver(setObserver!!)
        } catch (e: IllegalStateException) {
            e.printStackTrace()
        }
    }

    private fun unRegisterSetObserver() {
        if (setObserver == null || viewPager == null || viewPager!!.adapter == null) {
            return
        }
        try {
            viewPager!!.adapter!!.unregisterDataSetObserver(setObserver!!)
            setObserver = null
        } catch (e: IllegalStateException) {
            e.printStackTrace()
        }
    }

    private fun updateState() {
        if (viewPager == null || viewPager!!.adapter == null) {
            return
        }
        val count = viewPager!!.adapter!!.count
        val selectedPos =
            if (isRtl) count - 1 - viewPager!!.currentItem else viewPager!!.currentItem
        manager!!.indicator().selectedPosition = selectedPos
        manager!!.indicator().selectingPosition = selectedPos
        manager!!.indicator().lastSelectedPosition = selectedPos
        manager!!.indicator().count = count
        manager!!.animate().end()
        updateVisibility()
        requestLayout()
    }

    private fun updateVisibility() {
        if (!manager!!.indicator().isAutoVisibility) {
            return
        }
        val count = manager!!.indicator().count
        val visibility = visibility
        if (visibility != VISIBLE && count > Indicator.MIN_COUNT) {
            setVisibility(VISIBLE)
        } else if (visibility != INVISIBLE && count <= Indicator.MIN_COUNT) {
            setVisibility(INVISIBLE)
        }
    }

    private fun onPageSelect(position: Int) {
        var position = position
        val indicator = manager!!.indicator()
        val canSelectIndicator = isViewMeasured
        val count = indicator.count
        if (canSelectIndicator) {
            if (isRtl) {
                position = count - 1 - position
            }
            selection = position
        }
    }

    private fun onPageScroll(position: Int, positionOffset: Float) {
        val indicator = manager!!.indicator()
        val animationType = indicator.animationType
        val interactiveAnimation = indicator.isInteractiveAnimation
        val canSelectIndicator =
            isViewMeasured && interactiveAnimation && animationType != AnimationType.NONE
        if (!canSelectIndicator) {
            return
        }
        val progressPair = CoordinatesUtils.getProgress(indicator, position, positionOffset, isRtl)
        val selectingPosition = progressPair.first
        val selectingProgress = progressPair.second
        setProgress(selectingPosition, selectingProgress)
    }

    private val isRtl: Boolean
        private get() {
            return when (manager!!.indicator().rtlMode) {
                RtlMode.On -> true
                RtlMode.Off -> false
                RtlMode.Auto -> TextUtilsCompat.getLayoutDirectionFromLocale(
                    context.resources.configuration.locale
                ) == ViewCompat.LAYOUT_DIRECTION_RTL
            }
            return false
        }
    private val isViewMeasured: Boolean
        private get() = measuredHeight != 0 || measuredWidth != 0

    private fun findViewPager(viewParent: ViewParent?) {
        val isValidParent = viewParent != null &&
                viewParent is ViewGroup && viewParent.childCount > 0
        if (!isValidParent) {
            return
        }
        val viewPagerId = manager!!.indicator().viewPagerId
        val viewPager = findViewPager((viewParent as ViewGroup?)!!, viewPagerId)
        if (viewPager != null) {
            setViewPager(viewPager)
        } else {
            findViewPager(viewParent!!.parent)
        }
    }

    private fun findViewPager(viewGroup: ViewGroup, id: Int): ViewPager? {
        if (viewGroup.childCount <= 0) {
            return null
        }
        val view = viewGroup.findViewById<View>(id)
        return if (view != null && view is ViewPager) {
            view
        } else {
            null
        }
    }

    private fun adjustPosition(position: Int): Int {
        var position = position
        val indicator = manager!!.indicator()
        val count = indicator.count
        val lastPosition = count - 1
        if (position < 0) {
            position = 0
        } else if (position > lastPosition) {
            position = lastPosition
        }
        return position
    }

    private fun displayWithAnimation() {
        animate().cancel()
        animate().alpha(1.0f).duration = Indicator.IDLE_ANIMATION_DURATION.toLong()
    }

    private fun hideWithAnimation() {
        animate().cancel()
        animate().alpha(0f).duration = Indicator.IDLE_ANIMATION_DURATION.toLong()
    }

    private fun startIdleRunnable() {
        HANDLER.removeCallbacks(idleRunnable)
        HANDLER.postDelayed(idleRunnable, manager!!.indicator().idleDuration)
    }

    private fun stopIdleRunnable() {
        HANDLER.removeCallbacks(idleRunnable)
        displayWithAnimation()
    }

    private val idleRunnable = Runnable {
        manager!!.indicator().isIdle = true
        hideWithAnimation()
    }

    companion object {
        private val HANDLER = Handler(Looper.getMainLooper())
    }
}