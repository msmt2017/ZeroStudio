// app/src/main/java/com/itsaky/androidide/actions/filetree/OpenInChatAiAction.kt
package com.itsaky.androidide.actions.filetree

import android.content.Context
import com.itsaky.androidide.actions.ActionData
import com.itsaky.androidide.actions.requireFile
import com.itsaky.androidide.resources.R
import com.itsaky.androidide.utils.flashInfo
import com.itsaky.androidide.utils.flashSuccess
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import me.rerere.rikkahub.mcp.McpClient

/**
 * 在 ChatAI 窗口上传并打开当前文件
 */
class OpenInChatAiAction(context: Context, override val order: Int) :
  BaseFileTreeAction(context, labelRes = R.string.open_in_chatai, iconRes = R.drawable.ic_terminal) {

  override val id: String = "ide.editor.fileTree.openInChatAi"

  override suspend fun execAction(data: ActionData) {
    val file = data.requireFile()
    val projectRoot = data.get(Context::class.java)
      ?.let { com.itsaky.androidide.projects.IProjectManager.getInstance().projectDirPath }
      ?: ""
    val client = McpClient()
    client.sendCommand("file.upload", mapOf(
      "path" to file.absolutePath,
      "projectRoot" to projectRoot
    ))
    // 给用户一个提示
    flashSuccess(R.string.opened_in_chatai)
  }
}
