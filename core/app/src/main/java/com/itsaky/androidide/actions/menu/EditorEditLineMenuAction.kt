// by android_zero
package com.itsaky.androidide.actions.menu

import android.content.Context
import android.view.MenuItem
import androidx.core.content.ContextCompat
import com.blankj.utilcode.util.KeyboardUtils
import com.itsaky.androidide.resources.R
import com.itsaky.androidide.actions.ActionData
import com.itsaky.androidide.actions.ActionItem
import com.itsaky.androidide.actions.ActionMenu // Import ActionMenu
import com.itsaky.androidide.actions.EditorRelatedAction
import com.itsaky.androidide.actions.markInvisible
import io.github.rosemoe.sora.widget.CodeEditor
import android.util.Log // Import Log for debugging

/**
 * 中文注释: EditorEditLineMenuAction 类是一个整合了文本编辑器中所有“编辑”菜单操作的 Action 类，
 * 包括复制行、剪切行、删除行、清空行、替换行、复制行、转换为大写、转换为小写、增加缩进、减少缩进、
 * 切换注释以及跳转到行等功能。此Action现在作为ActionMenu，用于打开一个包含这些操作的二级菜单。
 * English annotation: The EditorEditLineMenuAction class is an Action class that consolidates all "Edit" menu operations in the text editor,
 * including copy line, cut line, delete line, empty line, replace line, duplicate line,
 * convert to uppercase, convert to lowercase, increase indent, decrease indent,
 * toggle comment, and jump to line functionalities. This Action now acts as an ActionMenu
 * to open a sub-menu containing these operations.
 *
 * @param context 中文注释: 应用上下文。 English annotation: The application context.
 * @param order 中文注释: 此操作在菜单或工具栏中的排序顺序。 English annotation: The order of this action in menus or toolbars.
 */
class EditorEditLineMenuAction(context: Context, override val order: Int) : EditorRelatedAction(), ActionMenu {

    // 中文注释: 用于存储子菜单项的集合
    // English annotation: Collection to store sub-menu items
    override val children: MutableSet<ActionItem> = mutableSetOf()

    /**
     * 中文注释: 初始化块，设置操作的标签和图标，并注册所有子菜单项。
     * English annotation: Initialization block, sets the action's label and icon, and registers all sub-menu items.
     */
    init {
        label = context.getString(R.string.edit) // 中文注释: 设置操作的显示文本为“Edit”
        // English annotation: Sets the action's display text to "Edit"
        icon = ContextCompat.getDrawable(context, R.drawable.ic_edit) // 中文注释: 设置操作的图标
        // English annotation: Sets the action's icon

        // 中文注释: 将所有行操作注册为该ActionMenu的子项
        // English annotation: Register all line operations as children of this ActionMenu
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
    }

    /**
     * 中文注释: 操作的唯一标识符。此ID应与XML菜单中“Edit”菜单项的ID匹配。
     * English annotation: The unique identifier for the action. This ID should match the ID of the "Edit" menu item in the XML menu.
     */
    override val id: String = "ide.editor.code.text.edit_menu"

    /**
     * 中文注释: 准备操作，根据编辑器状态启用或禁用操作。
     * English annotation: Prepares the action, enabling or disabling it based on the editor's state.
     * @param data 中文注释: 包含操作所需数据的 ActionData 对象。 English annotation: The ActionData object containing data required for the action.
     */
    override fun prepare(data: ActionData) {
        super<EditorRelatedAction>.prepare(data) // Call super for EditorRelatedAction
        super<ActionMenu>.prepare(data) // Call super for ActionMenu

        if (!visible) {
            return
        }

        val editor = data.getEditor() ?: run {
            markInvisible() // 中文注释: 如果编辑器不可用，则将操作标记为不可见
            // English annotation: Marks the action as invisible if the editor is not available
            return
        }

        // 中文注释: 如果编辑器可编辑，则“编辑”菜单通常应启用。
        // English annotation: The "Edit" menu should generally be enabled if the editor is editable.
        enabled = editor.isEditable
    }

