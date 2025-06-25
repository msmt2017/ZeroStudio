# AndroidIDE MCP 指令使用示例

## 概述

本文档提供了AndroidIDE MCP系统中各种指令的使用示例，包括@gradle、@task、@shell和@BuildLog指令集。

## @gradle 指令集

### 项目构建
```bash
# 快速构建项目（无需选择任务）
@gradle:run-project
```

### 项目同步
```bash
# 同步刷新工程项目
@gradle:Refresh-project
```

## @task 指令集

### 执行Gradle任务
```bash
# 构建整个项目
@task:runTask=build

# 清理构建文件
@task:runTask=clean

# 构建Debug版本
@task:runTask=assembleDebug

# 构建Release版本
@task:runTask=assembleRelease

# 安装Debug版本
@task:runTask=installDebug

# 运行测试
@task:runTask=test

# 代码检查
@task:runTask=lint

# 模块化任务（app模块的assembleDebug任务）
@task:runTask=:app:assembleDebug
```

### 获取任务列表
```bash
# 列出所有Gradle任务
@task:taskList
```

### 搜索任务
```bash
# 搜索包含"build"的任务
@task:searchTask=build

# 搜索包含"test"的任务
@task:searchTask=test

# 搜索包含"assemble"的任务
@task:searchTask=assemble
```

## @shell 指令集

### 执行终端命令
```bash
# 查看当前目录
@shell:execute=pwd

# 列出文件
@shell:execute=ls -la

# 查看目录结构
@shell:execute=tree

# 查看系统信息
@shell:execute=uname -a

# 查看进程
@shell:execute=ps aux

# 查看网络连接
@shell:execute=netstat -tuln

# 查看磁盘使用情况
@shell:execute=df -h

# 查看内存使用情况
@shell:execute=free -h

# 查看Java版本
@shell:execute=java -version

# 查看Gradle版本
@shell:execute=gradle --version

# 查看Android SDK位置
@shell:execute=echo $ANDROID_HOME
```

## @BuildLog 指令集

### 获取构建日志
```bash
# 获取Gradle构建输出日志
@BuildLog:getBuildLog
```

### 获取应用日志
```bash
# 获取应用运行日志
@BuildLog:getAppLog
```

### 获取IDE日志
```bash
# 获取IDE运行日志
@BuildLog:getIDELog
```

### 获取当前日志视图
```bash
# 获取当前活动的日志视图内容
@BuildLog:getLogView
```

## 组合使用示例

### 完整的构建流程
```bash
# 1. 同步项目
@gradle:Refresh-project

# 2. 查看可用的构建任务
@task:taskList

# 3. 构建Debug版本
@task:runTask=assembleDebug

# 4. 查看构建日志
@BuildLog:getBuildLog

# 5. 安装APK
@task:runTask=installDebug

# 6. 查看应用运行日志
@BuildLog:getAppLog
```

### 调试流程
```bash
# 1. 查看当前目录结构
@shell:execute=find . -name "*.kt" -o -name "*.java" | head -10

# 2. 查看构建输出
@BuildLog:getBuildLog

# 3. 查看IDE日志
@BuildLog:getIDELog

# 4. 运行测试
@task:runTask=test

# 5. 查看测试结果
@BuildLog:getBuildLog
```

### 项目维护流程
```bash
# 1. 查看项目信息
@File:workspace:getmoduleInfo

# 2. 查看Gradle配置
@File:workspace:getGradleWrapperInfo

# 3. 清理项目
@task:runTask=clean

# 4. 重新构建
@task:runTask=build

# 5. 查看构建结果
@BuildLog:getBuildLog
```

## 错误处理示例

### 构建失败时的调试
```bash
# 1. 查看构建错误
@BuildLog:getBuildLog

# 2. 查看IDE日志
@BuildLog:getIDELog

# 3. 检查Gradle配置
@File:workspace:getGradleWrapperInfo

# 4. 尝试清理并重新构建
@task:runTask=clean
@task:runTask=build
```

### 应用运行问题调试
```bash
# 1. 查看应用日志
@BuildLog:getAppLog

# 2. 查看IDE日志
@BuildLog:getIDELog

# 3. 重新安装应用
@task:runTask=installDebug

# 4. 查看安装后的日志
@BuildLog:getAppLog
```

## 最佳实践

### 1. 指令使用顺序
- 先使用@gradle:Refresh-project同步项目
- 再使用@task:taskList查看可用任务
- 然后执行具体的构建任务
- 最后查看相关日志

### 2. 日志查看策略
- 构建问题：使用@BuildLog:getBuildLog
- 应用问题：使用@BuildLog:getAppLog
- IDE问题：使用@BuildLog:getIDELog
- 通用查看：使用@BuildLog:getLogView

### 3. 终端命令使用
- 使用@shell:execute执行系统级命令
- 避免执行危险命令（如rm -rf）
- 优先使用相对路径
- 注意命令输出的编码问题

### 4. 错误处理
- 总是查看相关日志了解错误原因
- 使用@gradle:Refresh-project重新同步项目
- 使用@task:runTask=clean清理构建缓存
- 检查Gradle配置和依赖

## 注意事项

1. **权限问题**：某些终端命令可能需要特定权限
2. **路径问题**：确保在正确的项目目录下执行命令
3. **编码问题**：终端输出可能包含特殊字符
4. **性能问题**：大量日志可能影响性能
5. **依赖问题**：确保所有必要的工具和服务都已安装

## 故障排除

### 常见问题

1. **构建失败**
   - 检查@BuildLog:getBuildLog中的错误信息
   - 使用@gradle:Refresh-project重新同步
   - 检查Gradle配置

2. **应用无法运行**
   - 查看@BuildLog:getAppLog中的错误
   - 重新安装应用
   - 检查应用权限

3. **IDE问题**
   - 查看@BuildLog:getIDELog
   - 重启IDE
   - 检查插件配置

4. **终端命令失败**
   - 检查命令语法
   - 确认命令是否可用
   - 检查权限设置 