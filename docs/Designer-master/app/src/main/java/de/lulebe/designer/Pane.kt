package de.lulebe.designer

import android.animation.ObjectAnimator
import android.content.Context
import android.graphics.Color
import android.os.Build
import android.support.v4.content.ContextCompat
import android.support.v4.view.ViewCompat
import android.util.AttributeSet
import android.util.TypedValue
import android.view.Gravity
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.ScrollView
import android.widget.TextView


class Pane : FrameLayout {


    val listeners = mutableListOf<(open: Boolean) -> Unit>()
    fun addOpenListener (l: (open: Boolean) -> Unit) {
        listeners.add(l)
    }
    private fun notifyListeners (open: Boolean) {
        for (l in listeners) {
            l(open)
        }
    }
    val lockListeners = mutableListOf<(open: Boolean) -> Unit>()
    fun addLockListener (l: (locked: Boolean) -> Unit) {
        lockListeners.add(l)
    }
    private fun notifyLockListeners (locked: Boolean) {
        for (l in lockListeners) {
            l(locked)
        }
    }

    private var locked = false
    fun isLocked () = locked

    private var expanded = false
    private var expandedTrans = 0F
    private var direction = 0 //0=right, 1=left, 2=up, 3=down
    private var icons = Array(5, { i ->
        when (i) {
            0 -> R.drawable.ic_keyboard_arrow_left_black_24dp
            1 -> R.drawable.ic_keyboard_arrow_up_black_24dp
            2 -> R.drawable.ic_keyboard_arrow_right_black_24dp
            3 -> R.drawable.ic_keyboard_arrow_down_black_24dp
            else -> R.drawable.ic_lock_outline_black_24dp
        }
    })

    private val btnToggle = ImageView(context)
    private val tvTitle = TextView(context)
    private val headerLayout = FrameLayout(context)



    constructor (context: Context?) : super(context) {
        init(context!!, null)
    }
    constructor(context: Context?, attrs: AttributeSet?) : super(context, attrs) {
        init(context!!, attrs)
    }
    constructor(context: Context?, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr) {
        init(context!!, attrs)
    }
    constructor(context: Context?, attrs: AttributeSet?, defStyleAttr: Int, defStyleRes: Int) : super(context, attrs, defStyleAttr, defStyleRes) {
        init(context!!, attrs)
    }

    fun isExpanded () : Boolean {
        return expanded
    }



