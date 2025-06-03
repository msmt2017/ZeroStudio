package de.lulebe.designer.styleEditing

import android.support.v4.content.ContextCompat
import android.support.v4.graphics.drawable.DrawableCompat
import android.support.v7.app.AlertDialog
import android.support.v7.widget.GridLayoutManager
import android.support.v7.widget.LinearLayoutManager
import android.support.v7.widget.RecyclerView
import android.text.Layout
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import de.lulebe.designer.BoardActivity
import de.lulebe.designer.R
import de.lulebe.designer.adapters.FontChooserAdapter
import de.lulebe.designer.data.BoardState
import de.lulebe.designer.data.FontCache
import de.lulebe.designer.data.objects.BoardObject
import de.lulebe.designer.data.objects.TextObject
import de.lulebe.designer.data.styles.TextStyle
import org.jetbrains.anko.onClick
import org.jetbrains.anko.runOnUiThread


class TextStyleManager(val mView: ViewGroup, val mBoardObject: BoardObject, val mBoardState: BoardState, val mActivity: BoardActivity) {
    private val mEmptyview: View
    private val mListview: RecyclerView

    init {
        mEmptyview = mView.findViewById(R.id.tv_no_textstyles)
        mListview = mView.findViewById(R.id.rv_textstyles)

        mListview.layoutManager = LinearLayoutManager(mListview.context)
        mListview.adapter = TextStyleAdapter()

        setVisibilities()

        mBoardObject.styles.addChangeListener {
            setVisibilities()
            mView.context.runOnUiThread { mListview.adapter.notifyDataSetChanged() }
        }
    }

    private fun setVisibilities () {
        if (mBoardObject.styles.textStyles.size > 0) {
            mEmptyview.visibility = View.GONE
            mListview.visibility = View.VISIBLE
        } else {
            mEmptyview.visibility = View.VISIBLE
            mListview.visibility = View.GONE
        }
    }


    private fun openEditDialog (ts: TextStyle) {
        val view = LayoutInflater.from(mView.context).inflate(R.layout.dialog_edit_textstyle, null)
        val nameView = view.findViewById<EditText>(R.id.field_name)
        val alignleftView = view.findViewById<ImageView>(R.id.btn_alignleft)
        val aligncenterView = view.findViewById<ImageView>(R.id.btn_aligncenter)
        val alignrightView = view.findViewById<ImageView>(R.id.btn_alignright)
        val fontsizeView = view.findViewById<EditText>(R.id.field_fontsize)
        nameView.setText(ts.name)
        var alignmentTmp = ts.alignment
        setAlignmentButtons(alignmentTmp, alignleftView, aligncenterView, alignrightView)
        alignleftView.onClick {
            alignmentTmp = Layout.Alignment.ALIGN_NORMAL
            setAlignmentButtons(alignmentTmp, alignleftView, aligncenterView, alignrightView)
        }
        aligncenterView.onClick {
            alignmentTmp = Layout.Alignment.ALIGN_CENTER
            setAlignmentButtons(alignmentTmp, alignleftView, aligncenterView, alignrightView)
        }
        alignrightView.onClick {
            alignmentTmp = Layout.Alignment.ALIGN_OPPOSITE
            setAlignmentButtons(alignmentTmp, alignleftView, aligncenterView, alignrightView)
        }
        fontsizeView.setText(ts.fontSize.toString())
        AlertDialog.Builder(mView.context)
                .setTitle(R.string.edit_text_style)
                .setView(view)
                .setNegativeButton(android.R.string.cancel, { dialogInterface, i ->
                    dialogInterface.cancel()
                })
                .setPositiveButton(android.R.string.ok, { dialogInterface, i ->
                    ts.name = nameView.text.toString()
                    ts.alignment = alignmentTmp
                    try {
                        val fontSize = fontsizeView.text.toString().toInt()
                        if (fontSize >= 1 && fontSize <= 200)
                            ts.fontSize = fontSize
                    } catch (e: NumberFormatException) {}
                    dialogInterface.dismiss()
                })
                .setNeutralButton(R.string.edit_font, { dialogInterface, i ->
                    dialogInterface.dismiss()
                    openFontEditDialog(ts)
                })
                .show()
    }

