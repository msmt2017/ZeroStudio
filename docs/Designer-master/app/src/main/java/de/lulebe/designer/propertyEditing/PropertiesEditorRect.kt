package de.lulebe.designer.propertyEditing

import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.support.v4.content.ContextCompat
import android.support.v4.graphics.drawable.DrawableCompat
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.*
import com.azeesoft.lib.colorpicker.ColorPickerDialog
import de.lulebe.designer.R
import de.lulebe.designer.data.objects.BoardObject
import de.lulebe.designer.data.objects.RectObject
import de.lulebe.designer.data.styles.ColorStyle


class PropertiesEditorRect(val mObject: RectObject, val mView: ViewGroup, val mBoardObject: BoardObject) : View.OnClickListener, TextView.OnEditorActionListener, View.OnKeyListener {

    private val mFilliconView: ImageView
    private val mToggleFillView: Switch
    private val mFillcolorLayout: View
    private val mFillcolorView: View
    private val mExtractFillcolorView: ImageView
    private val mGradientLayout: View
    private val mGradienthorizontalView: ImageView
    private val mGradientverticalView: ImageView
    private val mGradientcircleView: ImageView
    private val mGradientstartcolorView: View
    private val mExtractGradientstartcolorView: ImageView
    private val mGradientendcolorView: View
    private val mExtractGradientendcolorView: ImageView
    private val mStrokewidthView: EditText
    private val mStrokecolorView: View
    private val mExtractStrokecolorView: ImageView
    private val mCornerradiusView: EditText
    private val mShadowView: CheckBox
    private val mShadowBlurView: EditText
    private val mShadowXPosView: EditText
    private val mShadowYPosView: EditText

    private val mColorpickerDialog = ColorPickerDialog.createColorPickerDialog(mView.context)
    private var currentEditingColor = 0 //fill, stroke, gradientStart, gradientEnd

    init {
        mFilliconView = mView.findViewById<ImageView>(R.id.icon_object_fill)
        mToggleFillView = mView.findViewById<Switch>(R.id.toggle_object_fill)
        mFillcolorLayout = mView.findViewById(R.id.layout_object_fill_solid)
        mFillcolorView = mView.findViewById(R.id.btn_object_fillcolor)
        mExtractFillcolorView = mView.findViewById<ImageView>(R.id.btn_object_extractfillcolor)
        mGradientLayout = mView.findViewById(R.id.layout_object_fill_gradient)
        mGradienthorizontalView = mView.findViewById<ImageView>(R.id.btn_object_gradient_horizontal)
        mGradientverticalView = mView.findViewById<ImageView>(R.id.btn_object_gradient_vertical)
        mGradientcircleView = mView.findViewById<ImageView>(R.id.btn_object_gradient_circle)
        mGradientstartcolorView = mView.findViewById(R.id.btn_object_gradient_startcolor)
        mExtractGradientstartcolorView = mView.findViewById<ImageView>(R.id.btn_object_extractgradientstartcolor)
        mGradientendcolorView = mView.findViewById(R.id.btn_object_gradient_endcolor)
        mExtractGradientendcolorView = mView.findViewById<ImageView>(R.id.btn_object_extractgradientendcolor)
        mStrokewidthView = mView.findViewById<EditText>(R.id.field_object_strokewidth)
        mStrokecolorView = mView.findViewById(R.id.btn_object_strokecolor)
        mExtractStrokecolorView = mView.findViewById<ImageView>(R.id.btn_object_extractstrokecolor)
        mCornerradiusView = mView.findViewById<EditText>(R.id.field_object_cornerradius)
        mShadowView = mView.findViewById<CheckBox>(R.id.field_object_shadow)
        mShadowBlurView = mView.findViewById<EditText>(R.id.field_object_shadowblur)
        mShadowXPosView = mView.findViewById<EditText>(R.id.field_object_shadowx)
        mShadowYPosView = mView.findViewById<EditText>(R.id.field_object_shadowy)


        mColorpickerDialog.setOnColorPickedListener { colorInt, colorString ->
            try {
                Color.alpha(colorInt)
                Color.red(colorInt)
                Color.green(colorInt)
                Color.blue(colorInt)
                when (currentEditingColor) {
                    0 -> mObject.fillColor = colorInt
                    1 -> mObject.strokeColor = colorInt
                    2 -> mObject.gradient?.startColor = colorInt
                    3 -> mObject.gradient?.endColor = colorInt
                }
            } catch (e: IllegalArgumentException) {}
        }
        
        mToggleFillView.setOnCheckedChangeListener { compoundButton, checked -> 
            if (checked != (mObject.gradient != null)) {
                if (checked)
                    mObject.gradient = RectObject.Gradient()
                else
                    mObject.gradient = null
            }
        }
        
        mFillcolorView.setOnClickListener(this)
        mExtractFillcolorView.setOnClickListener(this)
        mGradienthorizontalView.setOnClickListener(this)
        mGradientverticalView.setOnClickListener(this)
        mGradientcircleView.setOnClickListener(this)
        mGradientstartcolorView.setOnClickListener(this)
        mExtractGradientstartcolorView.setOnClickListener(this)
        mGradientendcolorView.setOnClickListener(this)
        mExtractGradientendcolorView.setOnClickListener(this)
        mStrokecolorView.setOnClickListener(this)
        mExtractStrokecolorView.setOnClickListener(this)

        mStrokewidthView.setOnEditorActionListener(this)
        mStrokewidthView.setOnKeyListener(this)
        mCornerradiusView.setOnEditorActionListener(this)
        mCornerradiusView.setOnKeyListener(this)



        mShadowBlurView.setOnEditorActionListener(this)
        mShadowBlurView.setOnKeyListener(this)
        mShadowXPosView.setOnEditorActionListener(this)
        mShadowXPosView.setOnKeyListener(this)
        mShadowYPosView.setOnEditorActionListener(this)
        mShadowYPosView.setOnKeyListener(this)
        mShadowView.setOnCheckedChangeListener { btn, shadow ->
            if (shadow != (mObject.shadow != null)) {
                if (shadow)
                    mObject.shadow = RectObject.Shadow()
                else
                    mObject.shadow = null
            }
        }

        mObject.addChangeListener {
            updateUI()
        }
        updateUI()
    }


