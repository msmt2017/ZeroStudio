# AndroidIDE MCP TabFile 功能实现总结

## 概述

本次实现完善了AndroidIDE中chatai模块的mcp子模块，重点增强了TabFile相关操作指令，并新增了AI窗口集成功能。同时重写和完善了所有@File操作指令，让它们能够实际执行文件树上下文的操作动作。

## 主要功能实现

### 1. TabFile工具增强

#### 1.1 更新现有工具
- **GetFileTool**: 更新为获取当前活动标签页文件（用户当前选中的tab）
- 添加了标签页索引和总数信息
- 改进了错误提示信息

#### 1.2 新增工具

##### UploadFileToAITool
- **功能**: 将当前活动标签页文件上传发送到AI窗口
- **参数**: `autoSend` (可选，是否自动发送到AI，默认true)
- **指令**: `@TabFile:uploadFileToAI`

##### GetAllEditorContentTool
- **功能**: 获取当前编辑器窗口的所有文本内容
- **参数**: `includeFileInfo` (可选，是否包含文件信息，默认true)
- **指令**: `@TabFile:getAllEditorContent`

##### GetActiveTabInfoTool
- **功能**: 获取当前活动标签页的详细信息
- **指令**: `@TabFile:getActiveTabInfo`

### 2. File操作工具重写和完善

#### 2.1 WriteFileTool - 文件写入工具
- **功能**: 写入文件内容，支持指定行写入
- **参数**: `path`, `content`, `writeLine` (可选)
- **指令**: `@File:WriteFile:#path=app/src/main/java/MainActivity.kt,#content=public class MainActivity,#writeLine=5`
- **特性**: 
  - 支持绝对路径和相对路径
  - 支持指定行号插入内容
  - 自动创建父目录
  - 支持UTF-8编码

#### 2.2 RenameFileTool - 文件重命名工具
- **功能**: 重命名文件或文件夹
- **参数**: `DestinationPath`, `RenameContent`
- **指令**: `@File:Rename:#DestinationPath=app/src/main/java/OldName.kt,#RenameContent=NewName.kt`
- **特性**:
  - 安全检查，防止路径分隔符
  - 检查目标文件是否已存在
  - 详细的成功/失败信息

#### 2.3 MoveFileTool - 文件移动工具
- **功能**: 移动文件或文件夹
- **参数**: `movePath`, `DestinationPath`
- **指令**: `@File:move:#movePath=app/src/main/java/oldpackage,#DestinationPath=app/src/main/java/newpackage`
- **特性**:
  - 自动创建目标目录
  - 检查源文件存在性
  - 支持文件夹移动

#### 2.4 CopyFileTool - 文件复制工具
- **功能**: 复制文件或文件夹
- **参数**: `copyPath`, `DestinationPath`
- **指令**: `@File:copy:#copyPath=app/src/main/java/MyClass.kt,#DestinationPath=app/src/main/java/backup/`
- **特性**:
  - 递归复制文件夹
  - 计算文件夹大小
  - 支持覆盖复制

#### 2.5 FileDeleteTool - 文件删除工具
- **功能**: 删除文件或文件夹
- **参数**: `path`
- **指令**: `@File:delete:#path=app/src/main/java/OldFile.kt`
- **特性**:
  - 递归删除文件夹
  - 统计删除的文件数量
  - 显示删除的文件大小

#### 2.6 FileSearchTool - 文件搜索工具
- **功能**: 在指定路径下搜索文件内容
- **参数**: `path`, `content`
- **指令**: `@File:search:#path=app/src/main/java,#content=MainActivity`
- **特性**:
  - 支持多种文件类型搜索
  - 显示匹配行号和内容
  - 高亮显示匹配位置

