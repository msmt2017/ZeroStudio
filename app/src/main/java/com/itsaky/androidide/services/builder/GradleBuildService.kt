package com.itsaky.androidide.services.builder

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.text.TextUtils
import androidx.core.app.NotificationManagerCompat
import com.blankj.utilcode.util.ResourceUtils
import com.blankj.utilcode.util.ZipUtils
import com.itsaky.androidide.BuildConfig
import com.itsaky.androidide.R.*
import com.itsaky.androidide.app.BaseApplication
import com.itsaky.androidide.lookup.Lookup
import com.itsaky.androidide.managers.ToolsManager
import com.itsaky.androidide.preferences.internal.isBuildCacheEnabled
import com.itsaky.androidide.preferences.internal.isDebugEnabled
import com.itsaky.androidide.preferences.internal.isInfoEnabled
import com.itsaky.androidide.preferences.internal.isOfflineEnabled
import com.itsaky.androidide.preferences.internal.isScanEnabled
import com.itsaky.androidide.preferences.internal.isStacktraceEnabled
import com.itsaky.androidide.preferences.internal.isWarningModeAllEnabled
import com.itsaky.androidide.preferences.logsenderEnabled
import com.itsaky.androidide.projects.ProjectManagerImpl
import com.itsaky.androidide.projects.builder.BuildService
import com.itsaky.androidide.resources.R
import com.itsaky.androidide.services.ToolingServerNotStartedException
import com.itsaky.androidide.services.builder.ToolingServerRunner.OnServerStartListener
import com.itsaky.androidide.tasks.ifCancelledOrInterrupted
import com.itsaky.androidide.tasks.runOnUiThread
import com.itsaky.androidide.tooling.api.ForwardingToolingApiClient
import com.itsaky.androidide.tooling.api.IProject
import com.itsaky.androidide.tooling.api.IToolingApiClient
import com.itsaky.androidide.tooling.api.IToolingApiServer
import com.itsaky.androidide.tooling.api.LogSenderConfig.PROPERTY_LOGSENDER_ENABLED
import com.itsaky.androidide.tooling.api.messages.InitializeProjectParams
import com.itsaky.androidide.tooling.api.messages.LogMessageParams
import com.itsaky.androidide.tooling.api.messages.TaskExecutionMessage
import com.itsaky.androidide.tooling.api.messages.result.BuildCancellationRequestResult
import com.itsaky.androidide.tooling.api.messages.result.BuildInfo
import com.itsaky.androidide.tooling.api.messages.result.BuildResult
import com.itsaky.androidide.tooling.api.messages.result.GradleWrapperCheckResult
import com.itsaky.androidide.tooling.api.messages.result.InitializeResult
import com.itsaky.androidide.tooling.api.messages.result.TaskExecutionResult
import com.itsaky.androidide.tooling.api.messages.toLogLine
import com.itsaky.androidide.tooling.events.ProgressEvent
import com.itsaky.androidide.utils.Environment
import com.itsaky.androidide.utils.ILogger
import com.termux.shared.termux.shell.command.environment.TermuxShellEnvironment
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.util.Objects
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionException
import java.util.concurrent.ExecutorService // 导入 ExecutorService
import java.util.concurrent.Executors // 导入 Executors
import java.util.concurrent.TimeUnit

/**
 * A foreground service that handles interaction with the Gradle Tooling API.
 *fix：Fatal Exception: java.lang.OutOfMemoryError: Failed to allocate a 65552 byte allocation with 10736 free bytes and 10KB until OOM, target footprint 536870912, growth limit 536870912
*如上所示，在部分高压构建创建下会有分配线程和运存不合理情况导致阻塞 ，这需要多个维度的调配和异步及自动管理分配控制线程池和运存等避免阻塞和卡顿盒无响应，下面的修改只是一个简约方案，并非最终或者最好方案。
下面分配八个线程到池里，让运行构建更流程.建议合理根据自己设备来分配org.gradle.jvmargs的jvm运存量来避免 by android_zero(零丶) github：msmt2017/ZeroStudio
 * @author Akash Yadav
 */
