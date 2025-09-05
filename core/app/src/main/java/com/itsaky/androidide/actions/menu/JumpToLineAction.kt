// by android_zero
package com.itsaky.androidide.actions.menu

import android.content.Context
import androidx.core.content.ContextCompat
import com.itsaky.androidide.resources.R
import com.itsaky.androidide.actions.ActionData
import com.itsaky.androidide.actions.EditorRelatedAction
import io.github.rosemoe.sora.widget.CodeEditor

/**
 * 中文注释: 跳转到指定行号的动作。
 * English annotation: Action to jump to a specific line number.
 */
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