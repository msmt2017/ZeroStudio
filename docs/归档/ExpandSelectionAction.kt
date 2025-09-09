package com.itsaky.androidide.actions.menu

import android.content.Context
import com.itsaky.androidide.actions.ActionData
import com.itsaky.androidide.actions.EditorRelatedAction
import com.itsaky.androidide.editor.ui.IDEEditor
import com.itsaky.androidide.resources.R

class ExpandSelectionAction(context: Context, override val order: Int) : EditorRelatedAction() {

    override val id: String = "ide.editor.smart_select.expand"

    init {
        label = context.getString(R.string.action_intelligent_expand_selection) 
    }

    override fun prepare(data: ActionData) {
        super.prepare(data)
        val editor = data.get(IDEEditor::class.java)
        enabled = editor?.editorLanguage is com.itsaky.androidide.editor.language.treesitter.TreeSitterLanguage
    }

    override suspend fun execAction(data: ActionData): Boolean {
        val editor = data.getEditor() ?: return false
        return EditorLineOperations.expandSelection(editor)
    }
}