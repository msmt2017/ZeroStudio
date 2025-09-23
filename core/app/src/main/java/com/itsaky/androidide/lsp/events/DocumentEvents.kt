package com.itsaky.androidide.lsp.events

import com.itsaky.androidide.models.OpenedFile
import com.itsaky.androidide.models.Range
import java.io.File

/** LSP 文档打开事件 */
data class DocumentOpenEvent(
    val file: File,
    val content: String,
    val version: Int = 1
)

/** LSP 文档变更事件 */
data class DocumentChangeEvent(
    val file: File,
    val newContent: String,
    val version: Int,
    val changeRange: Range? = null
)

/** LSP 文档关闭事件 */
data class DocumentCloseEvent(
    val file: File,
    val version: Int
)