    override fun onClick(view: View) {
        when (view) {
            mFillcolorView -> {
                currentEditingColor = 0
                mColorpickerDialog.setLastColor(mObject.fillColor)
                mColorpickerDialog.setInitialColor(mObject.fillColor)
                mColorpickerDialog.show()
            }
            mExtractFillcolorView -> {
                if (mObject.fillColorStyle != null)
                    mObject.fillColorStyle = null
                else {
                    val cs = mObject.extractFillcolorStyle()
                    val se = StyleExtractor<ColorStyle>()
                    se.createStyle(cs, mView.context) {
                        mBoardObject.styles.addColorStyle(it)
                        mObject.fillColorStyle = it
                    }
                }
            }
            mGradienthorizontalView -> mObject.gradient?.direction = RectObject.Gradient.Direction.HORIZONTAL
            mGradientverticalView -> mObject.gradient?.direction = RectObject.Gradient.Direction.VERTICAL
            mGradientcircleView -> mObject.gradient?.direction = RectObject.Gradient.Direction.CIRCLE
            mGradientstartcolorView -> {
                if (mObject.gradient != null) {
                    currentEditingColor = 2
                    mColorpickerDialog.setLastColor(mObject.gradient!!.startColor)
                    mColorpickerDialog.setInitialColor(mObject.gradient!!.startColor)
                    mColorpickerDialog.show()
                }
            }
            mExtractGradientstartcolorView -> {
                if (mObject.gradient?.startColorStyle != null)
                    mObject.gradient?.startColorStyle = null
                else if (mObject.gradient != null) {
                    val cs = mObject.gradient!!.extractStartcolorStyle()
                    val se = StyleExtractor<ColorStyle>()
                    se.createStyle(cs, mView.context) {
                        mBoardObject.styles.addColorStyle(it)
                        mObject.gradient?.startColorStyle = it
                    }
                }
            }
            mGradientendcolorView -> {
                if (mObject.gradient != null) {
                    currentEditingColor = 3
                    mColorpickerDialog.setLastColor(mObject.gradient!!.endColor)
                    mColorpickerDialog.setInitialColor(mObject.gradient!!.endColor)
                    mColorpickerDialog.show()
                }
            }
            mExtractGradientendcolorView -> {
                if (mObject.gradient?.endColorStyle != null)
                    mObject.gradient?.endColorStyle = null
                else if (mObject.gradient != null) {
                    val cs = mObject.gradient!!.extractEndcolorStyle()
                    val se = StyleExtractor<ColorStyle>()
                    se.createStyle(cs, mView.context) {
                        mBoardObject.styles.addColorStyle(it)
                        mObject.gradient?.endColorStyle = it
                    }
                }
            }
            mStrokecolorView -> {
                currentEditingColor = 1
                mColorpickerDialog.setLastColor(mObject.strokeColor)
                mColorpickerDialog.setInitialColor(mObject.strokeColor)
                mColorpickerDialog.show()
            }
            mExtractStrokecolorView -> {
                if (mObject.strokeColorStyle != null)
                    mObject.strokeColorStyle = null
                else {
                    val cs = mObject.extractStrokecolorStyle()
                    val se = StyleExtractor<ColorStyle>()
                    se.createStyle(cs, mView.context) {
                        mBoardObject.styles.addColorStyle(it)
                        mObject.strokeColorStyle = it
                    }
                }
            }
        }
    }


