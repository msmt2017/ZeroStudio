package com.itsaky.androidide.actions.menu

import android.content.Context
import com.itsaky.androidide.actions.ActionData
import com.itsaky.androidide.actions.EditorRelatedAction
import com.itsaky.androidide.resources.R
import java.io.File

/**
 * Action to show a dialog for inserting predefined code snippets.
 */
class ShowSnippetsAction(context: Context, override val order: Int) : EditorRelatedAction() {

    override val id: String = "ide.editor.show_snippets"

    init {
        label = context.getString(R.string.action_show_snippets)
    }

    override fun prepare(data: ActionData) {
        super.prepare(data)
        // 只要有编辑器和文件，就启用此功能
        enabled = data.getEditor() != null && data.get(File::class.java) != null
    }

    override suspend fun execAction(data: ActionData): Boolean {
        val editor = data.getEditor() ?: return false
        val file = data.get(File::class.java) ?: return false
        val context = data.get(Context::class.java) ?: return false
        
        // 调用统一的显示和插入片段方法
        EditorLineOperations.showAndInsertSnippet(editor, context, file)
        return true
    }
}