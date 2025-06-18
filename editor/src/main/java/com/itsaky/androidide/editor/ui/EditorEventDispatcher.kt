package com.itsaky.androidide.editor.ui

import com.itsaky.androidide.eventbus.events.editor.DocumentChangeEvent
import com.itsaky.androidide.eventbus.events.editor.DocumentCloseEvent
import com.itsaky.androidide.eventbus.events.editor.DocumentEvent
import com.itsaky.androidide.eventbus.events.editor.DocumentOpenEvent
import com.itsaky.androidide.eventbus.events.editor.DocumentSaveEvent
import com.itsaky.androidide.eventbus.events.editor.DocumentSelectedEvent
import com.itsaky.androidide.projects.FileManager.onDocumentClose
import com.itsaky.androidide.projects.FileManager.onDocumentContentChange
import com.itsaky.androidide.projects.FileManager.onDocumentOpen
import com.itsaky.androidide.utils.ILogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

import org.greenrobot.eventbus.EventBus
import java.util.concurrent.CancellationException
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import java.lang.Runtime


/**
 * Dispatches events for the editor.
 *
 * @author Akash Yadav
 */
class EditorEventDispatcher(
    var editor: IDEEditor? = null
) {

    // 限制事件队列最大容量为200(建议100)，避免内存积压()
    private val eventQueue = LinkedBlockingQueue<DocumentEvent>(200)

    private var eventDispatcherJob: Job? = null

    companion object {
        private val log = ILogger.newInstance("EditorEventDispatcher")
    }

    fun init(scope: CoroutineScope) {

        val parallelism = Runtime.getRuntime().availableProcessors().coerceAtMost(8)
        eventDispatcherJob = scope.launch(Dispatchers.Default) { 
            while (isActive) {
                dispatchNextEvent()
            }
        }.also {
            it.invokeOnCompletion { error ->
                if (error != null && error !is CancellationException) {
                    log.error("Failed to dispatch editor events", error)
                }
            }
        }
    }

    fun dispatch(event: DocumentEvent) {
        check(eventQueue.offer(event)) {
            "Failed to dispatch event: $event"
        }
    }

    private suspend fun dispatchNextEvent() {

        val event = withContext(Dispatchers.IO) {
            eventQueue.poll(100, TimeUnit.MILLISECONDS)
        }
        if (event == null){
            return
        }

        if (editor?.isReleased != false) {
            return
        }

        when (event) {
            is DocumentOpenEvent -> dispatchOpen(event)
            is DocumentChangeEvent -> dispatchChange(event)
            is DocumentSaveEvent -> dispatchSave(event)
            is DocumentCloseEvent -> dispatchClose(event)
            is DocumentSelectedEvent -> dispatchSelected(event)
            else -> throw IllegalArgumentException("Unknown document event: $event")
        }
    }

    private fun dispatchOpen(event: DocumentOpenEvent) {
        onDocumentOpen(event)
        post(event)
    }

    private fun dispatchChange(event: DocumentChangeEvent) {
        onDocumentContentChange(event)
        post(event)
    }

    private fun dispatchSave(event: DocumentSaveEvent) {
        post(event)
    }

    private fun dispatchClose(event: DocumentCloseEvent) {
        onDocumentClose(event)
        post(event)
    }

    private fun dispatchSelected(event: DocumentSelectedEvent) {
        post(event)
    }

    private fun post(event: DocumentEvent) {
        EventBus.getDefault().post(event)
    }

    fun destroy() {
        editor = null
        eventDispatcherJob?.cancel(CancellationException("Cancellation requested"))
    }
}
