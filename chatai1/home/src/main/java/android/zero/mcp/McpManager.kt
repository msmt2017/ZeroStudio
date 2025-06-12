// 文件路径：app/src/main/java/android/zero/mcp/McpManager.kt
package android.zero.mcp

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okhttp3.sse.EventSource
import okhttp3.sse.EventSourceListener
import okhttp3.sse.EventSources
import java.util.concurrent.ConcurrentHashMap

/**
 * 统一管理所有 MCP 配置的启动 / 停止，并向 UI 提供实时状态流。
 */
class McpManager(
    private val okHttp: OkHttpClient,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO),
) {

    /** 各配置的状态 MapFlow */
    private val _status = MutableStateFlow<Map<String, McpStatus>>(emptyMap())
    val status: StateFlow<Map<String, McpStatus>> = _status

    /** 保存正在运行的协程 Job 或本地 Server 对象 */
    private val jobs = ConcurrentHashMap<String, Job>()

    /** 同步全部配置（新增 / 修改 / 删除） */
    fun syncAll(configs: List<McpServerConfig>) {
        // 先停掉已删除或取消启用的
        jobs.keys.forEach { id ->
            val cfg = configs.find { it.commonOptions.id == id }
            if (cfg == null || !cfg.commonOptions.enable) stop(cfg?.commonOptions?.id ?: id)
        }
        // 再启动新的
        configs.filter { it.commonOptions.enable }.forEach { start(it) }
    }

    /** 查询单个配置状态 Flow */
    fun getStatusFlow(cfg: McpServerConfig): StateFlow<McpStatus> =
        status.map { it[cfg.commonOptions.id] ?: McpStatus.Idle }
            .stateIn(scope, SharingStarted.Eagerly, McpStatus.Idle)

    /** ------- 内部实现 ------- */

    private fun start(cfg: McpServerConfig) {
        val id = cfg.commonOptions.id
        if (jobs.containsKey(id)) return          // 已经在跑

        update(id, McpStatus.Connecting)

        val job = scope.launch {
            when (cfg) {
                is McpServerConfig.SseTransportServer ->
                    runSseClient(id, cfg.url, cfg.commonOptions.headers)

                is McpServerConfig.WebSocketServer ->
                    runWsClient(id, cfg.url, cfg.commonOptions.headers)
            }
        }
        jobs[id] = job
    }

    private fun stop(id: String) {
        jobs.remove(id)?.cancel()
        update(id, McpStatus.Idle)
    }

    /** SSE 客户端 */
    private suspend fun runSseClient(id: String, url: String, headers: List<Pair<String, String>>) {
        try {
            val reqBuilder = Request.Builder().url(url)
            headers.forEach { (k, v) -> reqBuilder.addHeader(k, v) }
            EventSources.createFactory(okHttp).newEventSource(
                reqBuilder.build(),
                object : EventSourceListener() {
                    override fun onOpen(es: EventSource, resp: okhttp3.Response) =
                        update(id, McpStatus.Connected)

                    override fun onClosed(es: EventSource) =
                        update(id, McpStatus.Idle)

                    override fun onFailure(
                        es: EventSource,
                        t: Throwable?,
                        resp: okhttp3.Response?,
                    ) = update(id, McpStatus.Error(t?.message ?: "SSE error"))
                }
            )
            // 阻塞保持连接
            awaitCancellation()
        } catch (e: Exception) {
            update(id, McpStatus.Error(e.message ?: "SSE failure"))
        }
    }

    /** WebSocket 客户端（占位示例） */
    private suspend fun runWsClient(id: String, url: String, headers: List<Pair<String, String>>) {
        try {
            val reqBuilder = Request.Builder().url(url)
            headers.forEach { (k, v) -> reqBuilder.addHeader(k, v) }
            okHttp.newWebSocket(
                reqBuilder.build(),
                object : WebSocketListener() {
                    override fun onOpen(ws: WebSocket, resp: okhttp3.Response) =
                        update(id, McpStatus.Connected)

                    override fun onClosing(ws: WebSocket, code: Int, reason: String) {
                        ws.close(code, reason)
                        update(id, McpStatus.Idle)
                    }

                    override fun onFailure(ws: WebSocket, t: Throwable, resp: okhttp3.Response?) =
                        update(id, McpStatus.Error(t.message ?: "WS error"))
                }
            )
            awaitCancellation()
        } catch (e: Exception) {
            update(id, McpStatus.Error(e.message ?: "WS failure"))
        }
    }

    private fun update(id: String, s: McpStatus) {
        _status.update { it.toMutableMap().apply { this[id] = s } }
    }
}
