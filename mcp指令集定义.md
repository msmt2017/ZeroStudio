指令集列表：
@File 通过mcp服务于ai进行文件交互与操作以及上下文
@gradle 通过mcp服务让ai直接开始编译运行当前项目
@task 通过mcp服务获得gradle task任务列表，让ai运行任何task任务
@TabFile 通过mcp服务获得当前editorActivity窗口见面的tab标签文件，包含整个文件内容，指定行内容，当前光标所在内容
@shell 运行执行终端命令行，通过mcp代理到termux终端当前在运行的会话然后可以让ai自己在窗口发送终端命令行到termux终端会话执行该命令行

指令集细节：
*逗号","隔开可以表示使用多个指令混合使用
"@File:create:" 这个指令有以下主要操作指令：path，folder，files
 path= 指定文件路径
 #folder=文件夹名
 #files=文件名+文件后缀

"@File:WriteFile:"  这个指令有以下主要操作指令：
path=文件路径+文件
#content=需要写入内容
#writeLine=需要写入到什么行 比如位于#path=该命令指定的文件内的多少行，例如第5行（表示从第五行开始写入#content=操作指令的内容）
"@File:info:" 这个命令标签表示获得和统计列出指定文件/文件夹（及文件夹下所有文件/文件夹）的信息，比如文件大小，文件数量，文件字数，文件创建时间，文件所在位置，文件路径，文件/文件夹的md5，sha，等哈希值信息等各类信息具体
#path=指定文件路径+文件夹/文件

"@File:search" 这个命令用于在#path指定路径下搜索指定内容，并列出搜索结果给ai
#path=指定路径
#content=需要搜索的内容

@File:Upload 这个指令用于上传指定文件给ai
#Files=指定路径下的文件
#getFolder 这个#getFolder 命令表示上传指定文件路径文件夹下所有文件


@TabFile: 直接细节解释：
#getFunction:<function-name>  这个#getFunction命令用于获得指定函数名的完整内容，大概就是： * 被指定的函数名(*) {*} 
需要支持获取java/kotlin这两种编程语言。
#getCursor 这个#getCursor 操作指令表示获得光标所在行的这一单行完整内容给ai
#getLine:<line-range> 这个#getLine操作指令表示获得指定行内容，比如第5行这一行完整内容，或者多行指定：5-10，表示获取获得从第5行开始至第10行之间的完整内容 上传给ai
#getFile 这个#getFile 操作指令表示获得editorActivity窗口界面tab标签打开的文件的完整所有文本内容上传给ai
#searchTabFile 这个#searchTabFile操作指令表示在editoractivity编辑器窗口界面的tab标签打开的文件搜索内容，可以是指定内容也可以说任意文本内容然后在编辑器界面tab标签打开的文件内搜索查找
示范：#searchTabFile=需要搜索的内容

@gradle: 指令集细节
#run 调用IDE的API直接开始运行构建编译

@task 指令集细节解释：
#runTask= 这个#runTask= 指令集表示运行指定的gradle task任务，比如build或者 :build（和在终端运行task命令一样可以运行:task的任务）
#taskList= 这个#taskList= 操作指令表示列出所有gradle task任务列表给ai
#searchTask= 这个#searchTask= 操作指令表示根据输入的任意内容搜索task任务

@shell 指令集细节解释:
#execute= 这个#execute= 操作指令表示运行执行指定终端命令行，通过mcp服务代理发送到当前termux会话执行

