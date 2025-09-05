

// by android_zero
package com.itsaky.androidide.actions.menu

import android.content.Context
import android.util.Log
import androidx.core.content.ContextCompat
import com.itsaky.androidide.actions.*
import com.itsaky.androidide.resources.R
import com.itsaky.androidide.viewmodel.EditorViewModel
import java.io.File

/**
 * An [ActionMenu] that consolidates all "Edit" menu operations for the text editor.
 *
 * This action serves as a container for a sub-menu that includes operations like
 * copy, cut, delete, duplicate, change case, manage indentation, toggle comments,
 * jump to a specific line, navigate cursor history, and toggle read-only mode.
 *
 * @param context The application context, used for retrieving resources.
 * @param order The order of this action in menus or toolbars.
 */
class EditorEditLineMenuAction(context: Context, override val order: Int) : EditorRelatedAction(), ActionMenu {

    /**
     * A mutable set holding all the child [ActionItem]s for this menu.
     */
    override val children: MutableSet<ActionItem> = mutableSetOf()

    /**
     * Initializes the menu action by setting its label and icon, and registering all
     * its child actions.
     */
    init {
        label = context.getString(R.string.edit)
        icon = ContextCompat.getDrawable(context, R.drawable.ic_edit)

        // Register all line and navigation operations as children of this ActionMenu
        addAction(CopyLineAction(context, 0))
        addAction(CutLineAction(context, 1))
        addAction(DeleteLineAction(context, 2))
        addAction(EmptyLineAction(context, 3))
        addAction(ReplaceLineAction(context, 4))
        addAction(DuplicateLineAction(context, 5))
        addAction(ConvertUppercaseAction(context, 6))
        addAction(ConvertLowercaseAction(context, 7))
        addAction(IncreaseIndentAction(context, 8))
        addAction(DecreaseIndentAction(context, 9))
        addAction(ToggleCommentAction(context, 10))
        addAction(JumpToLineAction(context, 11))
        
        addAction(FormatCodeAction(context, 12))
        addAction(PreviousLocationAction(context, 13))
        addAction(NextLocationAction(context, 14))
        addAction(ToggleReadOnlyAction(context, 15))
        addAction(ShowSnippetsAction(context, 16))
        addAction(SwitchToIfElseAction(context, 17))
        addAction(IfElseToSwitchAction(context, 18))
        addAction(ShowCodeOutlineAction(context, 19))
        // addAction(ExpandSelectionAction(context, 16)) //已归档，暂时没啥用
        // addAction(ShrinkSelectionAction(context, 17))//已归档，暂时没啥用
        // addAction(AddCursorAboveAction(context, 18))//已归档，暂时没啥用
        // addAction(AddCursorBelowAction(context, 19))//已归档，暂时没啥用
        // addAction(SelectAllOccurrencesAction(context, 20))//已归档，暂时没啥用

    }

    /**
     * The unique identifier for the action.
     */
    override val id: String = "ide.editor.code.text.edit_menu"

    /**
     * Prepares the action by updating its state based on the current context.
     * This action is made invisible if no editor is available.
     *
     * @param data The [ActionData] object containing data required for the action.
     */
    override fun prepare(data: ActionData) {
        super<EditorRelatedAction>.prepare(data) // Call super for EditorRelatedAction
        super<ActionMenu>.prepare(data) // Call super for ActionMenu

        if (!visible) {
            return
        }

        val editor = data.getEditor() ?: run {
            markInvisible()
            return
        }

        // The "Edit" menu itself should be visible if an editor exists.
        // Child actions will handle their own enabled state.
        enabled = true
    }

    /**
     * Executes the action. For an [ActionMenu], this method is a no-op as the framework
     * is responsible for displaying the sub-menu.
     *
     * @return `true` to indicate the action was handled.
     */
    override suspend fun execAction(data: ActionData): Boolean {
        Log.d("EditorEditLineMenuAction", "execAction called. Framework should handle sub-menu display.")
        return true
    }
}
