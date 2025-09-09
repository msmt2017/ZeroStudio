// by android_zero
package com.itsaky.androidide.actions.menu

import android.content.Context
import androidx.core.content.ContextCompat
import com.itsaky.androidide.resources.R
import com.itsaky.androidide.actions.ActionData
import com.itsaky.androidide.actions.EditorRelatedAction
import io.github.rosemoe.sora.widget.CodeEditor

/**
 * 中文注释: 清空当前行或选中的行内容的动作。
 * English annotation: Action to empty the content of the current line or selected lines.
 */
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