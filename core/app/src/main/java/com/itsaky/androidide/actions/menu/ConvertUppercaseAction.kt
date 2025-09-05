
// by android_zero

package com.itsaky.androidide.actions.menu

import android.content.Context
import androidx.core.content.ContextCompat
import com.itsaky.androidide.resources.R
import com.itsaky.androidide.actions.ActionData
import com.itsaky.androidide.actions.EditorRelatedAction
import io.github.rosemoe.sora.widget.CodeEditor

/**
 * 中文注释: 将选中的文本转换为大写的动作。
 * English annotation: Action to convert the selected text to uppercase.
 */
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