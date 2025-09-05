package com.itsaky.androidide.actions.menu

import android.content.Context
import androidx.core.content.ContextCompat
import com.itsaky.androidide.actions.ActionData
import com.itsaky.androidide.actions.EditorRelatedAction
import com.itsaky.androidide.editor.ui.IDEEditor
import com.itsaky.androidide.resources.R

class SwitchToIfElseAction(context: Context, override val order: Int) : EditorRelatedAction() {
    override val id: String = "ide.editor.refactor.switch_to_if"

    init {
        label = "Switch/When to If/Else"
        icon = ContextCompat.getDrawable(context, com.itsaky.androidide.R.drawable.ic_edit_refactor_switch_to_if)
    }

    override fun prepare(data: ActionData) {
        super.prepare(data)
        if (!visible) return

        val editor = data.get(IDEEditor::class.java)
        // enabled = editor != null && EditorLineOperations.isCursorInSwitch(editor)
    }

    override suspend fun execAction(data: ActionData): Boolean {
        val editor = data.get(IDEEditor::class.java) ?: return false
        return EditorLineOperations.convertSwitchToIfElse(editor)
    }
}
