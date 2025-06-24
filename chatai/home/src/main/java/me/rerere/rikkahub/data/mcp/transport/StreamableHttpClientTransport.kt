package me.rerere.rikkahub.data.mcp.transport

import android.util.Log
import io.modelcontextprotocol.kotlin.sdk.JSONRPCMessage
import io.modelcontextprotocol.kotlin.sdk.shared.AbstractTransport
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import me.rerere.ai.util.await
import me.rerere.rikkahub.data.mcp.McpJson
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.sse.EventSources
import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.concurrent.atomics.ExperimentalAtomicApi

private const val TAG = "StreamableHttpClientTransport"

@OptIn(ExperimentalAtomicApi::class)
internal class StreamableHttpClientTransport(
  private val client: OkHttpClient,
  private val urlString: String,
  private val headers: List<Pair<String, String>>,
) : AbstractTransport() {
  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
  private val eventSourceFactory = EventSources.createFactory(client)
  private val initialized: AtomicBoolean = AtomicBoolean(false)
  private var sessionId = ""

  private var job: Job? = null

  override suspend fun start() {
    if (!initialized.compareAndSet(false, true)) {
      error(
        "SSEClientTransport already started! " +
          "If using Client class, note that connect() calls start() automatically.",
      )
    }
  }

  @OptIn(ExperimentalCoroutinesApi::class)
  override suspend fun send(message: JSONRPCMessage) {
    try {
      Log.i(TAG, "send: ${McpJson.encodeToString(message)}")
      val request = Request.Builder()
        .url(urlString)
        .apply {
          for ((key, value) in headers) {
            addHeader(key, value)
          }
          addHeader("Accept", "application/json")
          if (sessionId.isNotEmpty()) {
            addHeader("Mcp-Session-Id", sessionId)
            Log.i(TAG, "send: append $sessionId")
          }
        }
        .post(
          McpJson.encodeToString(message).toRequestBody(
            contentType = "application/json".toMediaType(),
          )
        )
        .build()
      val response = client.newCall(request).await()

      if (!response.isSuccessful) {
        val text = response.body?.string()
        error("Error POSTing to endpoint $urlString (HTTP ${response.code}): $text")
      }

      // handle session id
      if (sessionId.isEmpty()) sessionId = response.headers["Mcp-Session-Id"] ?: ""

      val content = response.body?.string()?.trim() ?: ""
      if (content == "null") {
        return
      }
      val message = McpJson.decodeFromString<JSONRPCMessage>(content)
      _onMessage(message)
    } catch (e: Exception) {
      _onError(e)
      throw e
    }
  }

  override suspend fun close() {
    if (!initialized.load()) {
      error("SSEClientTransport is not initialized!")
    }
    _onClose()
    job?.cancelAndJoin()
  }
}