    override fun onKey(view: View, keyCode: Int, keyEvent: KeyEvent): Boolean {
        if (keyEvent.keyCode == KeyEvent.KEYCODE_ENTER || keyEvent.keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER)
            return processEditText(view as EditText)
        return false
    }


    override fun onEditorAction(v: TextView, actionId: Int, event: KeyEvent?): Boolean {
        if (actionId != EditorInfo.IME_ACTION_DONE) return false
        return processEditText(v as EditText)
    }


    private fun processEditText(v: EditText): Boolean {
        when (v) {
            mStrokewidthView -> {
                val value: Int
                try {
                    value = mStrokewidthView.text.toString().toInt()
                } catch (e: NumberFormatException) {
                    return false
                }
                if (value < 0 || value > 99) return false
                mObject.strokeWidth = value
                return true
            }
            mCornerradiusView -> {
                val value: Int
                try {
                    value = mCornerradiusView.text.toString().toInt()
                } catch (e: NumberFormatException) {
                    return false
                }
                if (value < 0) return false
                mObject.cornerRadius = value
                return true
            }
            mShadowBlurView -> {
                val value: Int
                try {
                    value = mShadowBlurView.text.toString().toInt()
                } catch (e: NumberFormatException) {
                    return false
                }
                if (value < 0) return false
                mObject.shadow?.blur = value
                return true
            }
            mShadowXPosView -> {
                val value: Int
                try {
                    value = mShadowXPosView.text.toString().toInt()
                } catch (e: NumberFormatException) {
                    return false
                }
                if (value < 0) return false
                mObject.shadow?.xpos = value
                return true
            }
            mShadowYPosView -> {
                val value: Int
                try {
                    value = mShadowYPosView.text.toString().toInt()
                } catch (e: NumberFormatException) {
                    return false
                }
                if (value < 0) return false
                mObject.shadow?.ypos = value
                return true
            }
            else -> return false
        }
    }


