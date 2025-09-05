// by android_zero
package com.itsaky.androidide.actions.menu

import android.content.Context
import androidx.core.content.ContextCompat
import com.itsaky.androidide.resources.R
import com.itsaky.androidide.actions.ActionData
import com.itsaky.androidide.actions.EditorRelatedAction
import io.github.rosemoe.sora.widget.CodeEditor

/**
 * 中文注释: 减少当前行或选中区域缩进的动作。
 * English annotation: Action to decrease the indentation of the current line or selected region.
 */
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