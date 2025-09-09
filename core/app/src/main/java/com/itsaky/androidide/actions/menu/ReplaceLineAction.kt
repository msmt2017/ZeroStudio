// by android_zero
package com.itsaky.androidide.actions.menu

import android.content.Context
import androidx.core.content.ContextCompat
import com.itsaky.androidide.resources.R
import com.itsaky.androidide.actions.ActionData
import com.itsaky.androidide.actions.EditorRelatedAction
import io.github.rosemoe.sora.widget.CodeEditor

/**
 * 中文注释: 用剪贴板内容替换当前行或选中的行内容的动作。
 * English annotation: Action to replace the content of the current line or selected lines with the clipboard content.
 */
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