# AndroidIDE MCP 服务系统

## 概述

这是一个基于 Model Context Protocol (MCP) 的服务系统，为 AndroidIDE 提供 AI 助手功能。系统包含完整的 MCP 服务器、客户端和服务管理组件，支持通过 SSE 协议进行实时通信。

## 系统架构

```
┌─────────────────┐    ┌─────────────────┐    ┌─────────────────┐
│   RikkaHub      │    │   MCP Client    │    │   MCP Server    │
│   (UI Layer)    │◄──►│   (HTTP/SSE)    │◄──►│   (Ktor)        │
└─────────────────┘    └─────────────────┘    └─────────────────┘
                              │                        │
                              ▼                        ▼
                       ┌─────────────────┐    ┌─────────────────┐
                       │ Service Manager │    │ Tool Registry   │
                       │ (Lifecycle)     │    │ (TabFile, etc.) │
                       └─────────────────┘    └─────────────────┘
```

## 核心组件

### 1. MCP 服务器 (McpServer.kt)
- 核心 MCP 协议实现
- 工具注册和管理
- 请求处理和响应
- 事件通知系统

### 2. Ktor 服务器 (KtorMcpServer.kt)
- HTTP 和 SSE 接口
- RESTful API 端点
- 实时事件推送
- CORS 支持

### 3. 服务管理器 (McpServiceManager.kt)
- 服务生命周期管理
- 客户端连接管理
- 状态监控
- 错误处理

### 4. MCP 客户端 (McpClient.kt)
- HTTP 客户端
- SSE 事件监听
- 请求发送
- 连接管理

## 安装和配置

### 1. 依赖项

在 `build.gradle.kts` 中添加以下依赖：

```kotlin
dependencies {
    // Ktor 服务器
    implementation("io.ktor:ktor-server-core:2.3.7")
    implementation("io.ktor:ktor-server-netty:2.3.7")
    implementation("io.ktor:ktor-server-content-negotiation:2.3.7")
    implementation("io.ktor:ktor-server-cors:2.3.7")
    implementation("io.ktor:ktor-server-sse:2.3.7")
    
    // Ktor 客户端
    implementation("io.ktor:ktor-client-core:2.3.7")
    implementation("io.ktor:ktor-client-okhttp:2.3.7")
    implementation("io.ktor:ktor-client-content-negotiation:2.3.7")
    implementation("io.ktor:ktor-client-sse:2.3.7")
    
    // 序列化
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.0")
    
    // 协程
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
}
```

### 2. 权限配置

在 `AndroidManifest.xml` 中添加：

```xml
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />

<service
    android:name="android.zero.mcp.services.KtorMcpService"
    android:enabled="true"
    android:exported="false"
    android:foregroundServiceType="dataSync" />
```

### 3. 初始化

在 Application 类中初始化：

```kotlin
class MyApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        
        // 初始化 MCP 系统
        val mcpServer = McpServer.getInstance()
        McpInitManager.initialize(this, mcpServer)
    }
    
    override fun onTerminate() {
        super.onTerminate()
        McpInitManager.cleanup(this)
    }
}
```

## 使用方法

### 1. 启动 MCP 服务

```kotlin
val serviceManager = McpServiceManager.getInstance(context)

// 启动服务
serviceManager.startMcpService()

// 连接到服务器
serviceManager.connectToServer("http://localhost:8080")
```

### 2. 使用 TabFile 工具

```kotlin
// 获取当前活动标签页文件信息
val request = McpRequest(
    id = "req_1",
    method = "TabFile.getFile"
)
val response = client.sendRequest(request)

// 获取指定行内容
val request = McpRequest(
    id = "req_2",
    method = "TabFile.getLine",
    params = buildJsonObject {
        put("lineRange", "5-10")
    }
)
val response = client.sendRequest(request)

// 上传文件到AI窗口
val request = McpRequest(
    id = "req_3",
    method = "TabFile.uploadFileToAI",
    params = buildJsonObject {
        put("autoSend", true)
    }
)
val response = client.sendRequest(request)

// 获取编辑器所有内容
val request = McpRequest(
    id = "req_4",
    method = "TabFile.getAllEditorContent",
    params = buildJsonObject {
        put("includeFileInfo", true)
    }
)
val response = client.sendRequest(request)
```

### 3. AI 集成功能

#### 3.1 文件上传到AI窗口

```kotlin
// 通过MCP工具上传
val request = McpRequest(
    id = "req_upload",
    method = "TabFile.uploadFileToAI"
)
val response = client.sendRequest(request)

// 直接使用服务
RikkahubAIIntegrationService.addFileContent(
    fileName = "MainActivity.kt",
    filePath = "/path/to/file",
    content = "文件内容...",
    autoSend = true
)
```

