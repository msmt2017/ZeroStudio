package de.lulebe.designer.propertyEditing

import android.support.v4.content.ContextCompat
import android.support.v4.graphics.drawable.DrawableCompat
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import de.lulebe.designer.CheatSheet
import de.lulebe.designer.R
import de.lulebe.designer.data.BoardState
import de.lulebe.designer.data.objects.BaseObject
import de.lulebe.designer.data.objects.BoardObject
import de.lulebe.designer.data.objects.CopyObject
import de.lulebe.designer.data.objects.SourceObject
import de.lulebe.designer.data.styles.BoxStyle


class PropertiesEditorObject(val mObject: SourceObject, val mView: ViewGroup, val mBoardObject: BoardObject, val mBoardState: BoardState)
        : TextView.OnEditorActionListener, View.OnKeyListener, View.OnClickListener {


    private val mNameView: EditText
    private val mDeleteView: View
    private val mDuplicateView: View
    private val mXPosView: EditText
    private val mYPosView: EditText
    private val mAlignhorizleftView: ImageView
    private val mAlignhorizcenterView: ImageView
    private val mAlignhorizrightView: ImageView
    private val mAlignverttopView: ImageView
    private val mAlignvertcenterView: ImageView
    private val mAlignvertbottomView: ImageView
    private val mWidthLayoutView: View
    private val mWidthView: EditText
    private val mWidthDisplayLayoutView: View
    private val mWidthDisplayView: TextView
    private val mHeightLayoutView: View
    private val mHeightView: EditText
    private val mHeightDisplayLayoutView: View
    private val mHeightDisplayView: TextView
    private val mFillparenthorizontalView: ImageView
    private val mFillparentverticalView: ImageView
    private val mExtractBoxstyleView: ImageView
    private val mRotationView: EditText
    private val mRotationhandleinfoView: View
    private val mAlphaView: EditText

    init {
        mNameView = mView.findViewById<EditText>(R.id.field_object_name)
        mDeleteView = mView.findViewById(R.id.btn_object_delete)
        mDuplicateView = mView.findViewById(R.id.btn_object_duplicate)
        mXPosView = mView.findViewById<EditText>(R.id.field_object_xpos)
        mYPosView = mView.findViewById<EditText>(R.id.field_object_ypos)
        mAlignhorizleftView = mView.findViewById<ImageView>(R.id.btn_object_alignleft)
        mAlignhorizcenterView = mView.findViewById<ImageView>(R.id.btn_object_alignhorizcenter)
        mAlignhorizrightView = mView.findViewById<ImageView>(R.id.btn_object_alignright)
        mAlignverttopView = mView.findViewById<ImageView>(R.id.btn_object_aligntop)
        mAlignvertcenterView = mView.findViewById<ImageView>(R.id.btn_object_alignvertcenter)
        mAlignvertbottomView = mView.findViewById<ImageView>(R.id.btn_object_alignbottom)
        mWidthLayoutView = mView.findViewById(R.id.field_object_width_layout)
        mWidthView = mView.findViewById<EditText>(R.id.field_object_width)
        mWidthDisplayLayoutView = mView.findViewById(R.id.display_object_width_layout)
        mWidthDisplayView = mView.findViewById<TextView>(R.id.display_object_width)
        mHeightLayoutView = mView.findViewById(R.id.field_object_height_layout)
        mHeightView = mView.findViewById<EditText>(R.id.field_object_height)
        mHeightDisplayLayoutView = mView.findViewById(R.id.display_object_height_layout)
        mHeightDisplayView = mView.findViewById<TextView>(R.id.display_object_height)
        mFillparenthorizontalView = mView.findViewById<ImageView>(R.id.btn_object_fillparent_horizontal)
        mFillparentverticalView = mView.findViewById<ImageView>(R.id.btn_object_fillparent_vertical)
        mExtractBoxstyleView = mView.findViewById<ImageView>(R.id.btn_object_extractboxstyle)
        mRotationView = mView.findViewById<EditText>(R.id.field_object_rotation)
        mRotationhandleinfoView = mView.findViewById(R.id.info_object_rotation_handles)
        mAlphaView = mView.findViewById<EditText>(R.id.field_object_alpha)

        initCheatSheets()

        mNameView.setOnEditorActionListener(this)
        mNameView.setOnKeyListener(this)
        mXPosView.setOnEditorActionListener(this)
        mXPosView.setOnKeyListener(this)
        mYPosView.setOnEditorActionListener(this)
        mYPosView.setOnKeyListener(this)
        mWidthView.setOnEditorActionListener(this)
        mWidthView.setOnKeyListener(this)
        mHeightView.setOnEditorActionListener(this)
        mHeightView.setOnKeyListener(this)
        mRotationView.setOnEditorActionListener(this)
        mRotationView.setOnKeyListener(this)
        mAlphaView.setOnEditorActionListener(this)
        mAlphaView.setOnKeyListener(this)


        mDeleteView.setOnClickListener(this)
        mDuplicateView.setOnClickListener(this)
        mAlignhorizleftView.setOnClickListener(this)
        mAlignhorizcenterView.setOnClickListener(this)
        mAlignhorizrightView.setOnClickListener(this)
        mAlignverttopView.setOnClickListener(this)
        mAlignvertcenterView.setOnClickListener(this)
        mAlignvertbottomView.setOnClickListener(this)
        mFillparenthorizontalView.setOnClickListener(this)
        mFillparentverticalView.setOnClickListener(this)
        mExtractBoxstyleView.setOnClickListener(this)

        mObject.addChangeListener {
            updateUI()
        }
        updateUI()
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

    private fun processEditText (v: EditText) : Boolean {
        when (v) {
            mNameView -> {
                mObject.name = mNameView.text.toString()
                return true
            }
            mXPosView -> {
                val value: Int
                try {
                    value = mXPosView.text.toString().toInt()
                } catch (e: NumberFormatException) {
                    return false
                }
                if (value < 0) return false
                mObject.xpos = value
                return true
            }
            mYPosView -> {
                val value: Int
                try {
                    value = mYPosView.text.toString().toInt()
                } catch (e: NumberFormatException) {
                    return false
                }
                if (value < 0) return false
                mObject.ypos = value
                return true
            }
            mWidthView -> {
                val value: Int
                try {
                    value = mWidthView.text.toString().toInt()
                } catch (e: NumberFormatException) {
                    return false
                }
                if (value < 0) return false
                if (!mObject.canDirectlyChangeWidth()) return false
                mObject.width = value
                return true
            }
            mHeightView -> {
                val value: Int
                try {
                    value = mHeightView.text.toString().toInt()
                } catch (e: NumberFormatException) {
                    return false
                }
                if (value < 0) return false
                if (!mObject.canDirectlyChangeHeight()) return false
                mObject.height = value
                return true
            }
            mRotationView -> {
                val value: Float
                try {
                    value = mRotationView.text.toString().toFloat()
                } catch (e: NumberFormatException) {
                    return false
                }
                if (value < 0 || value >= 360) return false
                mObject.rotation = value
                return true
            }
            mAlphaView -> {
                val value: Int
                try {
                    value = mAlphaView.text.toString().toInt()
                } catch (e: NumberFormatException) {
                    return false
                }
                if (value < 0 || value > 255) return false
                mObject.alpha = value
                return true
            }
            else -> return false
        }
    }


    override fun onClick(view: View?) {
        when (view) {
            mDeleteView -> {
                try {
                    mBoardObject.removeObject(mObject)
                } catch (e: BoardObject.CannotDeleteCopiedObjectException) {
                    Toast.makeText(mView.context, R.string.cant_delete_copied_object, Toast.LENGTH_SHORT).show()
                }
                mBoardState.selectedClear()
            }
            mDuplicateView -> {
                val newObj = CopyObject()
                newObj.sourceId = mObject.uid
                newObj.init(mView.context, mBoardObject)
                newObj.xpos = mObject.xpos + 10
                newObj.ypos = mObject.ypos + 10
                newObj.xposOrigin = mObject.xposOrigin
                newObj.yposOrigin = mObject.yposOrigin
                newObj.name = mView.resources.getString(R.string.copy_of) + " " + mObject.name
                mBoardObject.addObject(newObj)
                mBoardState.selectedSet(newObj)
            }
            mAlignhorizleftView -> {
                mObject.xposOrigin = BaseObject.HorizontalOrigin.LEFT
            }
            mAlignhorizcenterView -> {
                mObject.xposOrigin = BaseObject.HorizontalOrigin.CENTER
            }
            mAlignhorizrightView -> {
                mObject.xposOrigin = BaseObject.HorizontalOrigin.RIGHT
            }
            mAlignverttopView -> {
                mObject.yposOrigin = BaseObject.VerticalOrigin.TOP
            }
            mAlignvertcenterView -> {
                mObject.yposOrigin = BaseObject.VerticalOrigin.CENTER
            }
            mAlignvertbottomView -> {
                mObject.yposOrigin = BaseObject.VerticalOrigin.BOTTOM
            }
            mFillparenthorizontalView -> {
                mObject.lockToParentWidth = !mObject.lockToParentWidth
            }
            mFillparentverticalView -> {
                mObject.lockToParentHeight = !mObject.lockToParentHeight
            }
            mExtractBoxstyleView -> {
                if (mObject.boxStyle != null)
                    mObject.boxStyle = null
                else {
                    val bs = mObject.extractBoxStyle()
                    StyleExtractor<BoxStyle>().createStyle(bs, mView.context) {
                        mBoardObject.styles.addBoxStyle(bs)
                        mObject.boxStyle = bs
                    }
                }
            }
        }
    }



    private fun updateUI () {
        mNameView.setText(mObject.name)
        mXPosView.setText(mObject.xpos.toString())
        mYPosView.setText(mObject.ypos.toString())
        setAlignmentImages()
        if (mObject.canDirectlyChangeWidth() && !mObject.lockToParentWidth) {
            mWidthLayoutView.visibility = View.VISIBLE
            mWidthDisplayLayoutView.visibility = View.GONE
            mWidthView.setText(mObject.width.toString())
        } else {
            mWidthLayoutView.visibility = View.GONE
            mWidthDisplayLayoutView.visibility = View.VISIBLE
            mWidthDisplayView.text = mObject.width.toString()
        }
        if (mObject.canDirectlyChangeHeight() && !mObject.lockToParentHeight) {
            mHeightLayoutView.visibility = View.VISIBLE
            mHeightDisplayLayoutView.visibility = View.GONE
            mHeightView.setText(mObject.height.toString())
        } else {
            mHeightLayoutView.visibility = View.GONE
            mHeightDisplayLayoutView.visibility = View.VISIBLE
            mHeightDisplayView.text = mObject.height.toString()
        }
        if (mObject.canDirectlyChangeWidth()) {
            mFillparenthorizontalView.visibility = View.VISIBLE
            setFillparentImages()
        } else
            mFillparenthorizontalView.visibility = View.GONE
        if (mObject.canDirectlyChangeHeight()) {
            mFillparentverticalView.visibility = View.VISIBLE
            setFillparentImages()
        } else
            mFillparentverticalView.visibility = View.GONE
        if (mObject.canAcceptBoxStyle()) {
            mExtractBoxstyleView.visibility = View.VISIBLE
            if (mObject.boxStyle != null) {
                var dr = DrawableCompat.wrap(ContextCompat.getDrawable(mView.context, R.drawable.ic_content_save_grey600_24dp))
                dr = dr.mutate()
                dr.setTint(ContextCompat.getColor(mView.context, R.color.colorAccent))
                mExtractBoxstyleView.setImageDrawable(dr)
            } else {
                val dr = ContextCompat.getDrawable(mView.context, R.drawable.ic_content_save_grey600_24dp)
                mExtractBoxstyleView.setImageDrawable(dr)
            }
        } else
            mExtractBoxstyleView.visibility = View.GONE
        mRotationView.setText(mObject.rotation.toString())
        if (mObject.rotation == 0F)
            mRotationhandleinfoView.visibility = View.GONE
        else
            mRotationhandleinfoView.visibility = View.VISIBLE
        mAlphaView.setText(mObject.alpha.toString())
    }

    private fun setFillparentImages () {
        if (mObject.lockToParentWidth)
            setToggleImage(mFillparenthorizontalView, R.drawable.ic_fill_parent_horizontal_grey600_24dp, true)
        else
            setToggleImage(mFillparenthorizontalView, R.drawable.ic_fill_parent_horizontal_grey600_24dp, false)
        if (mObject.lockToParentHeight)
            setToggleImage(mFillparentverticalView, R.drawable.ic_fill_parent_vertical_grey600_24dp, true)
        else
            setToggleImage(mFillparentverticalView, R.drawable.ic_fill_parent_vertical_grey600_24dp, false)
    }

    private fun setAlignmentImages () {
        when (mObject.xposOrigin) {
            BaseObject.HorizontalOrigin.LEFT -> {
                setToggleImage(mAlignhorizleftView, R.drawable.ic_format_horizontal_align_left_grey600_24dp, true)
                setToggleImage(mAlignhorizcenterView, R.drawable.ic_format_horizontal_align_center_grey600_24dp, false)
                setToggleImage(mAlignhorizrightView, R.drawable.ic_format_horizontal_align_right_grey600_24dp, false)
            }
            BaseObject.HorizontalOrigin.CENTER -> {
                setToggleImage(mAlignhorizleftView, R.drawable.ic_format_horizontal_align_left_grey600_24dp, false)
                setToggleImage(mAlignhorizcenterView, R.drawable.ic_format_horizontal_align_center_grey600_24dp, true)
                setToggleImage(mAlignhorizrightView, R.drawable.ic_format_horizontal_align_right_grey600_24dp, false)
            }
            BaseObject.HorizontalOrigin.RIGHT -> {
                setToggleImage(mAlignhorizleftView, R.drawable.ic_format_horizontal_align_left_grey600_24dp, false)
                setToggleImage(mAlignhorizcenterView, R.drawable.ic_format_horizontal_align_center_grey600_24dp, false)
                setToggleImage(mAlignhorizrightView, R.drawable.ic_format_horizontal_align_right_grey600_24dp, true)
            }
        }
        when (mObject.yposOrigin) {
            BaseObject.VerticalOrigin.TOP -> {
                setToggleImage(mAlignverttopView, R.drawable.ic_format_vertical_align_top_grey600_24dp, true)
                setToggleImage(mAlignvertcenterView, R.drawable.ic_format_vertical_align_center_grey600_24dp, false)
                setToggleImage(mAlignvertbottomView, R.drawable.ic_format_vertical_align_bottom_grey600_24dp, false)
            }
            BaseObject.VerticalOrigin.CENTER -> {
                setToggleImage(mAlignverttopView, R.drawable.ic_format_vertical_align_top_grey600_24dp, false)
                setToggleImage(mAlignvertcenterView, R.drawable.ic_format_vertical_align_center_grey600_24dp, true)
                setToggleImage(mAlignvertbottomView, R.drawable.ic_format_vertical_align_bottom_grey600_24dp, false)
            }
            BaseObject.VerticalOrigin.BOTTOM -> {
                setToggleImage(mAlignverttopView, R.drawable.ic_format_vertical_align_top_grey600_24dp, false)
                setToggleImage(mAlignvertcenterView, R.drawable.ic_format_vertical_align_center_grey600_24dp, false)
                setToggleImage(mAlignvertbottomView, R.drawable.ic_format_vertical_align_bottom_grey600_24dp, true)
            }
        }
    }


    private fun setToggleImage(iv: ImageView, res: Int, checked: Boolean) {
        val dr = ContextCompat.getDrawable(mView.context, res)
        if (checked) {
            val d = DrawableCompat.wrap(dr).mutate()
            d.setTint(ContextCompat.getColor(mView.context, R.color.colorAccent))
            iv.setImageDrawable(d)
        } else
            iv.setImageDrawable(dr)
    }


    private fun initCheatSheets () {
        CheatSheet.setup(mDeleteView)
        CheatSheet.setup(mDuplicateView)
    }

}