
enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

dependencyResolutionManagement {
  repositories {
    google()
    mavenCentral()
  }
  versionCatalogs {
    create("libs") {
      from(files("../gradle/libs.versions.toml"))
    }
  }

// 声明 chatai 子模块专用的版本目录
    // 它将从 "chatai/gradle/libs.versions.toml" 文件加载
    // versionCatalogs {
        // create("chataiLibs") { // 目录名称，可以自定义，例如 "chataiDeps"
            // from(files("../chatai/gradle/libs.versions.toml"))
        // }
        // // 默认的 "libs" 目录会自动从 "gradle/libs.versions.toml" 加载 (如果存在)
    // }

}



include(
  ":ide"
)

rootProject.name = "build-logic"