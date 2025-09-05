package com.itsaky.androidide.actions.menu

import android.content.Context
import androidx.core.content.ContextCompat
import com.itsaky.androidide.actions.ActionData
import com.itsaky.androidide.actions.EditorRelatedAction
import com.itsaky.androidide.editor.ui.IDEEditor
import com.itsaky.androidide.resources.R

class IfElseToSwitchAction(context: Context, override val order: Int) : EditorRelatedAction() {
    override val id: String = "ide.editor.refactor.if_to_switch"

    init {
        label = "If/Else to Switch/When"
        icon = ContextCompat.getDrawable(context, com.itsaky.androidide.R.drawable.ic_edit_refactor_if_to_switch)
    }

    override fun prepare(data: ActionData) {
        super.prepare(data)
        if (!visible) return
        
        val editor = data.get(IDEEditor::class.java)
        enabled = editor != null && EditorLineOperations.isCursorInConvertibleIfElse(editor)
    }

    override suspend fun execAction(data: ActionData): Boolean {
        val editor = data.get(IDEEditor::class.java) ?: return false
        return EditorLineOperations.convertIfElseToSwitch(editor)
    }
}