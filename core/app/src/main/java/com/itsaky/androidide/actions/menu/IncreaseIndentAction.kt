// by android_zero

package com.itsaky.androidide.actions.menu

import android.content.Context
import androidx.core.content.ContextCompat
import com.itsaky.androidide.resources.R
import com.itsaky.androidide.actions.ActionData
import com.itsaky.androidide.actions.EditorRelatedAction
import io.github.rosemoe.sora.widget.CodeEditor

/**
 * 中文注释: 增加当前行或选中区域缩进的动作。
 * English annotation: Action to increase the indentation of the current line or selected region.
 */
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