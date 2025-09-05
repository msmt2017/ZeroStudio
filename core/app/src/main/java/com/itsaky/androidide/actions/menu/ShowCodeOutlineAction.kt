
package com.itsaky.androidide.actions.menu

import android.content.Context
import androidx.core.content.ContextCompat
import com.itsaky.androidide.actions.ActionData
import com.itsaky.androidide.actions.EditorRelatedAction
import com.itsaky.androidide.editor.ui.IDEEditor
import com.itsaky.androidide.resources.R

class ShowCodeOutlineAction(context: Context, override val order: Int) : EditorRelatedAction() {
    override val id: String = "ide.editor.show_code_outline"
    init {
        label = context.getString(R.string.action_show_code_outline) 
        icon = ContextCompat.getDrawable(context, R.drawable.ic_edit_code_outline_action)
    }
    override fun prepare(data: ActionData) {
        super.prepare(data)
        val editor = data.get(IDEEditor::class.java)
        enabled = editor?.editorLanguage is com.itsaky.androidide.editor.language.treesitter.TreeSitterLanguage
    }
    override suspend fun execAction(data: ActionData): Boolean {
        val editor = data.getEditor() ?: return false
        val context = data.get(Context::class.java) ?: return false
        EditorLineOperations.showCodeOutline(editor, context)
        return true
    }
}