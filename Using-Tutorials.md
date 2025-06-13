# ZeroStudio ChatAI Command and MCP Service Configuration Documentation

## # ZeroStudio ChatAI Command and MCP Service Configuration Documentation
 ChatAI 指令与MCP服务配置文档

## Table of Contents

## 目录
- [@Command Reference](#command-reference)
- [@指令参考](#指令参考)
  - [@TabFile Command](#tabfile-command)
  - [@TabFile 指令](#tabfile-指令)
  - [@file Command](#file-command)
  - [@file 指令](#file-指令)
  - [@task Command](#task-command)
  - [@task 指令](#task-指令)
  - [@gradle Command](#gradle-command)
  - [@gradle 指令](#gradle-指令)
  - [@shell Command](#shell-command)
  - [@shell 指令](#shell-指令)
- [MCP Service Configuration](#mcp-service-configuration)
- [MCP服务配置](#mcp服务配置)
- [Complete Usage Examples](#complete-usage-examples)
- [完整使用示例](#完整使用示例)
- [Author Information](#author-information)
- [作者信息](#作者信息)

## Command Reference

## 指令参考

### @TabFile Command

Used to manipulate currently open file tabs, retrieve file content, specific lines, cursor position, or function definitions.

#### Subcommands
| Subcommand | Syntax | Description | Parameters |
|------------|--------|-------------|------------|
| getFile | `@TabFile:getFile` | Get complete content of current file | None |
| getLine | `@TabFile:getLine:<line-range>` | Get content of specified line range | `line-range`: e.g., `5` (single line) or `5-10` (multiple lines) |
| getCursor | `@TabFile:getCursor` | Get content of line where cursor is located | None |
| getFunction | `@TabFile:getFunction:<function-name>` | Get complete definition of specified function | `function-name`: Name of the function to retrieve |

#### Usage Examples
1. Get complete content of current file:
   ```
   @TabFile:getFile
   ```
   Output: Returns entire content of the currently active file

2. Get lines 10-15 of code:
   ```
   @TabFile:getLine:10-15
   ```
   Output: Returns code content from lines 10 to 15 of the file

3. Get line where cursor is located:
   ```
   @TabFile:getCursor
   ```
   Output: Returns content of the line where cursor is currently positioned

4. Get function named `handleInput`:
   ```
   @TabFile:getFunction:handleInput
   ```
   Output: Returns complete definition of the `handleInput` function, including declaration and body

### @TabFile 指令
用于操作当前打开的文件标签，获取文件内容、特定行、光标位置或函数定义。

#### 子命令列表
| 子命令 | 语法 | 描述 | 参数 |
|--------|------|------|------|
| getFile | `@TabFile:getFile` | 获取当前文件完整内容 | 无 |
| getLine | `@TabFile:getLine:<行号范围>` | 获取指定行范围的内容 | `行号范围`: 如`5`(单行)或`5-10`(多行) |
| getCursor | `@TabFile:getCursor` | 获取光标所在行内容 | 无 |
| getFunction | `@TabFile:getFunction:<函数名>` | 获取指定函数的完整定义 | `函数名`: 要获取的函数名称 |

#### 使用示例
1. 获取当前文件完整内容:
   ```
   @TabFile:getFile
   ```
   输出: 返回当前活动文件的全部内容

2. 获取第10-15行代码:
   ```
   @TabFile:getLine:10-15
   ```
   输出: 返回文件中第10到15行的代码内容

3. 获取光标所在行:
   ```
   @TabFile:getCursor
   ```
   输出: 返回光标当前位置所在行的内容

4. 获取名为`handleInput`的函数:
   ```
   @TabFile:getFunction:handleInput
   ```
   输出: 返回`handleInput`函数的完整定义，包括函数声明和体

### @file Command

Used to perform file system operations such as creating files, writing content, searching files, etc.

#### Subcommands
| Subcommand | Syntax | Description | Parameters |
|------------|--------|-------------|------------|
| create | `@file:create:<path>[:isDir]` | Create file or directory | `path`: File/directory path; `isDir`: Optional, set to `true` to create directory |
| write | `@file:write:<path>:<content>` | Write content to file | `path`: Target file path; `content`: Text to write |
| searchName | `@file:searchName:<keyword>` | Search files by name | `keyword`: Search keyword |
| searchContent | `@file:searchContent:<content>` | Search files by content | `content`: Text to search for |
| upload | `@file:upload:<path>` | Upload file content | `path`: Path of file to upload |

#### Usage Examples
1. Create new file:
   ```
   @file:create:/app/src/main/java/com/example/MyClass.kt
   ```
   Output: `{"success":true}` indicating successful file creation

2. Write content to file:
   ```
   @file:write:/app/src/main/java/com/example/MyClass.kt:package com.example

class MyClass {
    fun hello() {
        println("Hello")
    }
}
   ```
   Output: `{"success":true}` indicating successful content writing

3. Search for files containing "Activity" in name:
   ```
   @file:searchName:Activity
   ```
   Output: Returns list of file paths with names containing "Activity"

### @file 指令
用于执行文件系统操作，如创建文件、写入内容、搜索文件等。

#### 子命令列表
| 子命令 | 语法 | 描述 | 参数 |
|--------|------|------|------|
| create | `@file:create:<路径>[:isDir]` | 创建文件或目录 | `路径`: 文件/目录路径；`isDir`: 可选，设为`true`则创建目录 |
| write | `@file:write:<路径>:<内容>` | 写入文件内容 | `路径`: 目标文件路径；`内容`: 要写入的文本 |
| searchName | `@file:searchName:<关键词>` | 按名称搜索文件 | `关键词`: 搜索关键词 |
| searchContent | `@file:searchContent:<内容>` | 按内容搜索文件 | `内容`: 要搜索的文本 |
| upload | `@file:upload:<路径>` | 上传文件内容 | `路径`: 要上传的文件路径 |

#### 使用示例
1. 创建新文件:
   ```
   @file:create:/app/src/main/java/com/example/MyClass.kt
   ```
   输出: `{"success":true}` 表示文件创建成功

2. 写入文件内容:
   ```
   @file:write:/app/src/main/java/com/example/MyClass.kt:package com.example

class MyClass {
    fun hello() {
        println("Hello")
    }
}
   ```
   输出: `{"success":true}` 表示内容写入成功

3. 搜索名称包含"Activity"的文件:
   ```
   @file:searchName:Activity
   ```
   输出: 返回所有名称包含"Activity"的文件路径列表

### @task Command

Used to execute project tasks, primarily related to building and running.

#### Syntax
```
@task:execute:<task-name>
```

#### Parameters
- `task-name`: Name of the task to execute, multiple tasks separated by commas

#### Usage Example
```
@task:execute:clean,build
```
Output: Result output of executing clean and build tasks

### @task 指令
用于执行项目任务，主要与构建和运行相关。

#### 语法
```
@task:execute:<任务名>
```

#### 参数
- `任务名`: 要执行的任务名称，多个任务用逗号分隔

#### 使用示例
```
@task:execute:clean,build
```
输出: 执行clean和build任务的结果输出

### @gradle Command

Used to execute Gradle commands.

#### Syntax
```
@gradle:execute:<command>
```

#### Parameters
- `command`: Gradle command to execute

#### Usage Example
```
@gradle:execute:assembleDebug
```
Output: Result of executing assembleDebug Gradle command

### @gradle 指令
用于执行Gradle命令。

#### 语法
```
@gradle:execute:<命令>
```

#### 参数
- `命令`: 要执行的Gradle命令

#### 使用示例
```
@gradle:execute:assembleDebug
```
输出: 执行assembleDebug Gradle命令的结果

### @shell Command

Used to execute shell commands.

#### Syntax
```
@shell:execute:<command>
```

#### Parameters
- `command`: Shell command to execute

#### Usage Example
```
@shell:execute:ls -l
```
Output: Output of executing ls -l command

### @shell 指令
用于执行shell命令。

#### 语法
```
@shell:execute:<命令>
```

#### 参数
- `命令`: 要执行的shell命令

#### 使用示例
```
@shell:execute:ls -l
```
输出: 执行ls -l命令的输出结果

## MCP Service Configuration

MCP (Message Control Protocol) service allows ChatAI to communicate with IDE functionality. Below are the configuration steps:

### 1. Ensure MCP Service is Properly Implemented
MCP service implementation is located in `d:\zeros\ZeroStudio\chatai\home\src\main\java\android\zero\mcp` directory, with main files including:
- `LocalMcpServer.kt`: MCP local server implementation
- `McpClient.kt`: MCP client implementation
- `Operator.kt`: Command executor
- `ContextManager.kt`: Context manager
- `FileHandler.kt`: File operation handler

### 2. Start MCP Server
The MCP server runs on port 11583 by default. Ensure the `LocalMcpServer` class is properly configured:

```kotlin
// Default configuration in LocalMcpServer.kt
class LocalMcpServer(private val projectRoot: File, private val port: Int = 11583)
```

### 3. Configure ChatAI to Connect to MCP Service
Ensure the following configuration in ChatAI settings:

1. Server address: `http://127.0.0.1:11583`
2. Communication endpoints:
   - Command sending: `/mcp`
   - Event stream: `/sse`

### 4. Verify MCP Connection
Test the connection using the `@TabFile:getFile` command. If it returns the current file content, the configuration is successful.

### 5. Troubleshooting
- **Connection failed**: Ensure MCP server is running and port 11583 is not occupied
- **No response to commands**: Check if command handling logic in `Operator.kt` is properly implemented
- **Permission issues**: Ensure the application has file system access permissions

## MCP服务配置

MCP (Message Control Protocol)服务允许ChatAI与IDE功能进行通信。以下是配置步骤：

### 1. 确保MCP服务正确实现
MCP服务实现位于`d:\zeros\ZeroStudio\chatai\home\src\main\java\android\zero\mcp`目录，主要文件包括：
- `LocalMcpServer.kt`: MCP本地服务器实现
- `McpClient.kt`: MCP客户端实现
- `Operator.kt`: 命令执行器
- `ContextManager.kt`: 上下文管理器

### 2. 启动MCP服务器
MCP服务器默认在端口11583上运行。确保`LocalMcpServer`类正确配置：

```kotlin
// LocalMcpServer.kt中的默认配置
class LocalMcpServer(private val projectRoot: File, private val port: Int = 11583)
```

### 3. 配置ChatAI连接到MCP服务
在ChatAI设置中确保以下配置：

1. 服务器地址: `http://127.0.0.1:11583`
2. 通信端点:
   - 命令发送: `/mcp`
   - 事件流: `/sse`

### 4. 验证MCP连接
使用`@TabFile:getFile`命令测试连接是否正常工作。如果返回当前文件内容，则配置成功。

### 5. 故障排除
- **连接失败**: 确保MCP服务器正在运行，检查端口11583是否被占用
- **命令无响应**: 检查`Operator.kt`中的命令处理逻辑是否正确实现
- **权限问题**: 确保应用具有文件系统访问权限

## Complete Usage Examples

### Example 1: Retrieve and Modify a Function

1. Retrieve the `handleInput` function:
   ```
   @TabFile:getFunction:handleInput
   ```

2. After modifying the function content, write it back using the @file command:
   ```
   @file:write:/path/to/ChatAiFragment.kt:fun handleInput(input: String) {
       // Modified function content
       // ...
   }
   ```

### Example 2: Execute Build and View Results

1. Execute Gradle build:
   ```
   @gradle:execute:build
   ```

2. Search build output:
   ```
   @file:searchContent:BUILD SUCCESSFUL
   ```

## 完整使用示例

### 示例1: 获取并修改函数

1. 获取`handleInput`函数:
   ```
   @TabFile:getFunction:handleInput
   ```

2. 修改函数内容后，使用`@file`命令写回:
   ```
   @file:write:/path/to/ChatAiFragment.kt:fun handleInput(input: String) {
       // 修改后的函数内容
       // ...
   }
   ```

### 示例2: 执行构建并查看结果

1. 执行Gradle构建:
   ```
   @gradle:execute:build
   ```

2. 搜索构建输出:
   ```
   @file:searchContent:BUILD SUCCESSFUL
   ```

## Author Information

android_zero/零丶

## 作者信息
android_zero/零丶