#### 2.7 FileCreateTool - 文件创建工具
- **功能**: 创建文件或文件夹
- **参数**: `path`, `folder`, `files` (可选)
- **指令**: `@File:create:#path=app/src/main/java,#folder=false,#files=NewClass.kt`
- **特性**:
  - 根据文件扩展名添加默认内容
  - 支持Java、Kotlin、XML等文件类型
  - 自动创建父目录

#### 2.8 FileInfoTool - 文件信息工具
- **功能**: 获取文件或文件夹的详细信息
- **参数**: `path`
- **指令**: `@File:info:#path=app/src/main/java/MainActivity.kt`
- **特性**:
  - 计算MD5、SHA-1、SHA-256、CRC32哈希值
  - 统计文件类型分布
  - 显示大文件列表
  - 文本文件字符统计

#### 2.9 FileUploadTool - 文件上传工具
- **功能**: 上传指定文件给AI
- **参数**: `Files` 或 `getFolder`
- **指令**: `@File:Upload:#Files=app/src/main/java/MainActivity.kt`
- **特性**:
  - 支持单个文件上传
  - 支持文件夹批量上传
  - 自动过滤可上传文件类型

### 3. File:workspace 工作区工具

#### 3.1 GetModuleInfoTool - 模块信息工具
- **功能**: 获取工作区的所有模块信息
- **指令**: `@File:workspace:getmoduleInfo`
- **特性**:
  - 解析settings.gradle.kts文件
  - 自动检测模块类型
  - 显示模块路径和构建文件

#### 3.2 GetGradleWrapperInfoTool - Gradle Wrapper信息工具
- **功能**: 获取gradle-wrapper.properties文件信息
- **指令**: `@File:workspace:getGradleWrapperInfo`
- **特性**:
  - 显示Gradle版本和分发类型
  - 解析distributionUrl
  - 显示文件内容

#### 3.3 ModifyGradleVersionTool - Gradle版本修改工具
- **功能**: 修改gradle-wrapper.properties中的Gradle版本
- **参数**: `version`
- **指令**: `@File:workspace:ModifyGradleVersion=8.5,all`
- **特性**:
  - 支持版本号验证
  - 自动备份原文件
  - 支持all和bin分发类型

#### 3.4 GetModuleSrcFileListTool - 模块源码文件列表工具
- **功能**: 获取指定模块src目录下的所有文件和文件夹列表
- **参数**: `moduleName`
- **指令**: `@File:workspace:GetModuleSrcFileList=app`
- **特性**:
  - 树形结构显示
  - 过滤隐藏文件和构建目录
  - 按类型排序

#### 3.5 InstallApkTool - APK安装工具
- **功能**: 安装指定变体的APK文件
- **参数**: `variant`
- **指令**: `@File:workspace:getinstallApk=debug`
- **特性**:
  - 检查APK签名状态
  - 自动启动系统安装器
  - 支持debug和release变体

### 4. 文件上传服务

#### 4.1 FileUploadService
- 提供文件内容上传到AI窗口的服务
- 支持自动发送和手动发送模式
- 包含上传历史管理功能
- 提供事件流监控上传状态

#### 4.2 RikkahubAIIntegrationService
- 与rikkahub AI窗口的集成服务
- 支持文件、文本、图片三种内容类型
- 提供内容队列管理
- 支持内容统计和状态监控

### 5. 编辑器活动管理增强

#### 5.1 EditorActivityManager扩展
- 新增获取当前标签页索引方法
- 新增获取标签页总数方法
- 新增获取所有标签页信息方法
- 新增标签页切换和关闭功能
- 新增TabInfo数据类

### 6. AI窗口集成UI

#### 6.1 AIContentManager
- 提供待处理内容的可视化管理界面
- 支持内容预览、选择和删除
- 显示内容类型、时间戳和状态信息
- 提供批量操作功能

#### 6.2 ContentStatsCard
- 显示内容统计信息
- 实时更新文件、文本、图片数量

### 7. ChatAiFragment增强

