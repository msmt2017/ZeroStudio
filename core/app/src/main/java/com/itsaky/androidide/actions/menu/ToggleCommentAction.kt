package com.itsaky.androidide.actions.menu

import android.content.Context
import androidx.core.content.ContextCompat
import com.itsaky.androidide.resources.R
import com.itsaky.androidide.actions.ActionData
import com.itsaky.androidide.actions.EditorRelatedAction
import io.github.rosemoe.sora.widget.CodeEditor
import java.io.File

/*
*中文注释： 切换当前行或所选区域的注释状态的编辑器作。
 *
 * 此作会根据
 * 当前活动的编辑器选项卡的文件类型。实际逻辑由
 * [EditorLineOperations.toggleComment]。
 *
 * @param 上下文 用于检索字符串和可绘制对象的上下文。
 * @param顺序 此作在菜单中的显示顺序。
*
 * An editor action that toggles the comment state of the current line or selected region.
 *
 * This action intelligently adds or removes the line or block comment syntax based on the
 * file type of the currently active editor tab. The actual logic is handled by
 * [EditorLineOperations.toggleComment].
 *
 * @param context The context used to retrieve strings and drawables.
 * @param order The display order of this action in a menu.
 */
class ToggleCommentAction(context: Context, override val order: Int) : EditorRelatedAction() {
    /**
     * The unique identifier for this action.
     */
    override val id: String = "ide.editor.line.toggle_comment"

    init {
        /**
         * The text label displayed for this action.
         */
        label = context.getString(R.string.toggle_comment)
        /**
         * The icon displayed for this action.
         */
        icon = ContextCompat.getDrawable(context, R.drawable.ic_edit_toggle_comment)
    }

    /**
     * Executes the action to toggle comments in the editor.
     * It retrieves the current file from the action data and passes it to the
     * operation handler to apply the correct comment style.
     *
     * @param data The [ActionData] containing the current context, including the editor instance and the active file.
     * @return `true` if the operation was successful, `false` otherwise.
     */
    override suspend fun execAction(data: ActionData): Boolean {
        val editor = data.getEditor() ?: return false
        // Get the currently active file from the action data. This is crucial for determining the file type.
        val file = data.get(File::class.java) ?: return false

        // Call the updated toggleComment method, now passing the file object.
        return EditorLineOperations.toggleComment(editor, file)
    }
}