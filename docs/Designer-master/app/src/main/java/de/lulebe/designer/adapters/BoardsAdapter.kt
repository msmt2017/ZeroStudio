package de.lulebe.designer.adapters

import android.support.v7.widget.RecyclerView
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import com.squareup.picasso.Picasso
import de.lulebe.designer.R
import de.lulebe.designer.data.BoardMeta
import de.lulebe.designer.data.BoardSaves
import org.jetbrains.anko.doAsync
import org.jetbrains.anko.uiThread
import java.io.File
import java.text.SimpleDateFormat
import java.util.*


class BoardsAdapter(val listener: (BoardMeta, longClick: Boolean) -> Unit) : RecyclerView.Adapter<BoardsAdapter.BoardViewHolder>() {

    private var mItems = mutableListOf<BoardMeta>()
    private val saveListener: (id: Long) -> Unit =  { id -> doAsync {
        mItems.forEachIndexed { i, boardMeta ->
            if (boardMeta._id == id) {
                uiThread { notifyItemChanged(i) }
                return@doAsync
            }
        }
    } }

    fun setItems (items: MutableList<BoardMeta>) {
        mItems = items
        notifyDataSetChanged()
    }

    override fun onAttachedToRecyclerView(recyclerView: RecyclerView?) {
        super.onAttachedToRecyclerView(recyclerView)
        BoardSaves.addListener(saveListener)
    }

    override fun onDetachedFromRecyclerView(recyclerView: RecyclerView?) {
        super.onDetachedFromRecyclerView(recyclerView)
        BoardSaves.removeListener(saveListener)
    }

    override fun getItemCount(): Int {
        return mItems.size
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BoardViewHolder? {
        return BoardViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.listitem_board, parent, false) as ViewGroup)
    }

    override fun onBindViewHolder(holder: BoardViewHolder, position: Int) {
        val item = mItems[position]
        holder.tvName.text = item.name
        val sdf = SimpleDateFormat("dd.MM.yyyy HH:mm:ss", Locale.getDefault())
        sdf.timeZone = Calendar.getInstance().timeZone
        holder.tvLastedited.text = sdf.format(Date(item.lastOpened))
        val ctx = holder.ivPreview.context
        val imgFile = File(ctx.filesDir.path + File.separator + item._id.toString() + File.separator + "preview.png")
        val picasso = Picasso.with(ctx)
        picasso.invalidate(imgFile)
        picasso.load(imgFile).into(holder.ivPreview)
        holder.view.setOnClickListener {
            listener(item, false)
        }
        holder.view.setOnLongClickListener {
            listener(item, true)
            true
        }
    }

    class BoardViewHolder : RecyclerView.ViewHolder {
        val tvName: TextView
        val tvLastedited: TextView
        val ivPreview: ImageView
        val view: View
        constructor(itemView: ViewGroup) : super(itemView) {
            view = itemView
            tvName = itemView.findViewById<TextView>(R.id.tv_name)
            tvLastedited = itemView.findViewById<TextView>(R.id.tv_lastedited)
            ivPreview = itemView.findViewById<ImageView>(R.id.iv_preview)
        }
    }
}