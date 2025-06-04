
import java.io.File

/**
 * Information about the CI build.
 *
 * @author Akash Yadav
 */
object CI {

  /** The short commit hash. */
  val commitHash by lazy {
    check(isGitRepo) {
      "This build is not a Git repository."
    }
    val sha = System.getenv("GITHUB_SHA") ?: "HEAD"
    cmdOutput("git", "rev-parse", "--short", sha)
  }

  /** Name of the current branch. */
  val branchName by lazy {
    check(isGitRepo) {
      "This build is not a Git repository."
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
    cmdOutput("git", "rev-parse", "--is-inside-work-tree").trim() == "true"
  }

  /** Whether the current build is a CI build. */
  val isCiBuild by lazy { "true" == System.getenv("CI") }

  /** Whether the current build is for tests. This is set ONLY in CI builds. */
  val isTestEnv by lazy { "true" == System.getenv("ANDROIDIDE_TEST") }

  private fun cmdOutput(vararg args: String): String {
    return ProcessBuilder(*args)
      .directory(File("."))
      .redirectErrorStream(true)
      .start()
      .inputStream
      .bufferedReader()
      .readText()
      .trim()
  }
}
