// by android_zero

package com.itsaky.androidide.actions.menu

import android.content.Context
import androidx.core.content.ContextCompat
import com.itsaky.androidide.resources.R
import com.itsaky.androidide.actions.ActionData
import com.itsaky.androidide.actions.EditorRelatedAction
import io.github.rosemoe.sora.widget.CodeEditor

/**
 * 中文注释: 剪切当前行或选中的文本的动作。
 * English annotation: Action to cut the current line or selected text.
 */
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