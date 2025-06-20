package com.yourcompany.buildlogic

import com.android.build.gradle.BaseExtension
import com.android.build.gradle.AppExtension 
import com.android.build.gradle.LibraryExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import java.io.File

class AndroidIdeConventionsPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        // 适用于 Android Application 模块
        project.plugins.withType(com.android.build.gradle.AppPlugin::class.java) {
            configureAndroid(project, project.extensions.getByType(AppExtension::class.java))
        }
        // 适用于 Android Library 模块
        project.plugins.withType(com.android.build.gradle.LibraryPlugin::class.java) {
            configureAndroid(project, project.extensions.getByType(LibraryExtension::class.java))
        }
    }

    private fun configureAndroid(project: Project, androidExtension: BaseExtension) {
        androidExtension.apply {
            buildTypes {
                getByName("release") {
                    isMinifyEnabled = true // 确保 release 构建启用 R8

                    // 引用 build-logic 中定义的通用 ProGuard 规则文件
                    val commonProguardFile = project.rootProject.file("build-logic/proguard/proguard-common.pro")
                    if (commonProguardFile.exists()) {
                        proguardFiles(commonProguardFile)
                    } else {
                        project.logger.warn("proguard-common.pro not found at ${commonProguardFile.absolutePath}")
                    }

                    // *** 关键修改在这里：通过 androidExtension 调用 getDefaultProguardFile ***
                    proguardFiles(androidExtension.getDefaultProguardFile("proguard-android-optimize.txt"))

                    // 如果子模块本身还有特定的 proguard-rules.pro 文件，也一并添加
                    val projectProguardFile = project.file("proguard-rules.pro")
                    if (projectProguardFile.exists()) {
                        proguardFiles(projectProguardFile)
                    } else {
                        project.logger.info("No proguard-rules.pro found in ${project.path}")
                    }

                    // 配置 Multidex 主 DEX 规则
                    val mainDexProguardFile = project.rootProject.file("build-logic/proguard/proguard-main-dex-rules.pro")
                    if (mainDexProguardFile.exists()) {
                        multiDexKeepProguard = mainDexProguardFile
                    } else {
                        project.logger.warn("proguard-main-dex-rules.pro not found at ${mainDexProguardFile.absolutePath}")
                    }
                }
                getByName("debug") {
                    isMinifyEnabled = false
                }
            }

            defaultConfig {
                multiDexEnabled = true
            }
        }
        // 统一添加 androidx.multidex 依赖
        project.dependencies.add("implementation", "androidx.multidex:multidex:2.0.1")
    }
}