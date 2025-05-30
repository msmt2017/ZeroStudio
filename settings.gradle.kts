@file:Suppress("UnstableApiUsage")

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")


pluginManagement {
includeBuild("build-logic")
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
           google()
    mavenCentral()
    }
    resolutionStrategy {
        eachPlugin {
            if (requested.id.id == "io.objectbox") {
                useModule("io.objectbox:objectbox-gradle-plugin:${requested.version}")
            }
        }
    }
}



buildscript {
  repositories {
    mavenCentral()
  }
  dependencies {
    classpath("com.mooltiverse.oss.nyx:gradle:2.5.1")
  }
}

FDroidConfig.load(rootDir)

if (FDroidConfig.hasRead && FDroidConfig.isFDroidBuild) {
  gradle.rootProject {
val regex = Regex("^v\\d+")
    val simpleVersion = regex.find(FDroidConfig.fDroidVersionName!!)?.value
      ?: throw IllegalArgumentException("Invalid version '${FDroidConfig.fDroidVersionName}. Version name must have semantic version format.'")

    project.setProperty("version", simpleVersion)
  }
} else {
  apply {
    plugin("com.mooltiverse.oss.nyx")
  }
}

dependencyResolutionManagement {
  repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
  repositories {
    google()
    mavenCentral()
    mavenLocal()
    maven("https://jitpack.io")
    maven { url = uri("https://s01.oss.sonatype.org/content/repositories/snapshots") }
    maven { url = uri("https://s01.oss.sonatype.org/content/groups/public") }
    maven { url = uri("https://jitpack.io") }
  }
  
// 声明 chatai 子模块专用的版本目录
    // 它将从 "chatai/gradle/libs.versions.toml" 文件加载
    versionCatalogs {
        create("chataiLibs") { // 目录名称，可以自定义，例如 "chataiDeps"
            from(files("chatai/gradle/libs.versions.toml"))
        }
        // 默认的 "libs" 目录会自动从 "gradle/libs.versions.toml" 加载 (如果存在)
    }

}





rootProject.name = "AndroidIDE"

include(
  ":annotation-processors",
  ":annotation-processors-ksp",
  ":annotations",
  ":actions",
  ":app",
  ":build-info",
  ":common",
  ":editor",
  ":editor-api",
  ":editor-treesitter",
  ":eventbus",
  ":eventbus-android",
  ":eventbus-events",
  ":gradle-plugin",
  ":gradle-plugin-config",
  ":idestats",
  ":lexers",
  ":logger",
  ":logsender",
  ":logsender-sample",
  ":lookup",
  ":preferences",
  ":resources",
  ":shared",
  ":templates-api",
  ":templates-impl",
  ":treeview",
  ":uidesigner",
  ":xml-inflater",
  ":lsp:api",
  ":lsp:models",
  ":lsp:java",
  ":lsp:xml",
  ":subprojects:aaptcompiler",
  ":subprojects:appintro",
  ":subprojects:builder-model-impl",
  ":subprojects:flashbar",
  ":subprojects:framework-stubs",
  ":subprojects:fuzzysearch",
  ":subprojects:google-java-format",
  ":subprojects:java-compiler",
  ":subprojects:javac",
  ":subprojects:javac-services",
  ":subprojects:javapoet",
  ":subprojects:jaxp",
  ":subprojects:jdk-compiler",
  ":subprojects:jdk-jdeps",
  ":subprojects:jdt",
  ":subprojects:layoutlib-api",
  ":subprojects:projects",
  ":subprojects:tooling-api",
  ":subprojects:tooling-api-events",
  ":subprojects:tooling-api-impl",
  ":subprojects:tooling-api-model",
  ":subprojects:xml-dom",
  ":subprojects:xml-utils",
  ":termux:termux-app",
  ":termux:termux-emulator",
  ":termux:termux-shared",
  ":termux:termux-view",
  ":testing:android",
  ":testing:lsp",
  ":testing:tooling",
  ":testing:unit",
  
  ":chatai:highlight",
  ":chatai:ai",
  ":chatai:search",
  ":chatai:rag",
  ":chatai:mcp",
  ":chatai:app",
)

object FDroidConfig {

  var hasRead: Boolean = false
    private set

  var isFDroidBuild: Boolean = false
    private set

  var fDroidVersionName: String? = null
    private set

  var fDroidVersionCode: Int? = null
    private set

  const val PROP_FDROID_BUILD = "ide.build.fdroid"
  const val PROP_FDROID_BUILD_VERSION = "ide.build.fdroid.version"
  const val PROP_FDROID_BUILD_VERCODE = "ide.build.fdroid.vercode"

  fun load(rootDir: File) {
    val propsFile = File(rootDir, "fdroid.properties")
    if (!propsFile.exists() || !propsFile.isFile) {
      hasRead = true
      isFDroidBuild = false
      return
    }

    val properties = propsFile.let { props ->
      java.util.Properties().also {
        it.load(props.reader())
      }
    }

    hasRead = true
    isFDroidBuild = properties.getProperty(PROP_FDROID_BUILD, null).toBoolean()

    fDroidVersionName = properties.getProperty(PROP_FDROID_BUILD_VERSION, null)
    fDroidVersionCode =  properties.getProperty(PROP_FDROID_BUILD_VERCODE, null)?.toInt()
  }
}