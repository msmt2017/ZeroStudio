// Remove this line: import org.jetbrains.kotlin.incremental.createDirectory

plugins {
    id("java-library")
    id("com.vanniktech.maven.publish.base")
}

description = "Information about the AndroidIDE build"

val buildInfoGenDir: Provider<Directory> = project.layout.buildDirectory.dir("generated/buildInfo")
// Removed: .also { it.get().asFile.createDirectory() } - Gradle will handle this

sourceSets { getByName("main").java.srcDir(buildInfoGenDir) }

// Use tasks.register instead of tasks.create for lazy configuration
tasks.register("generateBuildInfo") {
    val buildInfoPath = "com/itsaky/androidide/buildinfo/BuildInfo.java"
    val buildInfo = buildInfoGenDir.get().file(buildInfoPath)
    val buildInfoIn = project.file("src/main/java/${buildInfoPath}.in")

    // Define outputs for Gradle's up-to-date checking and directory creation
    outputs.file(buildInfo)
    inputs.file(buildInfoIn)

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
                    "CI_GIT_BRANCH" to CI.branchName,
                    "CI_COMMIT_HASH" to CI.commitHash,

                    "REPO_HOST" to ProjectConfig.REPO_HOST,
                    "REPO_OWNER" to ProjectConfig.REPO_OWNER,
                    "REPO_NAME" to ProjectConfig.REPO_NAME,
                    "PROJECT_SITE" to ProjectConfig.PROJECT_SITE,

                    "AGP_VERSION_MININUM" to AGP_VERSION_MINIMUM,
                    "AGP_VERSION_LATEST" to libs.versions.agp.tooling.get(),

                    "SNAPSHOTS_REPOSITORY" to VersionUtils.SONATYPE_SNAPSHOTS_REPO,
                    "PUBLIC_REPOSITORY" to VersionUtils.SONATYPE_PUBLIC_REPO,
                )
        )
    }
}

tasks.withType<JavaCompile> { dependsOn("generateBuildInfo") }
tasks.withType<Jar> { dependsOn("generateBuildInfo") }
