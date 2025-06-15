// File: android/zero/mcp/McpServer.kt
package android.zero.mcp

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.sse.*
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

/**
 * [McpServer] runs the Ktor HTTP server as an Android Foreground Service.
 * This ensures the server can operate persistently in the background,
 * even when the application's UI is not active.
 *
 * It exposes:
 * - A `/command` endpoint for receiving AI commands (HTTP POST).
 * - An `/events` endpoint for streaming real-time logs and command responses (Server-Sent Events).
 *
 * All logs are directed to [LogManager] for display in the [McpServerLogFragment].
 *
 * @author Android Zero
 */
class McpServer : Service() {

    private var embeddedServer: NettyApplicationEngine? = null
    private lateinit var mcpService: McpService

    private val SERVER_PORT = 8080
    private val SERVER_HOST = "127.0.0.1" // Localhost for internal network communication

    private val CHANNEL_ID = "McpServerChannel"
    private val NOTIFICATION_ID = 1

    override fun onCreate() {
        super.onCreate()
        LogManager.addLog("McpServer: Service onCreate called.", "INFO", "McpServer")

        // Get the McpService instance from the application
        mcpService = (application as? McpApplication)?.getMcpService()
            ?: throw IllegalStateException("McpService not initialized in McpApplication!")

        createNotificationChannel()
        startForeground(NOTIFICATION_ID, createNotification())

        startKtorServer()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        LogManager.addLog("McpServer: Service onStartCommand called.", "INFO", "McpServer")
        return START_STICKY // Ensures the service restarts if killed by the system
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null // Not providing a Binder for this service
    }

    override fun onDestroy() {
        super.onDestroy()
        LogManager.addLog("McpServer: Service onDestroy called. Stopping Ktor server...", "INFO", "McpServer")
        stopKtorServer()
        mcpService.destroy() // Clean up McpService resources
        LogManager.addLog("McpServer: Service onDestroy complete.", "INFO", "McpServer")
    }

    /**
     * Creates a notification channel for the foreground service.
     * Required for Android 8.0 (Oreo) and above.
     */
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                CHANNEL_ID,
                "MCP Server Notifications",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(serviceChannel)
        }
    }

    /**
     * Creates the notification displayed for the foreground service.
     * This notification informs the user that the MCP server is running.
     */
    private fun createNotification(): Notification {
        val notificationIntent = Intent(this, MainActivity::class.java) // Assuming MainActivity is your main entry point
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            notificationIntent,
            PendingIntent.FLAG_IMMUTABLE // Use FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("MCP Server Running")
            .setContentText("Listening on $SERVER_HOST:$SERVER_PORT")
            .setSmallIcon(android.R.drawable.ic_dialog_info) // Generic icon
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    /**
     * Starts the Ktor embedded Netty server.
     * Configures routes for command reception and SSE event streaming.
     */
    private fun startKtorServer() {
        if (embeddedServer != null) {
            LogManager.addLog("KtorServer: Server already running.", "WARN", "McpServer")
            return
        }

        embeddedServer = embeddedServer(Netty, host = SERVER_HOST, port = SERVER_PORT) {
            install(ContentNegotiation) {
                json(Json = JsonAdapters.defaultJson)
            }
            install(io.ktor.server.websocket.WebSockets) // Install WebSockets for SSE as it's often used for SSE setup
            install(SSE) // Install SSE plugin

            routing {
                // Endpoint to receive commands from AI client
                post("/command") {
                    try {
                        val commandJson = call.receiveText()
                        mcpService.dispatchCommand(commandJson)
                        call.respondText("Command received", ContentType.Text.Plain, HttpStatusCode.OK)
                        LogManager.addLog("KtorServer: Command POST success.", "DEBUG", "McpServer")
                    } catch (e: Exception) {
                        LogManager.addLog("KtorServer: Error receiving command: ${e.message}", "ERROR", "McpServer")
                        call.respondText("Error: ${e.message}", ContentType.Text.Plain, HttpStatusCode.InternalServerError)
                    }
                }

                // Endpoint to stream events (logs, responses) to AI client via SSE
                sse("/events") {
                    LogManager.addLog("KtorServer: SSE client connected.", "INFO", "McpServer")
                    try {
                        // Collect all responses from McpService and send them as SSE events
                        mcpService.responses.collect { responseJson ->
                            if (coroutineContext.isActive) {
                                send(SseEvent(data = responseJson))
                                LogManager.addLog("KtorServer: SSE event sent: ${responseJson.take(100)}...", "VERBOSE", "McpServer")
                            } else {
                                LogManager.addLog("KtorServer: SSE connection inactive, stopping sending events.", "INFO", "McpServer")
                                return@collect
                            }
                        }
                    } catch (e: Exception) {
                        LogManager.addLog("KtorServer: SSE client error: ${e.message}", "ERROR", "McpServer")
                    } finally {
                        LogManager.addLog("KtorServer: SSE client disconnected.", "INFO", "McpServer")
                    }
                }

                get("/") {
                    call.respondText("MCP Server is running! Use /command for POST and /events for SSE.", ContentType.Text.Plain, HttpStatusCode.OK)
                }
            }
        }.start(wait = false) // Start non-blocking
        LogManager.addLog("KtorServer: Server started on $SERVER_HOST:$SERVER_PORT", "INFO", "McpServer")
    }

    /**
     * Stops the Ktor embedded server.
     */
    private fun stopKtorServer() {
        embeddedServer?.stop(1, 5, TimeUnit.SECONDS) // Graceful shutdown
        embeddedServer = null
        LogManager.addLog("KtorServer: Server stopped.", "INFO", "McpServer")
    }
}
