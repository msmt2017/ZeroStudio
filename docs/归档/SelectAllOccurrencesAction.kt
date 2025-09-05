package com.itsaky.androidide.actions.menu

import android.content.Context
import com.itsaky.androidide.actions.ActionData
import com.itsaky.androidide.actions.EditorRelatedAction
import com.itsaky.androidide.editor.utils.getCursorCount
import com.itsaky.androidide.resources.R

class SelectAllOccurrencesAction(context: Context, override val order: Int) : EditorRelatedAction() {
    override val id: String = "ide.editor.cursor.select_all_occurrences"

    init {
        label = context.getString(R.string.action_select_all_occurrences)
    }

    override fun prepare(data: ActionData) {
        super.prepare(data)
        val editor = data.getEditor()
        enabled = editor != null && editor.cursor.getCursorCount() <= 1
    }

    override suspend fun execAction(data: ActionData): Boolean {
        val editor = data.getEditor() ?: return false
        return EditorLineOperations.selectAllOccurrences(editor)
    }
}