#### 7.1 新增指令支持
- 所有TabFile指令
- 所有File操作指令
- 所有File:workspace指令

#### 7.2 改进响应处理
- 优化了成功/失败状态的显示
- 添加了更详细的错误信息
- 改进了用户反馈机制

### 8. Gradle操作工具

#### 8.1 RunProjectTool - 项目构建工具
- **功能**: 调用IDE的API直接开始运行构建编译，参考QuickRunWithCancellationAction里面的代码逻辑
- **指令**: `@gradle:run-project`
- **特性**:
  - 直接调用IDE的快速构建功能
  - 无需选择指定task任务来运行
  - 自动处理构建状态和结果

#### 8.2 RefreshProjectTool - 项目同步工具
- **功能**: 同步刷新工程项目，参考ProjectSyncAction调用编写的代码逻辑
- **指令**: `@gradle:Refresh-project`
- **特性**:
  - 执行项目同步操作
  - 重新加载项目结构
  - 更新Gradle配置

### 9. Task操作工具

#### 9.1 RunTaskTool - 任务执行工具
- **功能**: 运行指定的gradle task任务，比如build或者:build（和在终端运行task命令一样可以运行:task的任务）
- **参数**: `runTask`
- **指令**: `@task:runTask=build`
- **特性**:
  - 支持所有Gradle任务
  - 支持模块化任务（如:app:assembleDebug）
  - 实时显示执行状态和结果

#### 9.2 TaskListTool - 任务列表工具
- **功能**: 列出所有gradle task任务列表给ai
- **指令**: `@task:taskList`
- **特性**:
  - 获取完整的Gradle任务列表
  - 显示常用任务说明
  - 提供任务分类信息

#### 9.3 SearchTaskTool - 任务搜索工具
- **功能**: 根据输入的任意内容搜索task任务
- **参数**: `searchTask`
- **指令**: `@task:searchTask=build`
- **特性**:
  - 支持关键词搜索
  - 不区分大小写
  - 支持部分匹配

### 10. Shell操作工具

#### 10.1 ExecuteShellTool - 终端命令执行工具
- **功能**: 通过获取代理或者创建当前会话然后在当前会话执行运行终端命令行（executeshell）
- **参数**: `execute`
- **指令**: `@shell:execute=ls -la`
- **特性**:
  - 通过TermuxShellExecutor执行命令
  - 支持所有标准Linux命令
  - 实时返回命令输出结果

### 11. BuildLog日志工具

#### 11.1 GetBuildLogTool - 构建日志工具
- **功能**: 获取和BuildOutputFragment编译构建输出（gradle构建运行生成的task任务构建日志）输出的日志内容
- **指令**: `@BuildLog:getBuildLog`
- **特性**:
  - 获取Gradle构建过程的完整输出
  - 包括任务执行、编译、打包等信息
  - 实时更新构建状态

#### 11.2 GetAppLogTool - 应用日志工具
- **功能**: 获取IDE构建开发的apk安装后运行输出的日志，参考AppLogFragment的实现
- **指令**: `@BuildLog:getAppLog`
- **特性**:
  - 获取已安装APK的运行输出
  - 包括应用启动、运行、错误等信息
  - 需要LogSender服务连接

#### 11.3 GetIDELogTool - IDE日志工具
- **功能**: 获取IDE构建与运行时输出的完整日志信息文本内容，参考IDELogFragment的实现
- **指令**: `@BuildLog:getIDELog`
- **特性**:
  - 获取AndroidIDE的运行输出
  - 包括IDE启动、插件加载、错误等信息
  - 使用Logback框架记录日志

#### 11.4 GetLogViewTool - 日志视图工具
- **功能**: 获取log日志，参考LogViewFragment里面的代码实现
- **指令**: `@BuildLog:getLogView`
- **特性**:
  - 显示当前活动的日志视图内容
  - 可能是构建输出、应用日志或IDE日志
  - 内容会根据当前选择的标签页变化