#### 3.2 在AI窗口中使用内容管理器

```kotlin
// 在Compose中使用
AIContentManager(
    onContentSelected = { contentItem ->
        // 处理选中的内容
        println("选中内容: ${contentItem.getFormattedContent()}")
    },
    onContentRemoved = { contentId ->
        // 移除内容
        RikkahubAIIntegrationService.removeContent(contentId)
    }
)
```

### 4. 监听事件

```kotlin
serviceManager.connectToEvents()?.collect { notification ->
    when (notification.method) {
        "file.changed" -> {
            // 处理文件变更事件
        }
        "cursor.moved" -> {
            // 处理光标移动事件
        }
    }
}
```

## API 端点

### HTTP 端点

- `GET /health` - 健康检查
- `POST /mcp` - MCP 请求处理
- `GET /tools` - 获取工具列表
- `GET /info` - 获取服务器信息

### SSE 端点

- `GET /events` - 事件流

## 工具列表

### TabFile 工具

| 工具名 | 描述 | 参数 |
|--------|------|------|
| `TabFile.getCursor` | 获取光标位置 | 无 |
| `TabFile.getFile` | 获取当前活动标签页文件内容 | 无 |
| `TabFile.getLine` | 获取指定行内容 | `lineRange` |
| `TabFile.searchTabFile` | 搜索文件内容 | `searchContent` |
| `TabFile.getFunction` | 获取函数内容 | `functionName` |
| `TabFile.insertLine` | 插入行 | `line`, `content` |
| `TabFile.replaceLine` | 替换行 | `line`, `content` |
| `TabFile.deleteLine` | 删除行 | `line` |
| `TabFile.getCurrentFile` | 获取当前文件信息 | 无 |
| `TabFile.uploadFileToAI` | 上传文件到AI窗口 | `autoSend` (可选) |
| `TabFile.getAllEditorContent` | 获取编辑器所有内容 | `includeFileInfo` (可选) |
| `TabFile.getActiveTabInfo` | 获取活动标签页信息 | 无 |

### File 操作工具

| 工具名 | 描述 | 参数 | 指令示例 |
|--------|------|------|----------|
| `File.WriteFile` | 写入文件内容 | `path`, `content`, `writeLine` (可选) | `@File:WriteFile:#path=app/src/main/java/MainActivity.kt,#content=public class MainActivity,#writeLine=5` |
| `File.Rename` | 重命名文件或文件夹 | `DestinationPath`, `RenameContent` | `@File:Rename:#DestinationPath=app/src/main/java/OldName.kt,#RenameContent=NewName.kt` |
| `File.move` | 移动文件或文件夹 | `movePath`, `DestinationPath` | `@File:move:#movePath=app/src/main/java/oldpackage,#DestinationPath=app/src/main/java/newpackage` |
| `File.copy` | 复制文件或文件夹 | `copyPath`, `DestinationPath` | `@File:copy:#copyPath=app/src/main/java/MyClass.kt,#DestinationPath=app/src/main/java/backup/` |
| `File.delete` | 删除文件或文件夹 | `path` | `@File:delete:#path=app/src/main/java/OldFile.kt` |
| `File.search` | 搜索文件内容 | `path`, `content` | `@File:search:#path=app/src/main/java,#content=MainActivity` |
| `File.create` | 创建文件或文件夹 | `path`, `folder`, `files` (可选) | `@File:create:#path=app/src/main/java,#folder=false,#files=NewClass.kt` |
| `File.info` | 获取文件或文件夹信息 | `path` | `@File:info:#path=app/src/main/java/MainActivity.kt` |
| `File.Upload` | 上传文件给AI | `Files` 或 `getFolder` | `@File:Upload:#Files=app/src/main/java/MainActivity.kt` |

### File:workspace 工作区工具

| 工具名 | 描述 | 参数 | 指令示例 |
|--------|------|------|----------|
| `File.workspace.getmoduleInfo` | 获取工作区模块信息 | 无 | `@File:workspace:getmoduleInfo` |
| `File.workspace.getGradleWrapperInfo` | 获取Gradle Wrapper信息 | 无 | `@File:workspace:getGradleWrapperInfo` |
| `File.workspace.getinstallApk` | 安装APK文件 | `variant` | `@File:workspace:getinstallApk=debug` |
| `File.workspace.ModifyGradleVersion` | 修改Gradle版本 | `version` | `@File:workspace:ModifyGradleVersion=8.5,all` |
| `File.workspace.GetModuleSrcFileList` | 获取模块源码文件列表 | `moduleName` | `@File:workspace:GetModuleSrcFileList=app` |