    private fun updateUI () {
        mToggleFillView.isChecked = mObject.gradient != null
        if (mObject.gradient != null) {
            mFillcolorLayout.visibility = View.GONE
            mGradientLayout.visibility = View.VISIBLE
            val gradient = mObject.gradient!!
            updateGradientDirectionViews(gradient.direction)
            mGradientstartcolorView.background = ColorDrawable(gradient.startColor)
            mGradientendcolorView.background = ColorDrawable(gradient.endColor)
            if (gradient.startColorStyle != null) {
                var dr = DrawableCompat.wrap(ContextCompat.getDrawable(mView.context, R.drawable.ic_content_save_grey600_24dp))
                dr = dr.mutate()
                dr.setTint(ContextCompat.getColor(mView.context, R.color.colorAccent))
                mExtractGradientstartcolorView.setImageDrawable(dr)
            } else {
                val dr = ContextCompat.getDrawable(mView.context, R.drawable.ic_content_save_grey600_24dp)
                mExtractGradientstartcolorView.setImageDrawable(dr)
            }
            if (gradient.endColorStyle != null) {
                var dr = DrawableCompat.wrap(ContextCompat.getDrawable(mView.context, R.drawable.ic_content_save_grey600_24dp))
                dr = dr.mutate()
                dr.setTint(ContextCompat.getColor(mView.context, R.color.colorAccent))
                mExtractGradientendcolorView.setImageDrawable(dr)
            } else {
                val dr = ContextCompat.getDrawable(mView.context, R.drawable.ic_content_save_grey600_24dp)
                mExtractGradientendcolorView.setImageDrawable(dr)
            }
        } else {
            mFillcolorLayout.visibility = View.VISIBLE
            mGradientLayout.visibility = View.GONE
            mFillcolorView.background = ColorDrawable(mObject.fillColor)
            if (mObject.fillColorStyle != null) {
                var dr = DrawableCompat.wrap(ContextCompat.getDrawable(mView.context, R.drawable.ic_content_save_grey600_24dp))
                dr = dr.mutate()
                dr.setTint(ContextCompat.getColor(mView.context, R.color.colorAccent))
                mExtractFillcolorView.setImageDrawable(dr)
            } else {
                val dr = ContextCompat.getDrawable(mView.context, R.drawable.ic_content_save_grey600_24dp)
                mExtractFillcolorView.setImageDrawable(dr)
            }
        }

        mStrokewidthView.setText(mObject.strokeWidth.toString())
        mStrokecolorView.background = ColorDrawable(mObject.strokeColor)
        mCornerradiusView.setText(mObject.cornerRadius.toString())
        if (mObject.strokeColorStyle != null) {
            var dr = DrawableCompat.wrap(ContextCompat.getDrawable(mView.context, R.drawable.ic_content_save_grey600_24dp))
            dr = dr.mutate()
            dr.setTint(ContextCompat.getColor(mView.context, R.color.colorAccent))
            mExtractStrokecolorView.setImageDrawable(dr)
        } else {
            val dr = ContextCompat.getDrawable(mView.context, R.drawable.ic_content_save_grey600_24dp)
            mExtractStrokecolorView.setImageDrawable(dr)
        }
        
        if (mObject.shadow != null) {
            mShadowView.isChecked = true
            mShadowXPosView.isEnabled = true
            mShadowYPosView.isEnabled = true
            mShadowBlurView.isEnabled = true
            mShadowXPosView.setText(mObject.shadow?.xpos.toString())
            mShadowYPosView.setText(mObject.shadow?.ypos.toString())
            mShadowBlurView.setText(mObject.shadow?.blur.toString())
        } else {
            mShadowView.isChecked = false
            mShadowXPosView.isEnabled = false
            mShadowYPosView.isEnabled = false
            mShadowBlurView.isEnabled = false
            mShadowXPosView.setText("")
            mShadowYPosView.setText("")
            mShadowBlurView.setText("")
        }
    }

    private fun updateGradientDirectionViews (dir: RectObject.Gradient.Direction) {
        when (dir) {
            RectObject.Gradient.Direction.HORIZONTAL -> {
                setDirectionImage(mGradienthorizontalView, R.drawable.ic_panorama_horizontal_grey600_24dp, true)
                setDirectionImage(mGradientverticalView, R.drawable.ic_panorama_vertical_grey600_24dp, false)
                setDirectionImage(mGradientcircleView, R.drawable.ic_panorama_fisheye_grey600_24dp, false)
            }
            RectObject.Gradient.Direction.VERTICAL -> {
                setDirectionImage(mGradienthorizontalView, R.drawable.ic_panorama_horizontal_grey600_24dp, false)
                setDirectionImage(mGradientverticalView, R.drawable.ic_panorama_vertical_grey600_24dp, true)
                setDirectionImage(mGradientcircleView, R.drawable.ic_panorama_fisheye_grey600_24dp, false)
            }
            RectObject.Gradient.Direction.CIRCLE -> {
                setDirectionImage(mGradienthorizontalView, R.drawable.ic_panorama_horizontal_grey600_24dp, false)
                setDirectionImage(mGradientverticalView, R.drawable.ic_panorama_vertical_grey600_24dp, false)
                setDirectionImage(mGradientcircleView, R.drawable.ic_panorama_fisheye_grey600_24dp, true)
            }
        }
    }

    private fun setDirectionImage (iv: ImageView, res: Int, checked: Boolean) {
        val dr = ContextCompat.getDrawable(mView.context, res)
        if (checked) {
            val d = DrawableCompat.wrap(dr).mutate()
            d.setTint(ContextCompat.getColor(mView.context, R.color.colorAccent))
            iv.setImageDrawable(d)
        } else
            iv.setImageDrawable(dr)
    }

}