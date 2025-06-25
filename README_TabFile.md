# TabFile 工具集使用说明

## 概述

TabFile 工具集是 MCP (Model Context Protocol) 服务中的一组工具，用于操作 AndroidIDE 编辑器中的标签页文件。这些工具允许 AI 助手直接与编辑器中的文件进行交互，获取文件内容、光标位置、函数信息等。

## 支持的指令

### 1. @TabFile:getCursor
**功能**: 获取光标所在行的完整内容

**使用示例**:
```
@TabFile:getCursor
```

**返回结果**:
```json
{
  "line": 5,
  "column": 10,
  "content": "val example = \"Hello World\"",
  "file": "/storage/emulated/0/AndroidIDEProjects/MyApp/app/src/main/java/com/example/MainActivity.kt"
}
```

### 2. @TabFile:getFile
**功能**: 获取当前标签页文件的完整内容

**使用示例**:
```
@TabFile:getFile
```

**返回结果**:
```json
{
  "file": "/storage/emulated/0/AndroidIDEProjects/MyApp/app/src/main/java/com/example/MainActivity.kt",
  "name": "MainActivity.kt",
  "size": 2048,
  "lines": 50,
  "content": "package com.example\n\nimport android.os.Bundle\n...",
  "modified": false
}
```

### 3. @TabFile:getLine
**功能**: 获取指定行内容，支持单行或多行范围

**使用示例**:
```
@TabFile:getLine:#getLine=5
@TabFile:getLine:#getLine=5-10
```

**返回结果**:
```json
{
  "file": "/storage/emulated/0/AndroidIDEProjects/MyApp/app/src/main/java/com/example/MainActivity.kt",
  "totalLines": 50,
  "requestedRange": "5",
  "line": 5,
  "content": "    override fun onCreate(savedInstanceState: Bundle?) {"
}
```

### 4. @TabFile:searchTabFile
**功能**: 在当前标签页文件中搜索内容

**使用示例**:
```
@TabFile:searchTabFile:#searchTabFile=onCreate
```

**返回结果**:
```json
{
  "file": "/storage/emulated/0/AndroidIDEProjects/MyApp/app/src/main/java/com/example/MainActivity.kt",
  "searchContent": "onCreate",
  "totalMatches": 2,
  "results": [
    {
      "line": 5,
      "column": 15,
      "matchedText": "onCreate",
      "lineContent": "    override fun onCreate(savedInstanceState: Bundle?) {",
      "startPos": 14,
      "endPos": 22
    }
  ]
}
```

### 5. @TabFile:getFunction
**功能**: 获取指定函数名的完整内容

**使用示例**:
```
@TabFile:getFunction:#getFunction:onCreate
```

**返回结果**:
```json
{
  "file": "/storage/emulated/0/AndroidIDEProjects/MyApp/app/src/main/java/com/example/MainActivity.kt",
  "functionName": "onCreate",
  "totalMatches": 1,
  "results": [
    {
      "line": 5,
      "functionName": "onCreate",
      "definition": "override fun onCreate(savedInstanceState: Bundle?) {",
      "body": "override fun onCreate(savedInstanceState: Bundle?) {\n        super.onCreate(savedInstanceState)\n        setContentView(R.layout.activity_main)\n    }",
      "startLine": 5,
      "endLine": 8
    }
  ]
}
```

### 6. @TabFile:insertLine
**功能**: 在指定行插入内容

**使用示例**:
```
@TabFile:insertLine:#line=5,#content=// 新插入的注释
```

**返回结果**:
```json
{
  "file": "/storage/emulated/0/AndroidIDEProjects/MyApp/app/src/main/java/com/example/MainActivity.kt",
  "line": 5,
  "insertedContent": "// 新插入的注释",
  "success": true
}
```

### 7. @TabFile:replaceLine
**功能**: 替换指定行的内容

**使用示例**:
```
@TabFile:replaceLine:#line=5,#content=// 替换后的注释
```

**返回结果**:
```json
{
  "file": "/storage/emulated/0/AndroidIDEProjects/MyApp/app/src/main/java/com/example/MainActivity.kt",
  "line": 5,
  "oldContent": "// 原来的注释",
  "newContent": "// 替换后的注释",
  "success": true
}
```

### 8. @TabFile:deleteLine
**功能**: 删除指定行

**使用示例**:
```
@TabFile:deleteLine:#line=5
```

**返回结果**:
```json
{
  "file": "/storage/emulated/0/AndroidIDEProjects/MyApp/app/src/main/java/com/example/MainActivity.kt",
  "line": 5,
  "deletedContent": "// 被删除的注释",
  "success": true
}
```

### 9. @TabFile:getCurrentFile
**功能**: 获取当前标签页文件信息

**使用示例**:
```
@TabFile:getCurrentFile
```

**返回结果**:
```json
{
  "file": "/storage/emulated/0/AndroidIDEProjects/MyApp/app/src/main/java/com/example/MainActivity.kt",
  "name": "MainActivity.kt",
  "size": 2048,
  "lines": 50,
  "modified": false,
  "exists": true,
  "canRead": true,
  "canWrite": true
}
```

## 在 ChatAiFragment 中的使用

在 `ChatAiFragment` 中，AI 助手可以通过以下方式使用这些指令：

1. **自动调用**: AI 模型可以根据上下文自动调用相应的 TabFile 工具
2. **用户指令**: 用户可以在聊天中输入 `@TabFile:` 开头的指令
3. **工具调用**: 通过 MCP 协议调用相应的工具方法

### 示例对话

**用户**: "请帮我查看当前文件第10行的内容"

**AI**: 我来帮你查看第10行的内容。
```
@TabFile:getLine:#getLine=10
```

**系统返回**:
```
第10行的内容是: setContentView(R.layout.activity_main)
```

## 技术实现

### 架构组件

1. **EditorActivityManager**: 管理编辑器活动的生命周期
2. **TabFileTools**: 包含所有 TabFile 工具的实现
3. **McpToolRegistry2**: 注册所有工具到 MCP 服务
4. **ChatAiFragment**: 处理用户界面和指令解析

### 工作流程

1. 用户或 AI 发送 TabFile 指令
2. ChatAiFragment 解析指令并调用相应的工具
3. 工具通过 EditorActivityManager 获取当前编辑器
4. 执行相应的文件操作
5. 返回结果给用户或 AI

### 错误处理

所有工具都包含完善的错误处理机制：

- 检查编辑器活动是否存在
- 验证文件是否可访问
- 处理行号越界情况
- 提供详细的错误信息

## 注意事项

1. **行号索引**: 所有行号都使用 1 基索引（从 1 开始）
2. **文件路径**: 返回的路径是绝对路径
3. **权限**: 确保应用有读取和写入文件的权限
4. **编辑器状态**: 工具只能在编辑器活动存在时工作

## 扩展开发

如需添加新的 TabFile 工具，请：

1. 在 `TabFileTools.kt` 中实现新的工具类
2. 在 `McpToolRegistry2.kt` 中注册新工具
3. 在 `McpToolInfo.kt` 中添加工具描述
4. 在 `ChatAiFragment.kt` 中添加指令处理逻辑
5. 更新此文档 