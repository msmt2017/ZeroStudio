package com.itsaky.androidide.actions.menu

import android.content.Context
import androidx.core.content.ContextCompat
import com.itsaky.androidide.actions.ActionData
import com.itsaky.androidide.actions.EditorRelatedAction
import com.itsaky.androidide.resources.R

/**
 * Navigates to the next cursor location in the editor's history.
 */
class NextLocationAction(context: Context, override val order: Int) : EditorRelatedAction() {

    override val id: String = "ide.editor.nav.next_location"

    init {
        label = context.getString(R.string.action_menu_editnext_location)
        icon = ContextCompat.getDrawable(context, R.drawable.ic_edit_line_next_position)
    }

    override fun prepare(data: ActionData) {
        super.prepare(data)
        // Enable this action only if there is a next location to go to.
        // enabled = EditorLineOperations.navigationHistory.canGoForward()
    }

    override suspend fun execAction(data: ActionData): Boolean {
        val editor = data.getEditor() ?: return false
        return EditorLineOperations.goToNextLocation(editor)
    }
}