    private fun init (context: Context, attrs: AttributeSet?) {
        var black = false
        if (attrs != null) {
            direction = context.obtainStyledAttributes(attrs, R.styleable.Pane).getInt(R.styleable.Pane_direction, 0)
            tvTitle.text = context.obtainStyledAttributes(attrs, R.styleable.Pane).getString(R.styleable.Pane_headertext)
            if (context.obtainStyledAttributes(attrs, R.styleable.Pane).getInt(R.styleable.Pane_colorscheme, 0) == 1) {
                black = true
                icons[0] = R.drawable.ic_keyboard_arrow_left_white_24dp
                icons[1] = R.drawable.ic_keyboard_arrow_up_white_24dp
                icons[2] = R.drawable.ic_keyboard_arrow_right_white_24dp
                icons[3] = R.drawable.ic_keyboard_arrow_down_white_24dp
                icons[4] = R.drawable.ic_lock_outline_white_24dp
            }
        }
        calcWidths()

        val dp224 = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 240F, resources.displayMetrics).toInt()
        val dp64 = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 64F, resources.displayMetrics).toInt()
        val dp48 = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 48F, resources.displayMetrics).toInt()
        val dp12 = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 12F, resources.displayMetrics).toInt()
        val dp8 = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 8F, resources.displayMetrics).toInt()

        headerLayout.layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, dp64)
        headerLayout.setPadding(dp8, dp8, dp8, dp8)
        
        //default View
        val lpTitle = FrameLayout.LayoutParams(dp224-dp48-(dp8*2), dp48)
        if (direction in 2..3)
            lpTitle.width = LayoutParams.WRAP_CONTENT
        val lpBtn = FrameLayout.LayoutParams(dp48, dp48)
        when (direction) {
            0 -> {
                lpTitle.gravity = Gravity.LEFT or Gravity.TOP
                lpTitle.leftMargin = dp8
                lpBtn.gravity = Gravity.RIGHT or Gravity.TOP
            }
            1 -> {
                lpTitle.gravity = Gravity.RIGHT or Gravity.TOP
                lpTitle.rightMargin = dp8
                lpBtn.gravity = Gravity.LEFT or Gravity.TOP
            }
            2 -> {
                lpTitle.gravity = Gravity.LEFT or Gravity.TOP
                lpTitle.leftMargin = dp48 + dp8
                lpBtn.gravity = Gravity.LEFT or Gravity.TOP
            }
            3 -> {
                lpTitle.gravity = Gravity.LEFT or Gravity.BOTTOM
                lpTitle.leftMargin = dp48 + dp8
                lpBtn.gravity = Gravity.LEFT or Gravity.BOTTOM
            }
        }
        
        //Title
        tvTitle.layoutParams = lpTitle
        if (direction == 1)
            tvTitle.gravity = Gravity.RIGHT or Gravity.CENTER_VERTICAL
        else
            tvTitle.gravity = Gravity.CENTER_VERTICAL
        @Suppress("DEPRECATION")
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M)
            tvTitle.setTextAppearance(context, R.style.TextAppearance_Widget_AppCompat_Toolbar_Title)
        else
            tvTitle.setTextAppearance(R.style.TextAppearance_Widget_AppCompat_Toolbar_Title)
        if (black)
            tvTitle.setTextColor(Color.WHITE)
        headerLayout.addView(tvTitle)

        //ToogleButton
        btnToggle.layoutParams = lpBtn
        btnToggle.setPadding(dp12, dp12, dp12, dp12)
        btnToggle.isClickable = true
        btnToggle.isLongClickable = true
        val tv = TypedValue()
        context.theme.resolveAttribute(R.attr.selectableItemBackgroundBorderless, tv, true)
        btnToggle.background = ContextCompat.getDrawable(context, tv.resourceId)
        when (direction) {
            0 -> btnToggle.setImageResource(icons[2])
            1 -> btnToggle.setImageResource(icons[0])
            2 -> btnToggle.setImageResource(icons[1])
            3 -> btnToggle.setImageResource(icons[3])
        }
        btnToggle.setOnClickListener {
            if (locked)
                lock(false)
            else
                expand()
        }
        btnToggle.setOnLongClickListener {
            //btnToggle.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
            lock()
            true
        }
        headerLayout.addView(btnToggle)
        headerLayout.background = background.constantState.newDrawable()
        addView(headerLayout)
    }

    override fun onFinishInflate() {
        super.onFinishInflate()
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return
        val dp2 = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 2F, resources.displayMetrics).toInt()
        val scv = findViewById<ScrollView?>(R.id.vert_scrollview)
        scv?.setOnScrollChangeListener { view, scrollX, scrollY, oldScrollX, oldScrollY ->
            if (scrollY > 0 && ViewCompat.getElevation(headerLayout) == 0F)
                ViewCompat.setElevation(headerLayout, dp2.toFloat())
            else if (scrollY == 0 && ViewCompat.getElevation(headerLayout) != 0F)
                ViewCompat.setElevation(headerLayout, 0F)
        }
    }

    private fun calcWidths() {
        if (direction in 0..1)
            expandedTrans = Math.abs(translationX)
        else
            expandedTrans = Math.abs(translationY)
    }

    fun expand (expanded: Boolean? = null, anim: Boolean = true, overrideLock: Boolean = false) {
        if (locked && !overrideLock)
            return
        val dur: Int
        if (anim)
            dur = resources.getInteger(android.R.integer.config_mediumAnimTime)
        else
            dur = 0
        if (expanded != null)
            this.expanded = expanded
        else
            this.expanded = !this.expanded
        notifyListeners(this.expanded)
        val animator: ObjectAnimator
        if (this.expanded) {
            when (direction) {
                0 -> {
                    btnToggle.setImageResource(icons[0])
                    animator = ObjectAnimator.ofFloat(this, "translationX", translationX, 0F)
                }
                1 -> {
                    btnToggle.setImageResource(icons[2])
                    animator = ObjectAnimator.ofFloat(this, "translationX", translationX, 0F)
                }
                2 -> {
                    btnToggle.setImageResource(icons[3])
                    animator = ObjectAnimator.ofFloat(this, "translationY", translationY, 0F)
                }
                else -> {
                    btnToggle.setImageResource(icons[1])
                    animator = ObjectAnimator.ofFloat(this, "translationY", translationY, 0F)
                }
            }
            animator.interpolator = DecelerateInterpolator(3F)
        } else {
            when (direction) {
                0 -> {
                    btnToggle.setImageResource(icons[2])
                    animator = ObjectAnimator.ofFloat(this, "translationX", translationX, -expandedTrans)
                }
                1 -> {
                    btnToggle.setImageResource(icons[0])
                    animator = ObjectAnimator.ofFloat(this, "translationX", translationX, expandedTrans)
                }
                2 -> {
                    btnToggle.setImageResource(icons[1])
                    animator = ObjectAnimator.ofFloat(this, "translationY", translationY, expandedTrans)
                }
                else -> {
                    btnToggle.setImageResource(icons[3])
                    animator = ObjectAnimator.ofFloat(this, "translationY", translationY, -expandedTrans)
                }
            }
            animator.interpolator = DecelerateInterpolator(3F)
        }
        animator.duration = dur.toLong()
        animator.start()
    }

    fun lock (locked: Boolean? = null, anim: Boolean = true) {
        if (locked != null)
            this.locked = locked
        else
            this.locked = !this.locked
        notifyLockListeners(this.locked)
        if (!expanded && this.locked)
            expand(true, anim, true)
        else if (expanded && !this.locked)
            expand(false, anim, true)
        if (this.locked)
            btnToggle.setImageResource(icons[4])
    }

}