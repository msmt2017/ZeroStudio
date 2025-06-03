package de.lulebe.designer.styleEditing

import android.content.DialogInterface
import android.support.v7.app.AlertDialog
import android.support.v7.widget.LinearLayoutManager
import android.support.v7.widget.RecyclerView
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import de.lulebe.designer.R
import de.lulebe.designer.data.BoardState
import de.lulebe.designer.data.objects.BoardObject
import de.lulebe.designer.data.styles.BoxStyle
import org.jetbrains.anko.runOnUiThread


class BoxStyleManager(val mView: ViewGroup, val mBoardObject: BoardObject, val mBoardState: BoardState) {

    private val mEmptyview: View
    private val mListview: RecyclerView

    init {
        mEmptyview = mView.findViewById(R.id.tv_no_boxstyles)
        mListview = mView.findViewById<RecyclerView>(R.id.rv_boxstyles)

        mListview.layoutManager = LinearLayoutManager(mListview.context)
        mListview.adapter = BoxStyleAdapter()

        setVisibilities()

        mBoardObject.styles.addChangeListener {
            setVisibilities()
            mView.context.runOnUiThread { mListview.adapter.notifyDataSetChanged() }
        }
    }

    private fun setVisibilities () {
        if (mBoardObject.styles.boxStyles.size > 0) {
            mEmptyview.visibility = View.GONE
            mListview.visibility = View.VISIBLE
        } else {
            mEmptyview.visibility = View.VISIBLE
            mListview.visibility = View.GONE
        }
    }


    private fun openEditDialog (bs: BoxStyle) {
        val view = LayoutInflater.from(mView.context).inflate(R.layout.dialog_edit_boxstyle, null)
        view.findViewById<EditText>(R.id.field_name).setText(bs.name)
        view.findViewById<EditText>(R.id.field_width).setText(bs.width.toString())
        view.findViewById<EditText>(R.id.field_height).setText(bs.height.toString())
        view.findViewById<EditText>(R.id.field_cornerradius).setText(bs.cornerRadius.toString())
        AlertDialog.Builder(mView.context)
                .setView(view)
                .setTitle(R.string.edit_box_style)
                .setNegativeButton(android.R.string.cancel, DialogInterface.OnClickListener { dialogInterface, i ->
                    dialogInterface.cancel()
                })
                .setPositiveButton(R.string.save, DialogInterface.OnClickListener { dialogInterface, i ->
                    dialogInterface.dismiss()
                    bs.name = view.findViewById<EditText>(R.id.field_name).text.toString()
                    bs.width = view.findViewById<EditText>(R.id.field_width).text.toString().toInt()
                    bs.height = view.findViewById<EditText>(R.id.field_height).text.toString().toInt()
                    bs.cornerRadius = view.findViewById<EditText>(R.id.field_cornerradius).text.toString().toInt()
                })
                .show()
    }


    inner class BoxStyleAdapter : RecyclerView.Adapter<BoxStyleAdapter.StyleViewHolder>() {
        override fun onBindViewHolder(holder: StyleViewHolder, position: Int) {
            val bs = mBoardObject.styles.boxStyles.values.toList().get(position)
            holder.name.text = bs.name
            holder.dimensions.text = bs.width.toString() + " * " + bs.height.toString() +
                    ", " + mView.resources.getString(R.string.corner_radius) + ": " + bs.cornerRadius.toString()
            holder.view.setOnClickListener {
                if (mBoardState.selected.size == 1)
                    mBoardState.selected[0].boxStyle = bs
            }
            holder.view.setOnLongClickListener {
                openEditDialog(bs)
                true
            }
            holder.delete.setOnClickListener {
                if (!mBoardObject.styleIsUsed(bs))
                    mBoardObject.styles.removeBoxStyle(bs)
                else
                    Toast.makeText(mView.context, R.string.style_in_use, Toast.LENGTH_SHORT).show()
            }
        }

        override fun getItemCount(): Int {
            return mBoardObject.styles.boxStyles.count()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): StyleViewHolder? {
            val itemView = LayoutInflater.from(parent.context).inflate(R.layout.listitem_boxstyle, parent, false)
            return StyleViewHolder(itemView)
        }

        inner class StyleViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val view: View
            val name: TextView
            val dimensions: TextView
            val delete: View
            init {
                view = itemView
                name = itemView.findViewById<TextView>(R.id.name)
                dimensions = itemView.findViewById<TextView>(R.id.dimensions)
                delete = itemView.findViewById(R.id.btn_delete)
            }
        }
    }
}