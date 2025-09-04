package com.itsaky.androidide.actions.filetree

import com.itsaky.androidide.eventbus.events.Event
import java.io.File

/**
 * Event to request highlighting a file or directory in the file tree.
 * 用于请求在文件树中高亮显示文件或目录的事件。
 *
 * @property file The file or directory to highlight.
 */
data class FileHighlightEvent(val file: File) : Event()