    private fun setAlignmentButtons (alignment: Layout.Alignment, btnLeft: ImageView, btnCenter: ImageView, btnRight: ImageView) {
        when (alignment) {
            Layout.Alignment.ALIGN_NORMAL -> {
                setAlignmentImage(btnLeft, R.drawable.ic_format_align_left_grey600_24dp, true)
                setAlignmentImage(btnCenter, R.drawable.ic_format_align_center_grey600_24dp, false)
                setAlignmentImage(btnRight, R.drawable.ic_format_align_right_grey600_24dp, false)
            }
            Layout.Alignment.ALIGN_CENTER -> {
                setAlignmentImage(btnLeft, R.drawable.ic_format_align_left_grey600_24dp, false)
                setAlignmentImage(btnCenter, R.drawable.ic_format_align_center_grey600_24dp, true)
                setAlignmentImage(btnRight, R.drawable.ic_format_align_right_grey600_24dp, false)
            }
            Layout.Alignment.ALIGN_OPPOSITE -> {
                setAlignmentImage(btnLeft, R.drawable.ic_format_align_left_grey600_24dp, false)
                setAlignmentImage(btnCenter, R.drawable.ic_format_align_center_grey600_24dp, false)
                setAlignmentImage(btnRight, R.drawable.ic_format_align_right_grey600_24dp, true)
            }
        }
    }

    private fun setAlignmentImage (iv: ImageView, res: Int, checked: Boolean) {
        val dr = ContextCompat.getDrawable(mView.context, res)
        if (checked) {
            val d = DrawableCompat.wrap(dr).mutate()
            d.setTint(ContextCompat.getColor(mView.context, R.color.colorAccent))
            iv.setImageDrawable(d)
        } else
            iv.setImageDrawable(dr)
    }

    private fun openFontEditDialog (ts: TextStyle) {
        val rv = RecyclerView(mView.context)
        val lm = GridLayoutManager(mView.context, 2)
        val rvAdapter = FontChooserAdapter(mView.context, mBoardObject, lm)
        val dialog = AlertDialog.Builder(mView.context)
                .setView(rv)
                .setNegativeButton(android.R.string.cancel) {dialogInterface, i ->
                    dialogInterface.cancel()
                }
                .create()
        rvAdapter.clickListener = { uid ->
            dialog.dismiss()
            ts.font = uid
        }
        rvAdapter.addUserFontListener = {
            dialog.dismiss()
            mActivity.requestFont { uid: Long ->
                ts.font = uid
            }
        }
        rv.layoutManager = lm
        rv.adapter = rvAdapter
        dialog.show()
    }

    private fun applyTextStyle (ts: TextStyle) {
        if (mBoardState.selected.size == 1 && mBoardState.selected[0] is TextObject) {
            (mBoardState.selected[0] as TextObject).textStyle = ts
        }
    }


    inner class TextStyleAdapter : RecyclerView.Adapter<TextStyleAdapter.StyleViewHolder>() {
        override fun onBindViewHolder(holder: StyleViewHolder, position: Int) {
            val ts = mBoardObject.styles.textStyles.values.toList().get(position)
            holder.name.text = ts.name
            FontCache.loadFont(ts.font, mBoardObject, mView.context) {
                if (holder.adapterPosition == position)
                    holder.name.typeface = it
            }
            val alignment = when (ts.alignment) {
                Layout.Alignment.ALIGN_CENTER -> "center"
                Layout.Alignment.ALIGN_OPPOSITE -> "right"
                else -> "left"
            }
            val fontName: String
            if (ts.font == 0L || !mBoardObject.fonts.containsKey(ts.font))
                fontName = "default Font"
            else
                fontName = mBoardObject.fonts[ts.font]!!
            holder.properties.text = "${ts.fontSize.toString()}px; $alignment; $fontName"
            holder.view.setOnClickListener {
                applyTextStyle(ts)
            }
            holder.view.setOnLongClickListener {
                openEditDialog(ts)
                true
            }
            holder.delete.setOnClickListener {
                if (!mBoardObject.styleIsUsed(ts))
                    mBoardObject.styles.removeTextStyle(ts)
                else
                    Toast.makeText(mView.context, R.string.style_in_use, Toast.LENGTH_SHORT).show()
            }
        }

        override fun getItemCount(): Int {
            return mBoardObject.styles.textStyles.count()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): StyleViewHolder? {
            val itemView = LayoutInflater.from(parent.context).inflate(R.layout.listitem_textstyle, parent, false)
            return StyleViewHolder(itemView)
        }

        inner class StyleViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val view: View
            val name: TextView
            val properties: TextView
            val delete: View
            init {
                view = itemView
                name = itemView.findViewById<TextView>(R.id.name)
                properties = itemView.findViewById<TextView>(R.id.properties)
                delete = itemView.findViewById(R.id.btn_delete)
            }
        }
    }
}