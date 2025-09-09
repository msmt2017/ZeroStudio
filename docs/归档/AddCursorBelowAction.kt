package com.itsaky.androidide.actions.menu

import android.content.Context
import com.itsaky.androidide.actions.ActionData
import com.itsaky.androidide.actions.EditorRelatedAction
import com.itsaky.androidide.editor.utils.getCursorCount
import com.itsaky.androidide.resources.R

/**
 * Action to add a new cursor on the line below the primary cursor.
 */
class AddCursorBelowAction(context: Context, override val order: Int) : EditorRelatedAction() {

    override val id: String = "ide.editor.cursor.add_below"

    init {
        label = context.getString(R.string.action_add_cursor_below)
    }

    override fun prepare(data: ActionData) {
        super.prepare(data)
        val editor = data.getEditor()
        enabled = editor != null && editor.cursor.getCursorCount() <= 1 && editor.cursor.leftLine < editor.lineCount - 1
    }

    override suspend fun execAction(data: ActionData): Boolean {
        val editor = data.getEditor() ?: return false
        return EditorLineOperations.addCursorBelow(editor)
    }
}