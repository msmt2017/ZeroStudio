
package com.itsaky.androidide.actions.menu

import android.content.Context
import androidx.core.content.ContextCompat
import com.itsaky.androidide.actions.ActionData
import com.itsaky.androidide.actions.EditorRelatedAction
import com.itsaky.androidide.resources.R

/**
 * Navigates to the previously recorded cursor location in the editor's history.
 */
class PreviousLocationAction(context: Context, override val order: Int) : EditorRelatedAction() {

    override val id: String = "ide.editor.nav.previous_location"

    init {
        label = context.getString(R.string.action_menu_edit_previous_location)
        icon = ContextCompat.getDrawable(context, R.drawable.ic_edit_line_previous_position)
    }

    override fun prepare(data: ActionData) {
        super.prepare(data)
        // Enable this action only if there is a previous location to go to.
        // enabled = EditorLineOperations.navigationHistory.canGoBack()
    }

    override suspend fun execAction(data: ActionData): Boolean {
        val editor = data.getEditor() ?: return false
        return EditorLineOperations.goToPreviousLocation(editor)
    }
}