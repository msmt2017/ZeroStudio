// by android_zero

package com.itsaky.androidide.actions.menu

import android.content.Context
import androidx.core.content.ContextCompat
import com.itsaky.androidide.resources.R
import com.itsaky.androidide.actions.ActionData
import com.itsaky.androidide.actions.EditorRelatedAction
import io.github.rosemoe.sora.widget.CodeEditor

/**
 * 中文注释: 复制当前行或选中的文本到剪贴板的动作。
 * English annotation: Action to copy the current line or selected text to the clipboard.
 */
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