/*
 *  This file is part of AndroidIDE.
 *
 *  AndroidIDE is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  AndroidIDE is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *   along with AndroidIDE.  If not, see <https://www.gnu.org/licenses/>.
 */

import org.gradle.api.Project
import java.text.SimpleDateFormat
import java.util.Date
import java.time.LocalDate
import java.time.format.DateTimeFormatter
/** @author Akash Yadav */
object ProjectConfig {

  const val REPO_HOST = "github.com"
  const val REPO_OWNER = "msmt2017"
  const val REPO_NAME = "ZeroStudio"
  const val REPO_URL = "https://${REPO_HOST}/${REPO_OWNER}/${REPO_NAME}"
  const val SCM_GIT =
    "scm:git:git://${REPO_HOST}/${REPO_OWNER}/${REPO_NAME}.git"
  const val SCM_SSH =
    "scm:git:ssh://git@${REPO_HOST}/${REPO_OWNER}/${REPO_NAME}.git"

  const val PROJECT_SITE = "https://m.androidide.com"
}
// Private variables for logging warnings, kept for consistency but their usage will change
private var shouldPrintNotAGitRepoWarning = true
private var shouldPrintVersionName = true
private var shouldPrintVersionCode = true


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

/**
 * Helper function to get the current date in YYYYMMDD format.
 */
private fun getCurrentDateVersion(): String {
    val dateFormatter = DateTimeFormatter.ofPattern("yyyyMMdd")
    return LocalDate.now().format(dateFormatter)
}

/**
 * Generates a simple version name based on the current date,
 * in the format "vYYYYMMDD".
 */
val Project.simpleVersionName: String
    get() {
        val dateVersion = getCurrentDateVersion()
        val version = "v$dateVersion"

        if (shouldPrintVersionName) {
            // Log the generated date-based version name
            logger.warn("Simple version name is '$version' (date-based).")
            shouldPrintVersionName = false
        }
        // No longer relying on git repo or rootProject.version for simpleVersionName,
        // as the user explicitly requested a date-based format.
        return version
    }

/**
 * Generates the project version code based on the current date,
 * in the format YYYYMMDD (as an integer).
 */
val Project.projectVersionCode: Int
    get() {
        // Get the date string part from simpleVersionName (e.g., "YYYYMMDD" from "vYYYYMMDD")
        val dateString = simpleVersionName.substring(1) // Remove the 'v' prefix

        val versionCode = dateString.toIntOrNull()

        if (versionCode == null) {
            // This should ideally not happen if simpleVersionName is correctly formatted as vYYYYMMDD
            throw IllegalStateException(
                "Cannot extract version code. Invalid date string '$dateString'."
            )
        }

        if (shouldPrintVersionCode) {
            logger.warn("Version code is '$versionCode' (date-based).")
            shouldPrintVersionCode = false
        }

        return versionCode
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

    if (CI.isCiBuild && CI.branchName != "ZeroStudio") {
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