    /**
     * 中文注释: 执行“编辑”菜单操作。作为ActionMenu，此方法通常由框架处理，不直接显示PopupMenu。
     * English annotation: Executes the "Edit" menu action. As an ActionMenu, this method is typically handled by the framework and does not directly display a PopupMenu.
     * @param data 中文注释: 包含操作所需数据的 ActionData 对象。 English annotation: The ActionData object containing data required for the action.
     * @return Boolean 中文注释: 如果操作成功执行则返回 true，否则返回 false。 English annotation: True if the action was executed successfully, false otherwise.
     */
    override suspend fun execAction(data: ActionData): Boolean {
        // 中文注释: 对于ActionMenu类型，execAction通常是空的，因为框架会负责显示其子菜单。
        // English annotation: For ActionMenu types, execAction is usually empty, as the framework is responsible for displaying its sub-menu.
        Log.d("EditorEditLineMenuAction", "execAction called for ActionMenu. Framework should handle sub-menu display.")
        return true // Return true to indicate the action was handled (even if it's just to open a sub-menu)
    }

    // --- Individual Action Classes for Line Operations ---
    // These classes will encapsulate the logic for each specific line operation.
    // You will need to create these files in your project, e.g., in `com.itsaky.androidide.actions.menu`
    // Their execAction methods now call functions from the EditorLineOperations object.

    class CopyLineAction(context: Context, override val order: Int) : EditorRelatedAction() {
        override val id: String = "ide.editor.line.copy"
        init {
            label = context.getString(R.string.copy_line)
            icon = ContextCompat.getDrawable(context, R.drawable.ic_edit_copy_line)
        }
        override suspend fun execAction(data: ActionData): Boolean {
            val editor = data.getEditor() ?: return false
            val context = data.get(Context::class.java) ?: return false
            return EditorLineOperations.copyLine(editor, context)
        }
    }

    class CutLineAction(context: Context, override val order: Int) : EditorRelatedAction() {
        override val id: String = "ide.editor.line.cut"
        init {
            label = context.getString(R.string.cut_line)
            icon = ContextCompat.getDrawable(context, R.drawable.ic_edit_cut_line)
        }
        override suspend fun execAction(data: ActionData): Boolean {
            val editor = data.getEditor() ?: return false
            val context = data.get(Context::class.java) ?: return false
            return EditorLineOperations.cutLine(editor, context)
        }
    }

    class DeleteLineAction(context: Context, override val order: Int) : EditorRelatedAction() {
        override val id: String = "ide.editor.line.delete"
        init {
            label = context.getString(R.string.delete_line)
            icon = ContextCompat.getDrawable(context, R.drawable.ic_edit_delete_line)
        }
        override suspend fun execAction(data: ActionData): Boolean {
            val editor = data.getEditor() ?: return false
            val context = data.get(Context::class.java) ?: return false
            return EditorLineOperations.deleteLine(editor, context)
        }
    }

    class EmptyLineAction(context: Context, override val order: Int) : EditorRelatedAction() {
        override val id: String = "ide.editor.line.empty"
        init {
            label = context.getString(R.string.empty_line)
            icon = ContextCompat.getDrawable(context, R.drawable.ic_edit_empty_line)
        }
        override suspend fun execAction(data: ActionData): Boolean {
            val editor = data.getEditor() ?: return false
            val context = data.get(Context::class.java) ?: return false
            return EditorLineOperations.emptyLine(editor, context)
        }
    }

    class ReplaceLineAction(context: Context, override val order: Int) : EditorRelatedAction() {
        override val id: String = "ide.editor.line.replace"
        init {
            label = context.getString(R.string.replace_line)
            icon = ContextCompat.getDrawable(context, R.drawable.ic_edit_replace_line)
        }
        override suspend fun execAction(data: ActionData): Boolean {
            val editor = data.getEditor() ?: return false
            val context = data.get(Context::class.java) ?: return false
            return EditorLineOperations.replaceLine(editor, context)
        }
    }

