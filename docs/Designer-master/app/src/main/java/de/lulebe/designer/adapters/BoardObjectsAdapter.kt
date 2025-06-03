package de.lulebe.designer.adapters

import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Handler
import android.os.Looper
import android.support.v4.content.ContextCompat
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import com.woxthebox.draglistview.DragItemAdapter
import de.lulebe.designer.R
import de.lulebe.designer.data.BoardState
import de.lulebe.designer.data.objects.BaseObject
import de.lulebe.designer.data.objects.BoardObject


class BoardObjectsAdapter(val mBoardObject: BoardObject, val mBoardState: BoardState) : DragItemAdapter<BaseObject, BoardObjectsAdapter.ObjectViewHolder>(false) {
    init {
        setHasStableIds(true)
        itemList = mBoardObject.objects
        mBoardObject.addChangeListener {
            Handler(Looper.getMainLooper()).post { notifyDataSetChanged() }
        }
        mBoardState.addListener(object: BoardState.BoardStateListener() {
            override fun onSelectChange(objs: List<BaseObject>) {
                Handler(Looper.getMainLooper()).post { notifyDataSetChanged() }
            }
        })
    }
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ObjectViewHolder {
        return ObjectViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.listitem_object, parent, false) as ViewGroup)
    }

    override fun onBindViewHolder(holder: ObjectViewHolder, position: Int) {
        super.onBindViewHolder(holder, position)
        val item = itemList[position]
        if (mBoardState.selected.contains(item))
            holder.textView.setTextColor(ContextCompat.getColor(holder.textView.context, R.color.colorAccent))
        else
            holder.textView.setTextColor(Color.WHITE)
        holder.textView.text = itemList[position].name
        holder.colorView.background = ColorDrawable(itemList[position].getMainColor())
        holder.obj = item
    }

    override fun swapItems(pos1: Int, pos2: Int) {
        if (!mBoardState.importing)
            super.swapItems(pos1, pos2)
    }

    override fun getItemId(position: Int) = itemList[position].uid

    inner class ObjectViewHolder(val view: ViewGroup) : DragItemAdapter<BaseObject, BoardObjectsAdapter.ObjectViewHolder>.ViewHolder(view, R.id.color) {
        val colorView = view.findViewById<View>(R.id.color)
        val textView = view.findViewById<TextView>(R.id.name)

        var obj: BaseObject? = null

        override fun onItemClicked(view: View) {
            mBoardState.selectedSet(obj)
        }
        override fun onItemLongClicked(view: View): Boolean {
            val clicked = obj ?: return false
            if (!mBoardState.selected.contains(clicked))
                mBoardState.selectedAdd(clicked)
            else
                mBoardState.selectedRemove(clicked)
            return true
        }
    }
}