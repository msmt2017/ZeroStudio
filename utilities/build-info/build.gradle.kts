import com.android.SdkConstants
import com.itsaky.androidide.build.config.AGP_VERSION_MINIMUM
import com.itsaky.androidide.build.config.BuildConfig
import com.itsaky.androidide.build.config.CI
import com.itsaky.androidide.build.config.FDroidConfig
import com.itsaky.androidide.build.config.ProjectConfig
import com.itsaky.androidide.build.config.VersionUtils
import com.itsaky.androidide.build.config.downloadVersion
import com.itsaky.androidide.build.config.publishingVersion
import com.itsaky.androidide.build.config.replaceContents
import com.itsaky.androidide.build.config.simpleVersionName
// Removed: import org.jetbrains.kotlin.incremental.createDirectory (This import is not needed for File.mkdirs())

plugins {
    //noinspection JavaPluginLanguageLevel
    id("java-library")
    id("com.vanniktech.maven.publish.base")
}

description = "Information about the AndroidIDE build"

val buildInfoGenDir: Provider<Directory> = project.layout.buildDirectory.dir("generated/buildInfo")
    // Replaced createDirectory() with standard File.mkdirs()
    .also { it.get().asFile.mkdirs() }

sourceSets { getByName("main").java.srcDir(buildInfoGenDir) }

tasks.create("generateBuildInfo") {
    val buildInfoPath = "com/itsaky/androidide/buildinfo/BuildInfo.java"
    val buildInfo = buildInfoGenDir.get().file(buildInfoPath)
    val buildInfoIn = project.file("src/main/java/${buildInfoPath}.in")

    doLast {
        // Ensure the parent directory exists before writing the file
        buildInfo.asFile.parentFile.mkdirs()
        buildInfoIn.replaceContents(
            dest = buildInfo.asFile,
            comment = "//",
            candidates =
            arrayOf(
                "PACKAGE_NAME" to BuildConfig.packageName,
                "MVN_GROUP_ID" to BuildConfig.packageName,

                "VERSION_NAME" to rootProject.version.toString(),
                "VERSION_NAME_SIMPLE" to rootProject.simpleVersionName,
                "VERSION_NAME_PUBLISHING" to rootProject.publishingVersion,
                "VERSION_NAME_DOWNLOAD" to rootProject.downloadVersion,

                "FDROID_BUILD" to FDroidConfig.isFDroidBuild.toString(),
                "FDROID_BUILD_VERSION_NAME" to (FDroidConfig.fDroidVersionName ?: "null"),
                "FDROID_BUILD_VERSION_CODE" to (FDroidConfig.fDroidVersionCode ?: -1).toString(),

                "CI_BUILD" to CI.isCiBuild.toString(),
                "CI_GIT_BRANCH" to if (CI.isGitRepo) CI.branchName else "NOT-A-GIT-REPO",
                "CI_COMMIT_HASH" to if (CI.isGitRepo) CI.commitHash else "NOT-A-GIT-REPO",

                "REPO_HOST" to ProjectConfig.REPO_HOST,
                "REPO_OWNER" to ProjectConfig.REPO_OWNER,
                "REPO_NAME" to ProjectConfig.REPO_NAME,
                "PROJECT_SITE" to ProjectConfig.PROJECT_SITE,

                "AGP_VERSION_MININUM" to AGP_VERSION_MINIMUM,
                "AGP_VERSION_LATEST" to libs.versions.agp.tooling.get(),
                "AGP_VERSION_GRADLE_LATEST" to SdkConstants.GRADLE_LATEST_VERSION,

                "SNAPSHOTS_REPOSITORY" to VersionUtils.SONATYPE_SNAPSHOTS_REPO,
                "PUBLIC_REPOSITORY" to VersionUtils.SONATYPE_PUBLIC_REPO,
            )
        )
    }
}

tasks.withType<JavaCompile> { dependsOn("generateBuildInfo") }
tasks.withType<Jar> { dependsOn("generateBuildInfo") }