    class DuplicateLineAction(context: Context, override val order: Int) : EditorRelatedAction() {
        override val id: String = "ide.editor.line.duplicate"
        init {
            label = context.getString(R.string.duplicate_line)
            icon = ContextCompat.getDrawable(context, R.drawable.ic_edit_duplicate_line)
        }
        override suspend fun execAction(data: ActionData): Boolean {
            val editor = data.getEditor() ?: return false
            val context = data.get(Context::class.java) ?: return false
            return EditorLineOperations.duplicateLine(editor, context)
        }
    }

    class ConvertUppercaseAction(context: Context, override val order: Int) : EditorRelatedAction() {
        override val id: String = "ide.editor.line.convert_uppercase"
        init {
            label = context.getString(R.string.convert_uppercase)
            icon = ContextCompat.getDrawable(context, R.drawable.ic_edit_convert_uppercase)
        }
        override suspend fun execAction(data: ActionData): Boolean {
            val editor = data.getEditor() ?: return false
            val context = data.get(Context::class.java) ?: return false
            return EditorLineOperations.convertUpperLowerCase(editor, context, true)
        }
    }

    class ConvertLowercaseAction(context: Context, override val order: Int) : EditorRelatedAction() {
        override val id: String = "ide.editor.line.convert_lowercase"
        init {
            label = context.getString(R.string.convert_lowercase)
            icon = ContextCompat.getDrawable(context, R.drawable.ic_edit_convert_lowercase)
        }
        override suspend fun execAction(data: ActionData): Boolean {
            val editor = data.getEditor() ?: return false
            val context = data.get(Context::class.java) ?: return false
            return EditorLineOperations.convertUpperLowerCase(editor, context, false)
        }
    }

    class IncreaseIndentAction(context: Context, override val order: Int) : EditorRelatedAction() {
        override val id: String = "ide.editor.line.increase_indent"
        init {
            label = context.getString(R.string.increase_indent)
            icon = ContextCompat.getDrawable(context, R.drawable.ic_edit_increase_indent)
        }
        override suspend fun execAction(data: ActionData): Boolean {
            val editor = data.getEditor() ?: return false
            val context = data.get(Context::class.java) ?: return false
            return EditorLineOperations.increaseIndent(editor, context)
        }
    }

    class DecreaseIndentAction(context: Context, override val order: Int) : EditorRelatedAction() {
        override val id: String = "ide.editor.line.decrease_indent"
        init {
            label = context.getString(R.string.decrease_indent)
            icon = ContextCompat.getDrawable(context, R.drawable.ic_edit_decrease_indent)
        }
        override suspend fun execAction(data: ActionData): Boolean {
            val editor = data.getEditor() ?: return false
            val context = data.get(Context::class.java) ?: return false
            return EditorLineOperations.decreaseIndent(editor, context)
        }
    }

    class ToggleCommentAction(context: Context, override val order: Int) : EditorRelatedAction() {
        override val id: String = "ide.editor.line.toggle_comment"
        init {
            label = context.getString(R.string.toggle_comment)
            icon = ContextCompat.getDrawable(context, R.drawable.ic_edit_toggle_comment)
        }
        override suspend fun execAction(data: ActionData): Boolean {
            val editor = data.getEditor() ?: return false
            val context = data.get(Context::class.java) ?: return false
            return EditorLineOperations.toggleComment(editor, context)
        }
    }

    class JumpToLineAction(context: Context, override val order: Int) : EditorRelatedAction() {
        override val id: String = "ide.editor.line.jump_to_line"
        init {
            label = context.getString(R.string.jump_to_line)
            icon = ContextCompat.getDrawable(context, R.drawable.ic_edit_jump_to_line)
        }
        override suspend fun execAction(data: ActionData): Boolean {
            val editor = data.getEditor() ?: return false
            val context = data.get(Context::class.java) ?: return false
            return EditorLineOperations.jumpToLine(editor, context)
        }
    }
}
