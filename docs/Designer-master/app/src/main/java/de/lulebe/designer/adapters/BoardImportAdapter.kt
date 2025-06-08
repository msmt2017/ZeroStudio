package de.lulebe.designer.adapters

import android.content.Context
import android.support.v7.widget.RecyclerView
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import com.squareup.picasso.Picasso
import de.lulebe.designer.R
import de.lulebe.designer.data.BoardMeta
import de.lulebe.designer.data.DBHelper
import de.lulebe.designer.data.IncludedFiles
import org.jetbrains.anko.doAsync
import org.jetbrains.anko.uiThread
import java.io.File


class BoardImportAdapter(val mContext: Context, val mBoardId: Long, val mClickListener: (bm: BoardMeta) -> Unit)
        : RecyclerView.Adapter<BoardImportAdapter.BoardViewHolder>() {

    val mBoards = mutableListOf<BoardMeta>()

    init {
        loadBoards()
    }

    private fun loadBoards () {
        doAsync {
            IncludedFiles.boards.forEach {
                val item = BoardMeta()
                item._id = it.key
                item.lastOpened = 0
                item.name = it.value
                mBoards.add(item)
            }
            val dbh = DBHelper(mContext)
            val db = dbh.readableDatabase
            val c = db.rawQuery("SELECT * FROM boards ORDER BY lastOpened DESC", null)
            while (c.moveToNext()) {
                val item = BoardMeta()
                item._id = c.getLong(c.getColumnIndex("_id"))
                item.name = c.getString(c.getColumnIndex("name"))
                item.lastOpened = c.getLong(c.getColumnIndex("lastOpened"))
                if (item._id != mBoardId)
                    mBoards.add(item)
            }
            c.close()
            db.close()
            dbh.close()
            uiThread {
                notifyDataSetChanged()
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int)
            = BoardViewHolder(LayoutInflater.from(mContext).inflate(R.layout.listitem_image, parent, false))


    override fun onBindViewHolder(holder: BoardViewHolder, position: Int) {
        val item = mBoards[position]
        holder.txtView.text = item.name
        if (item._id < 0L)
            holder.included.visibility = View.VISIBLE
        val ctx = holder.imgView.context
        Picasso.with(ctx).load(File(ctx.filesDir.path + File.separator + item._id.toString() + File.separator + "preview.png"))
                .into(holder.imgView)
    }


    override fun getItemCount() = mBoards.size



    inner class BoardViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val included = itemView.findViewById<View>(R.id.included)
        val imgView = itemView.findViewById<ImageView>(R.id.iv)
        val txtView = itemView.findViewById<TextView>(R.id.tv)
        init {
            itemView.setOnClickListener {
                mClickListener(mBoards[adapterPosition])
            }
        }
    }

}