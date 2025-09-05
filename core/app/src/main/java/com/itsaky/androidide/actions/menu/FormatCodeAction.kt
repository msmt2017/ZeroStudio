package com.itsaky.androidide.actions.menu

import android.content.Context
import com.itsaky.androidide.actions.ActionData
import com.itsaky.androidide.actions.EditorRelatedAction
import com.itsaky.androidide.resources.R
import java.io.File

/*
*高级格式化功能：
*1.为每一个文件类型提供单独的格式化缩进，没有标准的文件类型使用通用格式化缩进提供器
*2.可以使用官方规范的格式化风格，也可以自己自定义格式化缩进风格
*/
class FormatCodeAction(context: Context, override val order: Int) : EditorRelatedAction() {
    override val id: String = "ide.editor.format_code"

    init {
        label = context.getString(R.string.action_format_code)
        icon = androidx.core.content.ContextCompat.getDrawable(context, com.itsaky.androidide.R.drawable.ic_format_code)
    }

    override fun prepare(data: ActionData) {
        super.prepare(data)
        enabled = data.getEditor() != null && data.get(File::class.java) != null
    }

    override suspend fun execAction(data: ActionData): Boolean {
        val editor = data.getEditor() ?: return false
        val file = data.get(File::class.java) ?: return false
        return EditorLineOperations.formatCode(editor, file)
    }
}