class GradleBuildService : Service(), BuildService, IToolingApiClient,
  ToolingServerRunner.Observer {

  private var mBinder: GradleServiceBinder? = null
  private var isToolingServerStarted = false
  override var isBuildInProgress = false
    private set


  private var _toolingApiClient: ForwardingToolingApiClient? = null
  private var toolingServerRunner: ToolingServerRunner? = null
  private var outputReaderJob: Job? = null
  private var notificationManager: NotificationManager? = null
  private var server: IToolingApiServer? = null
  private var eventListener: EventListener? = null

  // 用于管理Gradle构建服务相关后台任务的协程作用域（例如，UI更新、轻量级逻辑）
  private val buildServiceScope = CoroutineScope(
    Dispatchers.Default + CoroutineName("GradleBuildService"))

  // 用于管理Gradle构建任务的线程池，以控制并发和资源分配。
  // 默认分配8个线程，允许并行执行构建步骤。
  // 这有助于防止线程饥饿并通过限制活跃任务来管理内存压力。
  private val buildThreadPool: ExecutorService = Executors.newFixedThreadPool(8)


  private val isGradleWrapperAvailable: Boolean
    get() {
      val projectManager = ProjectManagerImpl.getInstance()
      val projectDir = projectManager.projectDirPath
      if (TextUtils.isEmpty(projectDir)) {
        return false
      }

      val projectRoot = Objects.requireNonNull(projectManager.projectDir)
      if (!projectRoot.exists()) {
        return false
      }

      val gradlew = File(projectRoot, "gradlew")
      val gradleWrapperJar = File(projectRoot, "gradle/wrapper/gradle-wrapper.jar")
      val gradleWrapperProps = File(projectRoot, "gradle/wrapper/gradle-wrapper.properties")
      return gradlew.exists() && gradleWrapperJar.exists() && gradleWrapperProps.exists()
    }

  companion object {

    private val log = ILogger.newInstance("GradleBuildService")
    private val serverLogger = ILogger.newInstance("ToolingApiServer")
    private val NOTIFICATION_ID = R.string.app_name
    private val SERVER_System_err = ILogger.newInstance("ToolingApiErrorStream")
  }

  override fun onCreate() {
    notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
    showNotification(getString(R.string.build_status_idle), false)
    Lookup.getDefault().update(BuildService.KEY_BUILD_SERVICE, this)
  }

  override fun isToolingServerStarted(): Boolean {
    return isToolingServerStarted && server != null
  }

  private fun showNotification(message: String,
    @Suppress("SameParameterValue") isProgress: Boolean) {
    log.info("启动构建进程成功...")
    createNotificationChannels()
    startForeground(NOTIFICATION_ID, buildNotification(message, isProgress))
  }

  private fun createNotificationChannels() {
    val buildNotificationChannel = NotificationChannel(
      BaseApplication.NOTIFICATION_GRADLE_BUILD_SERVICE,
      getString(string.title_gradle_service_notification_channel),
      NotificationManager.IMPORTANCE_LOW)
    NotificationManagerCompat.from(this)
      .createNotificationChannel(buildNotificationChannel)
  }

  private fun buildNotification(message: String, isProgress: Boolean): Notification {
    val ticker = getString(R.string.title_gradle_service_notification_ticker)
    val title = getString(R.string.title_gradle_service_notification)
    val launch = packageManager.getLaunchIntentForPackage(BuildConfig.APPLICATION_ID)
    // FLAG_IMMUTABLE is required for Android 12+ for PendingIntents
    val intent = PendingIntent.getActivity(this, 0, launch, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    val builder = Notification.Builder(this, BaseApplication.NOTIFICATION_GRADLE_BUILD_SERVICE)
      .setSmallIcon(R.drawable.ic_launcher_notification).setTicker(ticker)
      .setWhen(System.currentTimeMillis()).setContentTitle(title).setContentText(message)
      .setContentIntent(intent)

    // Checking whether to add a ProgressBar to the notification
    if (isProgress) {
      // Add ProgressBar to Notification
      builder.setProgress(100, 0, true)
    }
    return builder.build()
  }

  override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
    // No point in restarting the service if it really gets killed.
    return START_NOT_STICKY
  }

  override fun onDestroy() {
    mBinder?.release()
    mBinder = null

    log.info("服务正在销毁。", "正在关闭显示的通知...")
    notificationManager!!.cancel(NOTIFICATION_ID)

    val lookup = Lookup.getDefault()
    lookup.unregister(BuildService.KEY_BUILD_SERVICE)
    lookup.unregister(BuildService.KEY_PROJECT_PROXY)

    server?.also { server ->
      try {
        log.info("正在关闭Tooling API服务器...")
        // 发送关闭请求，但不要等待服务器响应
        // 服务不应阻塞onDestroy调用，以避免超时
        // 工具服务器必须释放资源并自动退出
        server.shutdown().get(1, TimeUnit.SECONDS)
      } catch (e: Throwable) {
        log.error("关闭Tooling API服务器失败", e)
      }
    }

    log.debug("正在取消Tooling服务器运行器...")
    toolingServerRunner?.release()
    toolingServerRunner = null

    _toolingApiClient?.client = null
    _toolingApiClient = null

    log.debug("正在取消Tooling服务器输出读取器任务...")
    outputReaderJob?.cancel()
    outputReaderJob = null

    // 关闭自定义构建线程池
    log.debug("正在关闭构建线程池...")
    buildThreadPool.shutdownNow() // 立即尝试停止所有正在执行的任务并返回等待执行的任务列表。
    try {
        // 等待一小段时间让任务优雅终止
        if (!buildThreadPool.awaitTermination(5, TimeUnit.SECONDS)) {
            log.warn("构建线程池未能优雅终止。")
        }
    } catch (ie: InterruptedException) {
        // 如果在等待时当前线程被中断，则重新中断该线程
        Thread.currentThread().interrupt()
        log.error("等待构建线程池关闭时被中断。", ie)
    }


    isToolingServerStarted = false
  }

  override fun onBind(intent: Intent): IBinder? {
    if (mBinder == null) {
      mBinder = GradleServiceBinder(this)
    }
    return mBinder
  }

  override fun onListenerStarted(server: IToolingApiServer, projectProxy: IProject,
    errorStream: InputStream) {
    startServerOutputReader(errorStream)
    this.server = server
    Lookup.getDefault().update(BuildService.KEY_PROJECT_PROXY, projectProxy)
    isToolingServerStarted = true
  }

  /**
   * Called when the Tooling API server process has exited.
   * CRITICAL FIX: Ensures the service's internal state reflects the server's exit.
   * This is crucial for preventing the service from attempting to interact with a non-existent server.
   */
  override fun onServerExited(exitCode: Int) {
    log.warn("Tooling API进程终止，退出代码为:", exitCode)
    stopForeground(STOP_FOREGROUND_REMOVE)
    // CRITICAL FIX: Ensure the service's internal state reflects the server's exit
    this.isToolingServerStarted = false
    // Also, clear the server reference to prevent further calls to a dead server
    this.server = null
    // Update Lookup to reflect that project proxy is no longer available
    Lookup.getDefault().update(BuildService.KEY_PROJECT_PROXY, null)
    log.info("Tooling API服务器状态已重置为未启动。")
  }

  override fun getClient(): IToolingApiClient {
    if (_toolingApiClient == null) {
      _toolingApiClient = ForwardingToolingApiClient(this)
    }
    return _toolingApiClient!!
  }

  override fun logMessage(params: LogMessageParams) {
    val line = params.toLogLine()
    serverLogger.log(line.level, line.formattedTagAndMessage())
  }

  override fun logOutput(line: String) {
    eventListener?.onOutput(line)
  }

  override fun prepareBuild(buildInfo: BuildInfo) {
    updateNotification(getString(R.string.build_status_in_progress), true)
    eventListener?.prepareBuild(buildInfo)
  }

  override fun onBuildSuccessful(result: BuildResult) {
    updateNotification(getString(R.string.build_status_sucess), false)
    eventListener?.onBuildSuccessful(result.tasks)
  }

  override fun onBuildFailed(result: BuildResult) {
    updateNotification(getString(R.string.build_status_failed), false)
    eventListener?.onBuildFailed(result.tasks)
  }

  override fun onProgressEvent(event: ProgressEvent) {
    eventListener?.onProgressEvent(event)
  }

  override fun getBuildArguments(): CompletableFuture<List<String>> {
    val extraArgs = ArrayList<String>()
    extraArgs.add("--init-script")
    extraArgs.add(Environment.INIT_SCRIPT.absolutePath)

    // Override AAPT2 binary
    // The one downloaded from Maven is not built for Android
    extraArgs.add("-Pandroid.aapt2FromMavenOverride=" + Environment.AAPT2.absolutePath)
    extraArgs.add("-P${PROPERTY_LOGSENDER_ENABLED}=${logsenderEnabled}")
    if (isStacktraceEnabled) {
      extraArgs.add("--stacktrace")
    }
    if (isInfoEnabled) {
      extraArgs.add("--info")
    }
    if (isDebugEnabled) {
      extraArgs.add("--debug")
    }
    if (isScanEnabled) {
      extraArgs.add("--scan")
    }
    if (isWarningModeAllEnabled) {
      extraArgs.add("--warning-mode")
      extraArgs.add("all")
    }
    if (isBuildCacheEnabled) {
      extraArgs.add("--build-cache")
    }
    if (isOfflineEnabled) {
      extraArgs.add("--offline")
    }
    return CompletableFuture.completedFuture(extraArgs)
  }

  override fun checkGradleWrapperAvailability(): CompletableFuture<GradleWrapperCheckResult> {
    return if (isGradleWrapperAvailable) CompletableFuture.completedFuture(
      GradleWrapperCheckResult(true)) else installWrapper()
  }

  internal fun setServerListener(listener: OnServerStartListener?) {
    if (toolingServerRunner != null) {
      toolingServerRunner!!.setListener(listener)
    }
  }

  private fun installWrapper(): CompletableFuture<GradleWrapperCheckResult> {
    eventListener?.also { eventListener ->
      eventListener.onOutput("-------------------- 注意 --------------------")
      eventListener.onOutput(getString(R.string.msg_installing_gradlew))
      eventListener.onOutput("----------------------------------------------")
    }
    // 使用自定义的buildThreadPool来执行安装任务
    // Use the custom buildThreadPool to execute the installation task
    return CompletableFuture.supplyAsync({ doInstallWrapper() }, buildThreadPool)
  }

  private fun doInstallWrapper(): GradleWrapperCheckResult {
    val extracted = File(Environment.TMP_DIR, "gradle-wrapper.zip")
    if (!ResourceUtils.copyFileFromAssets(ToolsManager.getCommonAsset("gradle-wrapper.zip"),
        extracted.absolutePath)
    ) {
      log.error("无法从IDE资源中解压gradle-plugin.zip。")
      return GradleWrapperCheckResult(false)
    }
    try {
      val projectDir = ProjectManagerImpl.getInstance().projectDir
      val files = ZipUtils.unzipFile(extracted, projectDir)
      if (files != null && files.isNotEmpty()) {
        return GradleWrapperCheckResult(true)
      }
    } catch (e: IOException) {
      log.error("解压Gradle Wrapper时发生错误", e)
    }
    return GradleWrapperCheckResult(false)
  }

  private fun updateNotification(message: String, isProgress: Boolean) {
    runOnUiThread { doUpdateNotification(message, isProgress) }
  }

  private fun doUpdateNotification(message: String, isProgress: Boolean) {
    (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).notify(NOTIFICATION_ID,
      buildNotification(message, isProgress))
  }

  override fun initializeProject(
    params: InitializeProjectParams): CompletableFuture<InitializeResult> {
    checkServerStarted()
    Objects.requireNonNull(params)
    // 使用自定义的buildThreadPool来执行初始化任务
    // Use the custom buildThreadPool to execute the initialization task
    return performBuildTasks(server!!.initialize(params))
  }

  override fun executeTasks(vararg tasks: String): CompletableFuture<TaskExecutionResult> {
    checkServerStarted()
    val message = TaskExecutionMessage(listOf(*tasks))
    // 使用自定义的buildThreadPool来执行任务
    // Use the custom buildThreadPool to execute the task
    return performBuildTasks(server!!.executeTasks(message))
  }

  override fun cancelCurrentBuild(): CompletableFuture<BuildCancellationRequestResult> {
    checkServerStarted()
    // 取消操作也可能在buildThreadPool上异步执行，如果server!!.cancelCurrentBuild()是异步的
    // The cancellation operation might also execute asynchronously on the buildThreadPool if server!!.cancelCurrentBuild() is asynchronous
    return server!!.cancelCurrentBuild()
  }

  /**
   * Performs build-related tasks by first preparing the build environment,
   * then executing the actual build logic provided by 'future',
   * and finally marking the build as finished.
   * All these steps are orchestrated using the custom 'buildThreadPool'
   * to manage concurrency and resource allocation.
   */
  private fun <T> performBuildTasks(future: CompletableFuture<T>): CompletableFuture<T> {
    // 这里明确使用我们自定义的buildThreadPool进行异步操作，
    // 从而控制构建任务准备和处理中涉及的线程数量。
    // Explicitly use our custom buildThreadPool for asynchronous operations here,
    // to control the number of threads involved in preparing and processing build tasks.
    return CompletableFuture.runAsync(this::onPrepareBuildRequest, buildThreadPool)
        .handleAsync({ _, _ ->
            try {
                // 这个get()调用仍然会阻塞buildThreadPool中的当前线程。
                // This get() call will still block the current thread in the buildThreadPool.
                // This is intentional, as it waits for the result of the remote Tooling API operation.
                return@handleAsync future.get()
            } catch (e: Throwable) {
                // Wrap any exceptions in CompletionException to propagate them through the CompletableFuture chain.
                throw CompletionException(e)
            }
        }, buildThreadPool) // 使用相同的buildThreadPool处理结果并标记构建完成
        // Use the same buildThreadPool to handle the result and mark the build as finished
        .handle(this::markBuildAsFinished)
  }

  private fun onPrepareBuildRequest() {
    checkServerStarted()
    ensureTmpdir()
    if (isBuildInProgress) {
      logBuildInProgress()
      throw BuildInProgressException()
    }
    isBuildInProgress = true
  }

  @Throws(ToolingServerNotStartedException::class)
  private fun checkServerStarted() {
    if (!isToolingServerStarted()) {
      throw ToolingServerNotStartedException()
    }
  }

  private fun ensureTmpdir() {
    Environment.mkdirIfNotExits(Environment.TMP_DIR)
  }

  private fun logBuildInProgress() {
    log.warn("构建已经在进行中！")
  }

  @Suppress("UNUSED_PARAMETER")
  private fun <T> markBuildAsFinished(result: T, throwable: Throwable?): T {
    isBuildInProgress = false
    return result
  }

  /**
   * Starts the Tooling API server if it's not already running.
   * If the server is already started, it notifies the listener directly.
   * This function utilizes the ToolingServerRunner to manage the server process lifecycle.
   */
  internal fun startToolingServer(listener: OnServerStartListener?) {
    if (toolingServerRunner?.isStarted != true) {
      val envs = TermuxShellEnvironment().getEnvironment(this, false)
      toolingServerRunner = ToolingServerRunner(listener, this).also { it.startAsync(envs) }
      return
    }

    // If server is already started, immediately notify the listener
    if (toolingServerRunner!!.isStarted && listener != null) {
      listener.onServerStarted()
    } else {
      // If server is not started but toolingServerRunner exists, set the listener
      setServerListener(listener)
    }
  }

  fun setEventListener(eventListener: EventListener?): GradleBuildService {
    if (eventListener == null) {
      this.eventListener = null
      return this
    }
    this.eventListener = wrap(eventListener)
    return this
  }

  private fun wrap(listener: EventListener?): EventListener? {
    return if (listener == null) {
      null
    } else object : EventListener {
      override fun prepareBuild(buildInfo: BuildInfo) {
        runOnUiThread { listener.prepareBuild(buildInfo) }
      }

      override fun onBuildSuccessful(tasks: List<String?>) {
        runOnUiThread { listener.onBuildSuccessful(tasks) }
      }

      override fun onProgressEvent(event: ProgressEvent) {
        runOnUiThread { listener.onProgressEvent(event) }
      }

      override fun onBuildFailed(tasks: List<String?>) {
        runOnUiThread { listener.onBuildFailed(tasks) }
      }

      override fun onOutput(line: String?) {
        runOnUiThread { listener.onOutput(line) }
      }
    }
  }

  /**
   * Starts a coroutine to read the error stream from the Tooling API server.
   * This is crucial to prevent the error stream buffer from filling up and blocking the server process.
   */
  private fun startServerOutputReader(input: InputStream) {
    if (outputReaderJob?.isActive == true) {
      return
    }

    outputReaderJob = buildServiceScope.launch(
      Dispatchers.IO + CoroutineName("ToolingServerErrorReader")) {
      val reader = input.bufferedReader()
      try {
        reader.forEachLine { line ->
          SERVER_System_err.error(line)
        }
      } catch (e: Throwable) {
        e.ifCancelledOrInterrupted(suppress = true) {
          // The exception will be suppressed if it's a CancellationException or InterruptedException,
          // which is expected during graceful shutdown.
          return@launch
        }

        // Log other unexpected errors silently to avoid crashing the reader job.
        log.error("Failed to read tooling server output", e)
      }
    }
  }

  /**
   * Handles events received from a Gradle build.
   */
  interface EventListener {

    /**
     * Called just before a build is started.
     *
     * @param buildInfo The information about the build to be executed.
     * @see IToolingApiClient.prepareBuild
     */
    fun prepareBuild(buildInfo: BuildInfo)

    /**
     * Called when a build is successful.
     *
     * @param tasks The tasks that were run.
     * @see IToolingApiClient.onBuildSuccessful
     */
    fun onBuildSuccessful(tasks: List<String?>)

    /**
     * Called when a progress event is received from the Tooling API server.
     *
     * @param event The event model describing the event.
     */
    fun onProgressEvent(event: ProgressEvent)

    /**
     * Called when a build fails.
     *
     * @param tasks The tasks that were run.
     * @see IToolingApiClient.onBuildFailed
     */
    fun onBuildFailed(tasks: List<String?>)

    /**
     * Called when the output line is received.
     *
     * @param line The line of the build output.
     */
    fun onOutput(line: String?)
  }
}
