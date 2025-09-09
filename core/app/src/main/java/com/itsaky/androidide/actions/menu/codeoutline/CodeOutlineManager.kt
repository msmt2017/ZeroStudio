package com.itsaky.androidide.actions.menu.codeoutline

import android.app.AlertDialog
import android.content.Context
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.widget.AdapterView
import android.widget.EditText
import android.widget.ListView
import com.google.android.material.textfield.TextInputLayout
import com.itsaky.androidide.R
import com.itsaky.androidide.actions.menu.codeoutline.CdSymb
import com.itsaky.androidide.actions.menu.codeoutline.SymbolKind
import com.itsaky.androidide.editor.ui.IDEEditor
import com.itsaky.androidide.editor.utils.setSelection
import com.itsaky.androidide.models.Position
import com.itsaky.androidide.models.Range
import io.github.rosemoe.sora.widget.EditorSearcher

object CodeOutlineManager {

    fun show(editor: IDEEditor, context: Context) {
        val symbols = CodeOutlineParser.getCodeStructure(editor)
        if (symbols.isEmpty()) {
            return
        }
        
        val dialogView = LayoutInflater.from(context).inflate(R.layout.action_menu_dialog_code_outline, null)
        val searchEditText = dialogView.findViewById<EditText>(R.id.edit_text_search)
        val listView = dialogView.findViewById<ListView>(R.id.list_view_symbols)
        
        val adapter = CodeOutlineAdapter(context, symbols)
        listView.adapter = adapter

        searchEditText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                adapter.filter.filter(s)
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        val dialog = AlertDialog.Builder(context)
            .setTitle(context.getString(R.string.action_show_code_outline))
            .setView(dialogView)
            .setNegativeButton(R.string.cancel_button, null)
            .create()

        listView.onItemClickListener = AdapterView.OnItemClickListener { _, _, position, _ ->
            val symbol = adapter.getItem(position) as CdSymb
            editor.setSelection(symbol.selectionRange)
            editor.jumpToLine(symbol.selectionRange.start.line)
            dialog.dismiss()
        }

        listView.onItemLongClickListener = AdapterView.OnItemLongClickListener { _, _, position, _ ->
            val symbol = adapter.getItem(position) as CdSymb
            showSymbolActions(editor, context, symbol)
            true
        }
        
        dialog.show()
    }
    
    private fun showSymbolActions(editor: IDEEditor, context: Context, symbol: CdSymb) {
        val renameable = when(symbol.kind) {
            SymbolKind.CLASS, SymbolKind.INTERFACE, SymbolKind.ENUM, 
            SymbolKind.METHOD, SymbolKind.FUNCTION, SymbolKind.FIELD, 
            SymbolKind.PROPERTY -> true
            else -> false
        }
        
        if (!renameable) return

        val actions = arrayOf<CharSequence>("Rename")
        AlertDialog.Builder(context)
            .setTitle("${symbol.kind.name.lowercase().replaceFirstChar { it.titlecase() }}: ${symbol.name}")
            .setItems(actions) { _, which ->
                when (which) {
                    0 -> showRenameDialog(editor, context, symbol)
                }
            }
            .show()
    }

    private fun showRenameDialog(editor: IDEEditor, context: Context, symbol: CdSymb) {
        val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_rename_symbol, null)
        val textInputLayout = dialogView.findViewById<TextInputLayout>(R.id.textInputLayout)
        val editText = dialogView.findViewById<EditText>(R.id.editText)
        editText.setText(symbol.name)
        textInputLayout.hint = "New name"

        val dialog = AlertDialog.Builder(context)
            .setTitle("Rename")
            .setView(dialogView)
            .setPositiveButton("OK", null)
            .setNegativeButton(R.string.cancel_button, null)
            .create()
        
        dialog.setCanceledOnTouchOutside(false)

        dialog.setOnShowListener {
            val positiveButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            positiveButton.setOnClickListener {
                val newName = editText.text.toString()
                if (newName.isNotBlank() && newName != symbol.name) {
                    renameSymbolInFile(editor, symbol.selectionRange, newName)
                    dialog.dismiss()
                } else {
                    textInputLayout.error = "Name cannot be blank."
                }
            }
        }
        
        dialog.show()
    }
    
    private fun renameSymbolInFile(editor: IDEEditor, symbolRange: Range, newName: String) {
        val oldName = editor.text.subSequence(
            editor.text.getCharIndex(symbolRange.start.line, symbolRange.start.column),
            editor.text.getCharIndex(symbolRange.end.line, symbolRange.end.column)
        ).toString()

        val searcher = editor.searcher
        searcher.search(oldName, EditorSearcher.SearchOptions(true, true))
        
        val replacements = mutableListOf<Pair<Range, String>>()
        while (searcher.gotoNext()) {
            replacements.add(editor.cursor.range.toLspRange() to newName)
        }
        searcher.stopSearch()
        
        editor.text.beginBatchEdit()
        try {
            replacements.asReversed().forEach { (range, text) ->
                editor.text.replace(range.start.line, range.start.column, range.end.line, range.end.column, text)
            }
        } finally {
            editor.text.endBatchEdit()
        }
    }

    private fun io.github.rosemoe.sora.text.TextRange.toLspRange(): Range {
        return Range(Position(this.start.line, this.start.column), Position(this.end.line, this.end.column))
    }
}