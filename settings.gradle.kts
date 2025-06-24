@file:Suppress("UnstableApiUsage")

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

pluginManagement {
  includeBuild("build-logic") {
    name = "build-logic"
  }
  resolutionStrategy {

    eachPlugin {
      if (requested.id.id == "io.objectbox") {
        useModule("io.objectbox:objectbox-gradle-plugin:${requested.version}")
      }
    }
  }

  repositories {
    google {
      content {
        includeGroupByRegex("com\\.android.*")
        includeGroupByRegex("com\\.google.*")
        includeGroupByRegex("androidx.*")
      }
    }

    gradlePluginPortal()
    google()
    mavenCentral()
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
    val dateFormat = java.text.SimpleDateFormat("yyyyMMdd")
    val today = dateFormat.format(java.util.Date())
    val simpleVersion = "v$today"
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
    // https://s01.oss.sonatype.org/content/repositories/snapshots/com/itsaky/androidide/gradle-plugin/maven-metadata.xml
    maven { url = uri("https://s01.oss.sonatype.org/content/repositories/snapshots/") }
    maven { url = uri("https://s01.oss.sonatype.org/content/groups/public/") }
    maven { url = uri("https://jitpack.io") }
  }
  versionCatalogs {
    create("chatai") {
      from(files("chatai/gradle/libs.versions.toml"))
    }}
}

rootProject.name = "ZeroStudio"

include(
    ":app",

  ":core:annotation-processors",
  ":core:annotation-processors-ksp",
  ":core:annotations",
  ":core:actions",
  ":core:resources",
  ":core:shared",
  ":core:templates-api",
  ":core:templates-impl",
  ":core:build-info",
  ":core:common",

  ":editors:editor",
  ":editors:editor-api",
  ":editors:editor-treesitter",
   ":editors:treeview",
  ":editors:uidesigner",
  ":editors:xml-inflater",
  ":editors:lexers",

  ":modules:eventbus",
  ":modules:eventbus-android",
  ":modules:eventbus-events",
  ":modules:gradle-plugin",
  ":modules:gradle-plugin-config",
  ":modules:idestats",
  ":modules:logger",
  ":modules:logsender",
  ":modules:logsender-sample",
  ":modules:lookup",
  ":modules:preferences",
  ":modules:testing:android",
  ":modules:testing:lsp",
  ":modules:testing:tooling",
  ":modules:testing:unit",
 //语言服务
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

  ":chatai:ai",
  ":chatai:highlight",
  ":chatai:home",
  ":chatai:rag",
  ":chatai:search",

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