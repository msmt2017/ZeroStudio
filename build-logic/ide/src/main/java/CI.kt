// CI.kt 文件内容

import java.io.File

/**
 * Information about the CI build.
 *
 * @author Akash Yadav
 */
object CI {

    /** The short commit hash. */
    val commitHash by lazy {
        // 修改：如果不是Git仓库，不抛异常，而是返回默认值
        if (!isGitRepo) {
            println("WARNING: (CI.kt) Not a Git repository detected. Using default commit hash 'NO_GIT_HASH' for build.")
            return@lazy "NO_GIT_HASH" // 提供一个默认的提交哈希值
        }
        val sha = System.getenv("GITHUB_SHA") ?: "HEAD"
        cmdOutput("git", "rev-parse", "--short", sha)
    }

    /** Name of the current branch. */
    val branchName by lazy {
        // 修改：如果不是Git仓库，不抛异常，而是返回默认值
        if (!isGitRepo) {
            println("WARNING: (CI.kt) Not a Git repository detected. Using default branch name 'NO_GIT_BRANCH' for build.")
            return@lazy "NO_GIT_BRANCH" // 提供一个默认的分支名称
        }
        System.getenv("GITHUB_REF_NAME") ?: cmdOutput(
            "git", "rev-parse", "--abbrev-ref",
            "HEAD"
        ) // by default, 'main'
    }

    /**
     * Whether the current build is a Git repository.
     */
    val isGitRepo by lazy {
        val output = cmdOutput("git", "rev-parse", "--is-inside-work-tree").trim()
        val result = output == "true"
        // 可以在这里添加更多调试信息，例如：
         println("DEBUG: git rev-parse --is-inside-work-tree output: '$output', isGitRepo result: $result")
        
        result
    }

    /** Whether the current build is a CI build. */
    val isCiBuild by lazy { "true" == System.getenv("CI") }

    /** Whether the current build is for tests. This is set ONLY in CI builds. */
    val isTestEnv by lazy { "true" == System.getenv("ANDROIDIDE_TEST") }

    private fun cmdOutput(vararg args: String): String {
        return try {
            ProcessBuilder(*args)
                .directory(File(".")) // 在当前项目目录执行命令
                .redirectErrorStream(true) // 将错误输出重定向到标准输出，方便捕获
                .start()
                .inputStream
                .bufferedReader()
                .readText()
                .trim()
        } catch (e: Exception) {
            // 捕获命令执行异常，例如 'git' 命令找不到或执行权限问题
            System.err.println("ERROR: Failed to run command '${args.joinToString(" ")}' in CI.kt: ${e.message}")
            "" // 发生错误时返回空字符串，避免崩溃
        }
    }
}