## 技术架构

### 1. 服务层架构
```
┌─────────────────┐    ┌─────────────────┐    ┌─────────────────┐
│   TabFileTools  │    │ FileUploadService│    │RikkahubAI      │
│   (MCP Tools)   │───►│   (Upload)       │───►│Integration     │
└─────────────────┘    └─────────────────┘    └─────────────────┘
                              │                        │
                              ▼                        ▼
                       ┌─────────────────┐    ┌─────────────────┐
                       │ EditorActivity  │    │ AIContentManager│
                       │ Manager         │    │ (UI)           │
                       └─────────────────┘    └─────────────────┘
```

### 2. 数据流
1. AI发送指令
2. ChatAiFragment解析指令
3. 调用对应的MCP工具
4. 工具通过EditorActivityManager获取编辑器信息
5. 结果通过FileUploadService发送到AI窗口
6. RikkahubAIIntegrationService管理内容队列
7. AIContentManager显示待处理内容

## 使用示例

### 1. 文件操作示例
```kotlin
// 写入文件
@File:WriteFile:#path=app/src/main/java/MainActivity.kt,#content=public class MainActivity,#writeLine=5

// 重命名文件
@File:Rename:#DestinationPath=app/src/main/java/OldName.kt,#RenameContent=NewName.kt

// 搜索文件内容
@File:search:#path=app/src/main/java,#content=MainActivity

// 获取文件信息
@File:info:#path=app/src/main/java/MainActivity.kt
```

### 2. 工作区操作示例
```kotlin
// 获取模块信息
@File:workspace:getmoduleInfo

// 修改Gradle版本
@File:workspace:ModifyGradleVersion=8.5,all

// 安装APK
@File:workspace:getinstallApk=debug

// 获取源码文件列表
@File:workspace:GetModuleSrcFileList=app
```

### 3. 文件上传示例
```kotlin
// 上传单个文件
@File:Upload:#Files=app/src/main/java/MainActivity.kt

// 上传文件夹
@File:Upload:#getFolder=app/src/main/java
```

## 配置和初始化

### 1. 服务注册
所有新工具已在`McpToolRegistry2`中注册：
```kotlin
// File操作工具
mcpServer.registerTool("File.WriteFile", WriteFileTool(workspaceRoot))
mcpServer.registerTool("File.Rename", RenameFileTool(workspaceRoot))
mcpServer.registerTool("File.move", MoveFileTool(workspaceRoot))
mcpServer.registerTool("File.copy", CopyFileTool(workspaceRoot))
mcpServer.registerTool("File.delete", FileDeleteTool(workspaceRoot))
mcpServer.registerTool("File.search", FileSearchTool(workspaceRoot))
mcpServer.registerTool("File.create", FileCreateTool(workspaceRoot))
mcpServer.registerTool("File.info", FileInfoTool(workspaceRoot))
mcpServer.registerTool("File.Upload", FileUploadTool(workspaceRoot))

// 工作区工具
mcpServer.registerTool("File.workspace.getmoduleInfo", GetModuleInfoTool(projectManager))
mcpServer.registerTool("File.workspace.getGradleWrapperInfo", GetGradleWrapperInfoTool(gradleWrapperFile))
mcpServer.registerTool("File.workspace.getinstallApk", InstallApkTool(getApkFile, launchInstaller))
mcpServer.registerTool("File.workspace.ModifyGradleVersion", ModifyGradleVersionTool(gradleWrapperFile))
mcpServer.registerTool("File.workspace.GetModuleSrcFileList", GetModuleSrcFileListTool(workspaceRoot))
```

### 2. 服务初始化
在`McpInitManager`中初始化AI集成服务：
```kotlin
RikkahubAIIntegrationService.initialize(context)
```

### 3. 资源清理
在应用关闭时清理资源：
```kotlin
RikkahubAIIntegrationService.shutdown()
```