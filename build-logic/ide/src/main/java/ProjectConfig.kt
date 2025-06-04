import org.gradle.api.Project

/** @author Akash Yadav */
object ProjectConfig {

  const val REPO_HOST = "github.com"
  const val REPO_OWNER = "msmt2017"
  const val REPO_NAME = "ZeroStudio"
  const val REPO_URL = "https://$REPO_HOST/$REPO_OWNER/$REPO_NAME"
  const val SCM_GIT =
    "scm:git:git://$REPO_HOST/$REPO_OWNER/$REPO_NAME.git"
  const val SCM_SSH =
    "scm:git:ssh://git@$REPO_HOST/$REPO_OWNER/$REPO_NAME.git"

  const val PROJECT_SITE = "https://m.androidide.com"
}

private var shouldPrintNotAGitRepoWarning = true
private var shouldPrintVersionName = true

/**
 * Whether this build is being executed in the F-Droid build server.
 */
val Project.isFDroidBuild: Boolean
  get() {
    if (!FDroidConfig.hasRead) {
      FDroidConfig.load(this)
    }
    return FDroidConfig.isFDroidBuild
  }

val Project.simpleVersionName: String
  get() {
    
    return BuildConfig.versionName
  }

private var shouldPrintVersionCode = true
val Project.projectVersionCode: Int
  get() {


    return BuildConfig.versionCode
  }

val Project.publishingVersion: String
  get() {

    var publishing = simpleVersionName
    if (isFDroidBuild) {
      // when building for F-Droid, the release is already published so we should have
      // the maven dependencies already published
      // simply return the simple version name here.
      return publishing
    }

    if (CI.isCiBuild && CI.isGitRepo && CI.branchName != "ZeroStudio") {
      publishing += "-${CI.commitHash}-SNAPSHOT"
    }

    return publishing
  }

/**
 * The version name which is used to download the artifacts at runtime.
 *
 * The value varies based on the following cases :
 * - For CI and F-Droid builds: same as [publishingVersion].
 * - For local builds: `latest.integration` to make sure that Gradle downloads the latest snapshots.
 */
val Project.downloadVersion: String
  get() {
    return if (CI.isCiBuild || isFDroidBuild) {
      publishingVersion
    } else {
      // sometimes, when working locally, Gradle fails to download the latest snapshot version
      // this may cause issues while initializing the project in AndroidIDE
      VersionUtils.getLatestSnapshotVersion("gradle-plugin")
    }
  }
