package com.itsaky.androidide.services.builder

import com.itsaky.androidide.shell.executeProcessAsync
import com.itsaky.androidide.tasks.cancelIfActive
import com.itsaky.androidide.tasks.ifCancelledOrInterrupted
import com.itsaky.androidide.tooling.api.IProject
import com.itsaky.androidide.tooling.api.IToolingApiClient
import com.itsaky.androidide.tooling.api.IToolingApiServer
import com.itsaky.androidide.tooling.api.util.ToolingApiLauncher
import com.itsaky.androidide.utils.Environment
import com.itsaky.androidide.utils.ILogger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex // 导入 Mutex
import kotlinx.coroutines.sync.withLock // 导入 withLock
import java.io.InputStream
import java.io.BufferedReader // 导入 BufferedReader
import java.io.InputStreamReader // 导入 InputStreamReader
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Runner thread for the Tooling API.
 * Manages the lifecycle of the external Tooling API server process, including its startup,
 * communication, and shutdown. Designed to handle asynchronous operations and manage resources
 * efficiently in potentially high-pressure scenarios using Kotlin Coroutines.
 *
 * @author Akash Yadav
 */
internal class ToolingServerRunner(
    private var listener: OnServerStartListener?,
    private var observer: Observer?,
) {

    // Job for the overall server running process. Used for cancellation.
    private var _job: Job? = null
    // AtomicBoolean for thread-safe check of server start status.
    private var _isStarted = AtomicBoolean(false)

    var isStarted: Boolean
        get() = _isStarted.get()
        private set(value) {
            _isStarted.set(value)
        }

    // CoroutineScope for managing background tasks within this runner.
    // Uses Dispatchers.IO, which is suitable for blocking I/O operations and
    // is backed by a pool of threads that grows and shrinks as needed.
    private val runnerScope = CoroutineScope(Dispatchers.IO + CoroutineName("ToolingServerRunner"))

    // Mutex to prevent multiple concurrent calls to startAsync.
    // This ensures that the server startup logic is executed as a single, atomic operation,
    // which is crucial in high-pressure scenarios to avoid race conditions and resource contention.
    private val startMutex = Mutex()

    companion object {
        private val log = ILogger.newInstance("ToolingServerRunner")
        // Define SERVER_System_err here, specifically for ToolingServerRunner's error output
        private val SERVER_System_err = ILogger.newInstance("ToolingApiErrorStream_Runner")
    }

    /**
     * Sets the listener for server start events.
     * @param listener The listener to set.
     */
    fun setListener(listener: OnServerStartListener?) {
        this.listener = listener
    }

    /**
     * Starts the Tooling API server asynchronously.
     * This function is made suspend to properly manage coroutine lifecycle and avoid blocking
     * the calling thread. It uses a Mutex to ensure only one instance of the server is started
     * at a time, which is important for stability in high-pressure scenarios.
     *
     * @param envs Environment variables for the process.
     */
    fun startAsync(envs: Map<String, String>) = runnerScope.launch {
        // Acquire mutex to ensure only one start operation runs at a time.
        // If another startAsync call comes in while this one is running, it will suspend until the mutex is released.
        startMutex.withLock {
            if (isStarted) {
                log.info("Tooling API server is already started.")
                return@launch
            }

            var process: Process? = null
            try {
                log.info("Starting tooling API server...")
                val command = listOf(
                    Environment.JAVA.absolutePath, // The 'java' binary executable
                    // Allow reflective access to private members of classes in the following
                    // packages:
                    // - java.lang
                    // - java.io
                    // - java.util
                    //
                    // If any of the model classes in 'tooling-api-model' module send/receive
                    // objects from the JDK, their package name must be declared here with
                    // '--add-opens' to prevent InaccessibleObjectException.
                    // For example, some of the model classes has members of type java.io.File.
                    // When sending/receiving these type of objects using LSP4J, members of
                    // these objects are reflectively accessed by Gson. If we do no specify
                    // '--add-opens' for 'java.io' (for java.io.File) package, JVM will throw an
                    // InaccessibleObjectException.
                    "--add-opens", "java.base/java.lang=ALL-UNNAMED",
                    "--add-opens", "java.base/java.util=ALL-UNNAMED",
                    "--add-opens", "java.base/java.io=ALL-UNNAMED", // The JAR file to run
                    "-jar", Environment.TOOLING_API_JAR.absolutePath
                )

                // Execute the process asynchronously, capturing the process object.
                process = executeProcessAsync {
                    this.command = command
                    this.redirectErrorStream = false // Error stream is used for server logs.
                    this.workingDirectory = null // HOME
                    this.environment = envs
                }

                val inputStream = process.inputStream
                val outputStream = process.outputStream
                val errorStream = process.errorStream

                // Launch a coroutine to wait for the process to exit and clean up.
                val processMonitorJob = launch(Dispatchers.IO) {
                    try {
                        process?.waitFor() // Blocks until the process terminates.
                        log.info("Tooling API process exited with code : ${process?.exitValue() ?: "<unknown>"}")
                    } finally {
                        log.info("Destroying Tooling API process...")
                        process?.destroyForcibly() // Ensure process is destroyed on exit or cancellation.
                    }
                }

                // Launch a coroutine to read from the error stream and log it.
                // This is crucial to prevent the error stream buffer from filling up and blocking the process,
                // which could lead to perceived "blocking" or memory issues.
                val errorStreamReaderJob = launch(Dispatchers.IO) {
                    // Use useLines to ensure the BufferedReader is closed automatically.
                    BufferedReader(InputStreamReader(errorStream)).useLines { lines ->
                        lines.forEach { line ->
                            SERVER_System_err.error(line) // Log server errors using its own dedicated logger.
                        }
                    }
                }

                val launcher = ToolingApiLauncher.newClientLauncher(
                    observer!!.getClient(),
                    inputStream,
                    outputStream
                )

                // Start listening for messages from the tooling server.
                val future = launcher.startListening()

                // Notify the observer that the listener has started.
                observer?.onListenerStarted(
                    server = launcher.remoteProxy as IToolingApiServer,
                    projectProxy = launcher.remoteProxy as IProject,
                    errorStream = errorStream // Pass the error stream for external logging if needed.
                )

                isStarted = true // Mark server as started.

                // Notify the listener that the server has successfully started.
                listener?.onServerStarted()

                // Release the listener reference to prevent memory leaks (activity/fragment context).
                listener = null

                // Wait for the server communication to complete or be cancelled.
                val serverJob = launch(Dispatchers.IO) {
                    try {
                        // This blocks until the LSP4J connection is terminated.
                        future.get()
                        log.info("Tooling API server communication ended normally.")
                    } catch (err: Throwable) {
                        // Handle cancellation gracefully.
                        err.ifCancelledOrInterrupted {
                            log.info("ToolingServerRunner server job has been cancelled or interrupted.")
                        }

                        // Rethrow other unexpected errors.
                        if (err !is CancellationException) {
                            log.error("Tooling API server communication error", err)
                        }
                    } finally {
                        // Ensure resources are cleaned up if this job finishes before processMonitorJob.
                        process?.destroyForcibly()
                    }
                }

                // Wait for all related background jobs (process monitor, error stream reader, server communication) to complete.
                // This ensures all resources are properly managed before startAsync exits.
                joinAll(processMonitorJob, errorStreamReaderJob, serverJob)

            } catch (e: Throwable) {
                // Log and handle exceptions during server startup, excluding CancellationException
                // (which is expected during deliberate shutdown).
                if (e !is CancellationException) {
                    log.error("Unable to start tooling API server", e)
                }
                // Ensure process is destroyed if an error occurs during startup.
                process?.destroyForcibly()
            } finally {
                // Ensure the isStarted flag is reset if startup fails or the process exits.
                // Check if the main job is no longer active OR if the server wasn't started successfully.
                if (_job?.isActive == false || !isStarted) {
                    isStarted = false
                    // Notify observer about server exit if it was previously started or failed to start.
                    observer?.onServerExited(process?.exitValue() ?: -1)
                }
                // Release the mutex.
            }
        }
    }.also {
        _job = it // Keep a reference to the main job for cancellation.
    }

    /**
     * Releases resources and cancels all running coroutines managed by this runner.
     * This is crucial for proper shutdown and preventing resource leaks.
     */
    fun release() {
        log.info("Releasing ToolingServerRunner resources...")
        this.listener = null // Clear listener reference.
        this.observer = null // Clear observer reference.
        // Cancel the main job (_job) and its children.
        // This propagates cancellation to all coroutines launched within startAsync.
        this._job?.cancel(CancellationException("ToolingServerRunner release requested."))
        // Cancel the entire runnerScope, which will cancel all its children coroutines
        // that might still be running (e.g., if startAsync was called multiple times without waiting for it to finish).
        this.runnerScope.cancelIfActive("ToolingServerRunner scope cancelled during release.")
        this.isStarted = false // Reset the started flag.
    }

    /**
     * Observer interface for monitoring the Tooling API server's lifecycle.
     */
    interface Observer {

        /**
         * Called when the tooling API server's listener has started.
         * Provides the server proxy, project proxy, and error stream.
         */
        fun onListenerStarted(
            server: IToolingApiServer,
            projectProxy: IProject,
            errorStream: InputStream,
        )

        /**
         * Called when the tooling API server process has exited.
         * @param exitCode The exit code of the process.
         */
        fun onServerExited(exitCode: Int)

        /**
         * Provides the client instance for the tooling API.
         */
        fun getClient(): IToolingApiClient
    }

    /** Callback to listen for Tooling API server start event. */
    fun interface OnServerStartListener {

        /** Called when the tooling API server has been successfully started. */
        fun onServerStarted()
    }
}
