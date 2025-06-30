package com.itsaky.androidide.fragments.sidebar.datatree

import com.itsaky.androidide.eventbus.events.Event
import java.io.File
import androidx.documentfile.provider.DocumentFile

/**
 * 支持File和DocumentFile的点击事件
 */
sealed class FileSystemClickEvent : Event() {
    data class FileClick(val file: File) : FileSystemClickEvent()
    data class DocumentClick(val document: DocumentFile) : FileSystemClickEvent()
}

/**
 * 支持File和DocumentFile的长按事件
 */
sealed class FileSystemLongClickEvent : Event() {
    data class FileLongClick(val file: File) : FileSystemLongClickEvent()
    data class DocumentLongClick(val document: DocumentFile) : FileSystemLongClickEvent()
}
