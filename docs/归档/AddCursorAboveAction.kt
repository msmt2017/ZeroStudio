package com.itsaky.androidide.actions.menu

import android.content.Context
import com.itsaky.androidide.R
import com.itsaky.androidide.actions.ActionData
import com.itsaky.androidide.actions.EditorRelatedAction
import com.itsaky.androidide.editor.utils.getCursorCount
import io.github.rosemoe.sora.widget.CodeEditor

class AddCursorAboveAction(context: Context, override val order: Int) : EditorRelatedAction() {
    override val id: String = "ide.editor.cursor.add_above"
    init {
        label = context.getString(R.string.action_add_cursor_above)
    }
    override fun prepare(data: ActionData) {
        super.prepare(data)
        val editor = data.getEditor()
        enabled = editor != null && editor.cursor.getCursorCount() <= 1 && editor.cursor.leftLine > 0
    }
    override suspend fun execAction(data: ActionData): Boolean {
        val editor = data.getEditor() ?: return false
        return EditorLineOperations.addCursorAbove(editor)
    }
}