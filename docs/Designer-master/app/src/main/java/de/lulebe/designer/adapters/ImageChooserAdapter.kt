package de.lulebe.designer.adapters

import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.support.v7.widget.GridLayoutManager
import android.support.v7.widget.RecyclerView
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import com.squareup.picasso.Picasso
import de.lulebe.designer.R
import de.lulebe.designer.data.DBHelper
import de.lulebe.designer.data.ImageSource
import de.lulebe.designer.data.objects.BoardObject
import java.io.File


class ImageChooserAdapter(val ctx: Context, val mBoardObject: BoardObject, val layoutManager: GridLayoutManager) : RecyclerView.Adapter<ImageChooserAdapter.ViewHolder>() {

    /*
    FIELDS
     */


    val TYPE_ADD_BTN = 1
    val TYPE_IMAGE = 2

    var mDBHelper: DBHelper? = null
    var mDB: SQLiteDatabase? = null
    var mCursor: Cursor? = null

    private var _imageSource = ImageSource.GOOGLE
    var imageSource: ImageSource
        get() = _imageSource
        set(value) {
            _imageSource = value
            updateCursor()
        }

    private var _imageCategory = ""
    var imageCategory: String
        get() = _imageCategory
        set(value) {
            _imageCategory = value
            updateCursor()
        }

    var clickListener = {path: Pair<ImageSource, String> -> }

    var addUserImageListener = {}



    /*
    OVERRIDES
     */



    override fun onAttachedToRecyclerView(recyclerView: RecyclerView?) {
        super.onAttachedToRecyclerView(recyclerView)
        mDBHelper = DBHelper(ctx)
        mDB = mDBHelper?.readableDatabase
        updateCursor()
    }
    override fun onDetachedFromRecyclerView(recyclerView: RecyclerView?) {
        super.onDetachedFromRecyclerView(recyclerView)
        mCursor?.close()
        mCursor = null
        mDB?.close()
        mDB = null
        mDBHelper?.close()
        mDBHelper = null
    }

    override fun getItemCount(): Int {
        when (imageSource) {
            ImageSource.USER -> return mBoardObject.images.size + 1
            else -> {
                if (mCursor != null && !mCursor!!.isClosed)
                    return mCursor!!.count
                return 0
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.listitem_image, parent, false)
        return ImageViewHolder(v)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val v = (holder as ImageViewHolder)
        if (getItemViewType(position) == TYPE_ADD_BTN) {
            v.txtView.text = "add Image"
            Picasso.with(v.imgView.context).load(R.drawable.ic_plus_grey600_48dp).into(v.imgView)
            v.click = addUserImageListener
            return
        }
        if (imageSource == ImageSource.USER) {
            val key = mBoardObject.images.keys.toList().get(position - 1)
            v.txtView.text = mBoardObject.images[key]
            val path = mBoardObject.getImagePath(key)
            Picasso.with(v.imgView.context).load(File(path)).resize(250, 150).onlyScaleDown().centerInside().into(v.imgView)
            v.click = {
                clickListener(Pair(ImageSource.USER, key.toString()))
            }
        } else if (mCursor != null && !mCursor!!.isClosed) {
            mCursor!!.moveToPosition(position)
            val name = mCursor!!.getString(mCursor!!.getColumnIndex("file"))
            val assetPath = Pair(ImageSource.valueOf(mCursor!!.getString(mCursor!!.getColumnIndex("source"))),
                    mCursor!!.getString(mCursor!!.getColumnIndex("dir")) + File.separator + name)
            val uri = "file:///android_asset" + File.separator + assetPath.first + File.separator + assetPath.second
            v.txtView.text = name
            Picasso.with(v.imgView.context).load(uri).into(v.imgView)
            v.click = {
                clickListener(assetPath)
            }
        }
    }

    override fun getItemViewType(position: Int): Int {
        if (position == 0 && imageSource == ImageSource.USER)
            return TYPE_ADD_BTN
        return TYPE_IMAGE
    }


    /*
    CUSTOM METHODS
     */



    private fun updateCursor () {
        if (mDB != null) {
            mCursor?.close()
            val category: String
            if (_imageCategory != "")
                category = " AND dir='$_imageCategory'"
            else
                category = ""
            mCursor = mDB!!.rawQuery("SELECT * FROM included_images WHERE source='" + imageSource.name + "'" + category, null)
        }
        notifyDataSetChanged()
    }


    /*
    VIEWHOLDER CLASSES
     */

    inner class ImageViewHolder(itemView: View) : ViewHolder(itemView) {
        var click = {}
        val imgView = itemView.findViewById<ImageView>(R.id.iv)
        val txtView = itemView.findViewById<TextView>(R.id.tv)
        init {
            itemView.setOnClickListener {
                click()
            }
        }
    }

    open inner class ViewHolder (itemView: View) : RecyclerView.ViewHolder(itemView) {

    }
}