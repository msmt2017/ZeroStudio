// by android_zero

package com.itsaky.androidide.actions.menu

import android.content.Context
import androidx.core.content.ContextCompat
import com.itsaky.androidide.resources.R
import com.itsaky.androidide.actions.ActionData
import com.itsaky.androidide.actions.EditorRelatedAction
import io.github.rosemoe.sora.widget.CodeEditor

/**
 * 中文注释: 切换当前行或选中区域注释状态的动作。
 * English annotation: Action to toggle the comment state of the current line or selected region.
 */
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
