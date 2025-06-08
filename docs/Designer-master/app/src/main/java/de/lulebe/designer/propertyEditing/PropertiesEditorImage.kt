package de.lulebe.designer.propertyEditing

import android.graphics.drawable.ColorDrawable
import android.os.AsyncTask
import android.os.Build
import android.support.v4.content.ContextCompat
import android.support.v4.graphics.drawable.DrawableCompat
import android.support.v7.app.AlertDialog
import android.support.v7.widget.GridLayoutManager
import android.support.v7.widget.RecyclerView
import android.util.TypedValue
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import com.azeesoft.lib.colorpicker.ColorPickerDialog
import de.lulebe.designer.BoardActivity
import de.lulebe.designer.R
import de.lulebe.designer.adapters.ImageChooserAdapter
import de.lulebe.designer.data.DBHelper
import de.lulebe.designer.data.ImageSource
import de.lulebe.designer.data.objects.BoardObject
import de.lulebe.designer.data.objects.ImageObject
import de.lulebe.designer.data.styles.ColorStyle
import java.io.File


class PropertiesEditorImage(val mObject: ImageObject, val mView: ViewGroup, val mBoardObject: BoardObject, val mActivity: BoardActivity) : TextView.OnEditorActionListener {

    private val mBtnchooseimageView: View
    private val mCbkeepratioView: CheckBox
    private val mCbtintView: CheckBox
    private val mTintcolorView: View
    private val mExtractTintcolorView: ImageView

    private val mColorpickerDialog = ColorPickerDialog.createColorPickerDialog(mView.context)

    private var updatingUI = false

    init {
        mBtnchooseimageView = mView.findViewById(R.id.btn_choose_image)
        mCbkeepratioView = mView.findViewById<CheckBox>(R.id.cb_keepratio)
        mCbtintView = mView.findViewById<CheckBox>(R.id.cb_tint)
        mTintcolorView = mView.findViewById(R.id.btn_object_tintcolor)
        mExtractTintcolorView = mView.findViewById<ImageView>(R.id.btn_object_extracttintcolor)

        mBtnchooseimageView.setOnClickListener {
            openImageChooserDialog()
        }

        mCbkeepratioView.setOnCheckedChangeListener { compoundButton, checked ->
            if (!updatingUI)
                mObject.keepRatio = checked
        }

        mCbtintView.setOnCheckedChangeListener { compoundButton, checked ->
            if (!updatingUI)
                mObject.tinted = checked
        }

        mColorpickerDialog.hideOpacityBar()
        mColorpickerDialog.setOnColorPickedListener { colorInt, colorString ->
            mObject.tintColor = colorInt
        }
        mTintcolorView.setOnClickListener {
            mColorpickerDialog.setLastColor(mObject.tintColor)
            mColorpickerDialog.setInitialColor(mObject.tintColor)
            mColorpickerDialog.show()
        }
        mExtractTintcolorView.setOnClickListener {
            if (mObject.tintColorStyle != null)
                mObject.tintColorStyle = null
            else {
                val cs = mObject.extractTintcolorStyle()
                val se = StyleExtractor<ColorStyle>()
                se.createStyle(cs, mView.context) {
                    mBoardObject.styles.addColorStyle(it)
                    mObject.tintColorStyle = it
                }
            }
        }
        

        mObject.addChangeListener {
            updateUI()
        }
        updateUI()
    }



    override fun onEditorAction(v: TextView?, actionId: Int, event: KeyEvent?): Boolean {
        return false
    }



    private fun updateUI () {
        updatingUI = true
        mCbkeepratioView.isChecked = mObject.keepRatio
        mCbtintView.isChecked = mObject.tinted
        mTintcolorView.background = ColorDrawable(mObject.tintColor)
        if (mObject.tintColorStyle != null) {
            var dr = DrawableCompat.wrap(ContextCompat.getDrawable(mView.context, R.drawable.ic_content_save_grey600_24dp))
            dr = dr.mutate()
            dr.setTint(ContextCompat.getColor(mView.context, R.color.colorAccent))
            mExtractTintcolorView.setImageDrawable(dr)
        } else {
            val dr = ContextCompat.getDrawable(mView.context, R.drawable.ic_content_save_grey600_24dp)
            mExtractTintcolorView.setImageDrawable(dr)
        }
        mTintcolorView.isEnabled = mObject.tinted
        mExtractTintcolorView.isEnabled = mObject.tinted
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            if (mObject.tinted)
                mTintcolorView.elevation = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 2F, mView.resources.displayMetrics)
            else
                mTintcolorView.elevation = 0F
        }
        updatingUI = false

    }

    private fun openImageChooserDialog () {
        val v = LayoutInflater.from(mView.context).inflate(R.layout.dialog_imagechooser, null)
        val rv = v.findViewById<RecyclerView>(R.id.list)
        val lm = GridLayoutManager(mView.context, 2)
        val rvAdapter = ImageChooserAdapter(mView.context, mBoardObject, lm)
        val spinner = v.findViewById<Spinner>(R.id.category)
        val spinnerAdapter = ArrayAdapter<String>(mView.context, android.R.layout.simple_spinner_item)
        spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        SpinnerLoader(spinnerAdapter).execute()
        spinner.adapter = spinnerAdapter
        spinner.onItemSelectedListener = object: AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: AdapterView<*>?) {}
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (position == 0) { //user images
                    rvAdapter.imageSource = ImageSource.USER
                    rvAdapter.imageCategory = ""
                } else {
                    val selection = imageSources[position - 1]
                    rvAdapter.imageCategory = selection.second
                    rvAdapter.imageSource = selection.first
                }
            }
        }
        val dialog = AlertDialog.Builder(mView.context)
                .setView(v)
                .setNegativeButton(android.R.string.cancel) {dialogInterface, i ->
                    dialogInterface.cancel()
                }
                .create()
        rvAdapter.clickListener = { path ->
            dialog.dismiss()
            mObject.setImage(path.first, path.second)
        }
        rvAdapter.addUserImageListener = {
            dialog.dismiss()
            mActivity.requestImage { uid: Long ->
                mObject.setImage(ImageSource.USER, uid.toString())
            }
        }
        rv.layoutManager = lm
        rv.adapter = rvAdapter
        dialog.show()
    }

    private var imageSources = mutableListOf<Pair<ImageSource, String>>()

    inner class SpinnerLoader(val adapter: ArrayAdapter<String>) : AsyncTask<Void, Void, List<Pair<ImageSource, String>>>() {
        override fun doInBackground(vararg params: Void?): List<Pair<ImageSource, String>> {
            val dbh = DBHelper(mView.context)
            val db = dbh.readableDatabase
            val c = db.rawQuery("SELECT source,dir FROM included_images GROUP BY dir ORDER BY source, dir", null)
            while (c.moveToNext()) {
                val src = ImageSource.valueOf(c.getString(c.getColumnIndex("source")))
                val dir = c.getString(c.getColumnIndex("dir"))
                imageSources.add(Pair(src, dir))
            }
            c.close()
            db.close()
            dbh.close()
            return imageSources
        }
        override fun onPostExecute(result: List<Pair<ImageSource, String>>) {
            adapter.add(mView.resources.getString(R.string.user_images))
            adapter.addAll(result.map { it.first.name + File.separator + it.second })
        }
    }

}