### 文件操作工具

| 工具名 | 描述 | 参数 |
|--------|------|------|
| `File.WriteFile` | 写入文件 | `path`, `content` |
| `File.Rename` | 重命名文件 | `DestinationPath`, `RenameContent` |
| `File.move` | 移动文件 | `movePath`, `DestinationPath` |
| `File.copy` | 复制文件 | `copyPath`, `DestinationPath` |
| `File.delete` | 删除文件 | `path` |
| `File.search` | 搜索文件 | `path`, `content` |
| `File.create` | 创建文件 | `path`, `folder`, `files` |
| `File.info` | 获取文件信息 | `path` |

### Gradle 工具

| 工具名 | 描述 | 参数 | 指令示例 |
|--------|------|------|----------|
| `gradle.run-project` | 调用IDE的API直接开始运行构建编译 | 无 | `@gradle:run-project` |
| `gradle.Refresh-project` | 同步刷新工程项目 | 无 | `@gradle:Refresh-project` |

### Task 工具

| 工具名 | 描述 | 参数 | 指令示例 |
|--------|------|------|----------|
| `task.runTask` | 运行指定的gradle task任务 | `runTask` | `@task:runTask=build` |
| `task.taskList` | 列出所有gradle task任务列表 | 无 | `@task:taskList` |
| `task.searchTask` | 根据输入的任意内容搜索task任务 | `searchTask` | `@task:searchTask=build` |

### Shell 工具

| 工具名 | 描述 | 参数 | 指令示例 |
|--------|------|------|----------|
| `shell.execute` | 通过TermuxShellExecutor执行终端命令 | `execute` | `@shell:execute=ls -la` |

### BuildLog 工具

| 工具名 | 描述 | 参数 | 指令示例 |
|--------|------|------|----------|
| `BuildLog.getBuildLog` | 获取Gradle构建输出日志 | 无 | `@BuildLog:getBuildLog` |
| `BuildLog.getAppLog` | 获取应用运行日志 | 无 | `@BuildLog:getAppLog` |
| `BuildLog.getIDELog` | 获取IDE运行日志 | 无 | `@BuildLog:getIDELog` |
| `BuildLog.getLogView` | 获取当前日志视图内容 | 无 | `@BuildLog:getLogView` |

## 错误处理

系统提供完善的错误处理机制：

1. **连接错误**: 自动重连机制
2. **请求错误**: 详细的错误信息返回
3. **工具错误**: 异常捕获和日志记录
4. **服务错误**: 优雅降级和恢复

## 调试

### 日志查看

```kotlin
// 启用详细日志
Logger.setLogLevel(LogLevel.DEBUG)
```

### 状态监控

```kotlin
// 监控连接状态
serviceManager.connectionState.collect { state ->
    when (state) {
        ConnectionState.CONNECTED -> println("已连接")
        ConnectionState.CONNECTING -> println("连接中...")
        ConnectionState.FAILED -> println("连接失败")
        ConnectionState.DISCONNECTED -> println("未连接")
    }
}
```

## 性能优化

1. **连接池**: 复用 HTTP 连接
2. **缓存**: 工具结果缓存
3. **异步处理**: 非阻塞请求处理
4. **内存管理**: 及时释放资源

## 安全考虑

1. **网络隔离**: 仅允许本地连接
2. **权限控制**: 最小权限原则
3. **输入验证**: 参数验证和清理
4. **错误信息**: 避免敏感信息泄露

## 扩展开发

### 添加新工具

1. 实现 `McpTool` 接口
2. 在 `McpToolRegistry2` 中注册
3. 在 `McpToolMetadata` 中添加描述
4. 更新文档

### 自定义协议

1. 扩展 `McpRequest` 和 `McpResponse`
2. 实现自定义处理器
3. 添加序列化支持

## 故障排除

### 常见问题

1. **服务启动失败**
   - 检查端口是否被占用
   - 确认权限配置正确

2. **连接超时**
   - 检查网络连接
   - 确认服务器地址正确

3. **工具调用失败**
   - 检查工具是否正确注册
   - 确认参数格式正确

### 日志分析

查看日志文件获取详细错误信息：

```bash
adb logcat | grep "MCP"
```

## 贡献指南

1. Fork 项目
2. 创建功能分支
3. 提交更改
4. 创建 Pull Request

## 许可证

本项目采用 MIT 许可证。 

## core 目录

- core/  # 指令解析、注册、分发等核心逻辑 