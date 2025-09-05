package com.itsaky.androidide.actions.menu.codeoutline

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.Filter
import android.widget.Filterable
import android.widget.ImageView
import android.widget.TextView
import com.itsaky.androidide.R
import com.itsaky.androidide.actions.menu.codeoutline.CdSymb
import com.itsaky.androidide.actions.menu.codeoutline.SymbolKind
import java.util.*

class CodeOutlineAdapter(
    private val context: Context,
    private var symbols: List<CdSymb>
) : BaseAdapter(), Filterable {

    private var filteredSymbols: List<CdSymb> = symbols
    private val inflater = LayoutInflater.from(context)

    override fun getCount(): Int = filteredSymbols.size
    override fun getItem(position: Int): Any = filteredSymbols[position]
    override fun getItemId(position: Int): Long = position.toLong()

    override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
        val view: View
        val holder: ViewHolder

        if (convertView == null) {
            view = inflater.inflate(R.layout.list_item_code_symbol, parent, false)
            holder = ViewHolder(
                view.findViewById(R.id.symbol_icon),
                view.findViewById(R.id.symbol_name),
                view.findViewById(R.id.symbol_detail),
                view.findViewById(R.id.symbol_description)
            )
            view.tag = holder
        } else {
            view = convertView
            holder = view.tag as ViewHolder
        }

        val symbol = getItem(position) as CdSymb

        holder.icon.setImageResource(getIconForKind(symbol.kind))
        holder.name.text = symbol.name
        
        if (symbol.detail.isNotBlank()) {
            holder.detail.text = symbol.detail
            holder.detail.visibility = View.VISIBLE
        } else {
            holder.detail.visibility = View.GONE
        }
        
        if (symbol.description.isNotBlank()) {
            holder.description.text = symbol.description
            holder.description.visibility = View.VISIBLE
        } else {
            holder.description.visibility = View.GONE
        }

        return view
    }

    private fun getIconForKind(kind: SymbolKind): Int {
        return when (kind) {
            SymbolKind.PACKAGE -> R.drawable.ic_symbol_package
            SymbolKind.IMPORT -> R.drawable.ic_symbol_import
            SymbolKind.CLASS, SymbolKind.INTERFACE, SymbolKind.ENUM -> R.drawable.ic_symbol_class
            SymbolKind.METHOD, SymbolKind.FUNCTION -> R.drawable.ic_symbol_method
            SymbolKind.FIELD, SymbolKind.PROPERTY -> R.drawable.ic_symbol_field
            else -> R.drawable.ic_symbol_unknown
        }
    }
    
    override fun getFilter(): Filter {
        return object : Filter() {
            override fun performFiltering(constraint: CharSequence?): FilterResults {
                val results = FilterResults()
                if (constraint.isNullOrEmpty()) {
                    results.values = symbols
                    results.count = symbols.size
                } else {
                    val query = constraint.toString().lowercase(Locale.getDefault())
                    val filtered = symbols.filter { 
                        it.name.lowercase(Locale.getDefault()).contains(query) 
                    }
                    results.values = filtered
                    results.count = filtered.size
                }
                return results
            }

            @Suppress("UNCHECKED_CAST")
            override fun publishResults(constraint: CharSequence?, results: FilterResults?) {
                filteredSymbols = (results?.values as? List<CdSymb>) ?: emptyList()
                notifyDataSetChanged()
            }
        }
    }

    private class ViewHolder(
        val icon: ImageView,
        val name: TextView,
        val detail: TextView,
        val description: TextView
    )
}