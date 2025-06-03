package de.lulebe.designer.adapters

import android.content.Context
import android.support.v7.widget.GridLayoutManager
import android.support.v7.widget.RecyclerView
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import de.lulebe.designer.R
import de.lulebe.designer.data.FontCache
import de.lulebe.designer.data.IncludedFiles
import de.lulebe.designer.data.objects.BoardObject


class FontChooserAdapter(val ctx: Context, val mBoardObject: BoardObject, val layoutManager: GridLayoutManager) : RecyclerView.Adapter<FontChooserAdapter.ViewHolder>() {

    /*
    FIELDS
     */


    val TYPE_ADD_BTN = 1
    val TYPE_FONT_DEFAULT = 2
    val TYPE_FONT_USER = 3


    var clickListener = {fontUID: Long -> }
    var addUserFontListener = {}



    /*
    OVERRIDES
     */



    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.listitem_font, null)
        return ViewHolder(v)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        when (getItemViewType(position)) {
            TYPE_ADD_BTN -> {
                holder.txtView.text = "add Font"
                holder.fontView.text = "+"
                holder.click = addUserFontListener
            }
            TYPE_FONT_DEFAULT -> {
                holder.includedView.visibility = View.VISIBLE
                holder.txtView.text = IncludedFiles.fonts[position-1L]
                holder.click = {
                    clickListener(position-1L)
                }
                FontCache.loadFont(position-1L, mBoardObject, ctx) {
                    if (position == holder.adapterPosition)
                        holder.fontView.typeface = it
                }
            }
            TYPE_FONT_USER -> {
                val key = mBoardObject.fonts.keys.toList().get(position - 2)
                holder.txtView.text = mBoardObject.fonts[key]
                holder.click = {
                    clickListener(key)
                }
                FontCache.loadFont(key, mBoardObject, ctx) {
                    if (position == holder.adapterPosition)
                        holder.fontView.typeface = it
                }
            }
        }
    }

    override fun getItemCount(): Int {
        return mBoardObject.fonts.size + 1 + IncludedFiles.fonts.size
    }

    override fun getItemViewType(position: Int): Int {
        if (position == 0)
            return TYPE_ADD_BTN
        if (position > 0 && position <= IncludedFiles.fonts.size)
            return TYPE_FONT_DEFAULT
        return TYPE_FONT_USER
    }


    /*
    VIEWHOLDER CLASSES
     */


    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        var click = {}
        val includedView = itemView.findViewById<View>(R.id.included)
        val fontView = itemView.findViewById<TextView>(R.id.fv)
        val txtView = itemView.findViewById<TextView>(R.id.tv)
        init {
            itemView.setOnClickListener{
                click()
            }
        }
    }
}