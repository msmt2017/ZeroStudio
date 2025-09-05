package com.itsaky.androidide.actions.menu

import android.content.Context
import androidx.core.content.ContextCompat
import com.itsaky.androidide.actions.ActionData
import com.itsaky.androidide.actions.CheckableAction
import com.itsaky.androidide.actions.EditorRelatedAction
import com.itsaky.androidide.R

/**
 * Toggles the read-only mode for the editor.
 * This action is checkable, reflecting the current state.
 */
class ToggleReadOnlyAction(context: Context, override val order: Int) : EditorRelatedAction(), CheckableAction {

    override val id: String = "ide.editor.toggle_readonly"
    override var checked: Boolean = false

    init {
        label = context.getString(R.string.action_menu_edit_read_write_mode)
        icon = ContextCompat.getDrawable(context, R.drawable.ic_edit_read_write_mode)
        // Initialize with the persisted setting as a default
        checked = EditorLineOperations.isReadOnly(context)
    }

    /**
     * Prepares the action's state. The checked status is synced directly
     * with the editor's current editable state, which is the source of truth.
     */
    override fun prepare(data: ActionData) {
        super<EditorRelatedAction>.prepare(data)
        val editor = data.getEditor()
        // The action is only enabled if an editor exists.
        enabled = editor != null
        // The checked state directly reflects if the editor is NOT editable.
        checked = editor != null && !editor.isEditable
    }

    /**
     * Executes the action to toggle the read-only state.
     */
    override suspend fun execAction(data: ActionData): Boolean {
        val editor = data.getEditor() ?: return false
        val context = data.get(Context::class.java) ?: return false
        
        // The operation returns the new read-only state, which we use to update our checked status.
        val isNowReadOnly = EditorLineOperations.toggleReadOnly(editor, context)
        checked = isNowReadOnly
        
        return true
    }
}