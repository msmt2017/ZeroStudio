
大概流程：chatai窗口发送@指令集>ai收到指令或者ai自动调用相应需要的指令集工具>转发给mcp服务>mcp服务收到指令工具>指令集转为java/kotlin的操作函数代码实例>操作实例执行对应指令需要的操作>操作完成给出回调结果内容>chatai窗口收到指令集操作完成的回调结果内容

指令集列表：
@File: 通过mcp服务于ai进行文件交互与操作以及上下文
@gradle: 通过mcp服务让ai直接开始编译运行当前项目
@task: 通过mcp服务获得gradle task任务列表，让ai运行任何task任务
@TabFile: 通过mcp服务获得当前editorActivity窗口见面的tab标签文件，包含整个文件内容，指定行内容，当前光标所在内容
@shell: 运行执行终端命令行，通过mcp代理到termux终端当前在运行的会话然后可以让ai自己在窗口发送终端命令行到termux终端会话执行该命令行

指令集细节：
*格式是：主要操作指令 +对操作指令的细节或者用处说明解释。#号用于触发和调用该操作指令与操作函数代码交互给mcp服务于mcp host，便于ai知道这些指令用途与准确操作等。
*逗号","隔开可以表示使用多个指令混合使用
@File:create: 这个指令有以下主要操作指令：path，folder，files
 path= 指定文件路径
 #folder=文件夹名
 #files=文件名+文件后缀

@File:WriteFile:  这个指令有以下主要操作指令：
path=文件路径+文件
#content=需要写入内容
#writeLine=需要写入到什么行 比如位于#path=该命令指定的文件内的多少行，例如第5行（表示从第五行开始写入#content=操作指令的内容）
@File:info: 这个命令标签表示获得和统计列出指定文件/文件夹（及文件夹下所有文件/文件夹）的信息，比如文件大小，文件数量，文件字数，文件创建时间，文件所在位置，文件路径，文件/文件夹的md5，sha系列文件值信息，crc32，等哈希值信息等各类信息具体
#path=指定文件路径+文件夹/文件

@File:Rename: 这个操作指令用于将指定目标文件/文件夹重命名为指定重命名名称内容
#DestinationPath= 指定需要重命名的目标文件/文件夹
#RenameContent= 需要重命名内容
完整示范：@File:Rename:#DestinationPath= 指定需要重命名的目标文件/文件夹,#RenameContent= 需要重命名内容

@File:copy: 这个指令用于复制文件
#copyPath=需要被复制的目标
#DestinationPath= 这个#DestinationPath= 操作指令是将#path=的需要被复制的文件/文件夹的所有内容目标复制到#DestinationPath= 指定路径

@File:move: 这个操作指令用于调用文件函数到指定目标下
#movePath= 需要被移动的目标文件/文件夹
#DestinationPath= 这个操作指令是将#movePath 的指定目标文件/文件夹移动到 #DestinationPath指定路径

@File:search: 这个命令用于在#path指定路径下搜索指定内容，并列出搜索结果给ai
#path=指定路径
#content=需要搜索的内容

@File:Upload: 这个指令用于上传指定文件给ai
#Files=指定路径下的文件
#getFolder 这个#getFolder 命令表示上传指定文件路径文件夹下所有文件

@File:workspace: 该指令用于被打开的工程项目的工作区操作
#getinstallApk=debug或者release 该指令用于安装位于工程项目com.android.application模块下的/build/outputs/apk/ 里面的apk文件，在#getinstallApk= 等号后面指定需要安装的变体例如debug或者release，然后验证apk是否已经签名，如果没签名则返回提示该apk未签名不能安装，如果apk已经签名，输入完指令给ai后，ai执行指令调用操作函数代码获得上下文然后intent拉起安卓系统内部的应用程序安装器安装apk
#getmoduleInfo 输出工作区的所有模块信息，包含该模块所在的绝对路径和相对路径，或者从settings.gradle.kts和settings.gradle文件里的include获取模块列表。然后输出给ai。通过调用androidIDE内的工作区或者工程项目的相关源码文件的API函数等来编写相关操作函数来实现。该#getmoduleInfo不需要=符号
#getGradleWrapperInfo 该指令用于获得gradle/wrapper/gradle-wrapper.properties中的文本信息（该操作指令getGradleWrapperInfo不需要等号）

#ModifyGradleVersion= 需要修改指定的gradle-wrapper版本。获得获取gradle/wrapper/路径下文件:gradle-wrapper.properties里面的文本上下文，然后获得distributionUrl=这个distributionUrl完整上下文信息后将后面的****/gradle-这里是版本-gradle类型（有bin和all两种）.zip
完整示范，比如我想修改gradle-wrapper.properties的distributionUrl链接中****/gradle-版本和gradle类型那么完整指令就是：例如@File:workspace:#ModifyGradleVersion=8.5,all
指定相关后的完整示例：https\://services.gradle.org/distributions/gradle-8.5-all.zip
这里表示了我讲URL：https\://services.gradle.org/distributions/后面的gradle-这里的版本指定为8.5-这里gradle类型指定为all.zip
目前gradle-wrapper.properties可用服务商URL有以下三家：
- 腾讯云镜像：https://mirrors.cloud.tencent.com/gradle/
- 阿里云镜像：https://mirrors.aliyun.com/macports/distfiles/gradle/ 或 https://mirrors.aliyun.com/gradle/
官方的：https\://services.gradle.org/distributions/
获得distributionUrl=后面的URL完整信息，判断是什么URL再去获得gradle-(gradle版本)-(gradle类型).zip信息再根据ModifyGradleVersion指定内容去修改链接后面的gradle-(gradle版本)-(gradle类型).zip信息内容

#GetModuleSrcFileList =指定工作区模块src下所有文件与文件夹删除为纯文本列表，例如app/src下所有文件与文件夹。自动屏蔽其它文件/文件夹，只需要得到/模块/src下的内容


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


@BuildLog: 这个指令用于获取LogViewFragment，IDELogFragment，BuildOutputFragment,AppLogFragment输出的日志内容给ai，重新编写一样逻辑功能的指令集日志函数。*这部分指令集不需要等号
#getBuildLog 这个指令集用于获取和BuildOutputFragment编译构建输出（gradle构建运行生成的task任务构建日志）输出的日志内容，重新编写一样功能逻辑的的日志函数
#getAppLog 这个指令用于IDE构建开发的apk安装后运行输出的日志，可以参考AppLogFragment的实现
#getIDELog 这个指令用于获取IDE构建与运行时输出的完整日志信息文本内容。可以参考IDELogFragment的实现。
#getLogView 这个指令用于获取log日志，参考LogViewFragment里面的代码实现