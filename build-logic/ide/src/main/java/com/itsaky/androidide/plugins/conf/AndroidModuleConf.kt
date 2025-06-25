package com.itsaky.androidide.plugins.conf

import BuildConfig
import FDroidConfig
import com.android.build.api.dsl.CommonExtension
import com.android.build.api.variant.AndroidComponentsExtension
import com.android.build.api.variant.ApplicationAndroidComponentsExtension
import com.android.build.api.variant.FilterConfiguration
import com.android.build.api.variant.impl.getFilter
import com.android.build.gradle.BaseExtension
import com.itsaky.androidide.plugins.util.SdkUtils.getAndroidJar
import isFDroidBuild
import org.gradle.api.Project
import org.gradle.api.artifacts.Dependency
import projectVersionCode
import java.text.SimpleDateFormat
import java.util.Date
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * ABIs for which the product flavors will be created.
 * The keys in this map are the names of the product flavors whereas,
 * the value for each flavor is a number that will be incremented to the base version code of the IDE
 * and set as the version code of that flavor.
 *
 * For example, if the base version code of the IDE is 270 (for v2.7.0), then for arm64-v8a
 * flavor, the version code will be `100 * 270 + 1` i.e. `27001`
 */
internal val flavorsAbis = mapOf("armeabi-v7a" to 1, "arm64-v8a" to 2, "x86_64" to 3)
private val disableCoreLibDesugaringForModules = arrayOf(":logsender", ":logger")
private var shouldPrintNotAGitRepoWarning = true
private var shouldPrintVersionName = true
private var shouldPrintVersionCode = true

fun Project.configureAndroidModule(
    coreLibDesugDep: Dependency
) {
    assert(
        plugins.hasPlugin("com.android.application") || plugins.hasPlugin("com.android.library")) {
        "${javaClass.simpleName} can only be applied to Android projects"
    }

    val androidJar = extensions.getByType(AndroidComponentsExtension::class.java)
        .getAndroidJar(assertExists = true)
    val frameworkStubsJar = findProject(":subprojects:framework-stubs")!!.file("libs/android.jar")
        .also { it.parentFile.mkdirs() }

    if (!(frameworkStubsJar.exists() && frameworkStubsJar.isFile)) {
        androidJar.copyTo(frameworkStubsJar)
    }

    extensions.getByType(CommonExtension::class.java).run {
        lint {
            checkDependencies = true
        }

        packaging {
            resources {
                excludes.addAll(
                    arrayOf(
                        "META-INF/CHANGES",
                        "META-INF/README.md",
                    )
                )
                pickFirsts.addAll(
                    arrayOf(
                        "META-INF/eclipse.inf",
                        "META-INF/LICENSE.md",
                        "META-INF/AL2.0",
                        "META-INF/LGPL2.1",
                        "META-INF/INDEX.LIST",
                        "about_files/LICENSE-2.0.txt",
                        "plugin.xml",
                        "plugin.properties",
                        "about.mappings",
                        "about.properties",
                        "about.ini",
                        "modeling32.png"
                    )
                )
            }
        }
    }

    extensions.getByType(BaseExtension::class.java).run {
        compileSdkVersion(BuildConfig.compileSdk)

        defaultConfig {
            minSdk = BuildConfig.minSdk
            targetSdk = BuildConfig.targetSdk
            versionCode = projectVersionCode
            // Modified to use the current date as the versionName
            versionName = "v" + getCurrentDateVersion()

            // required
            multiDexEnabled = true

            testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        }

        compileOptions {
            sourceCompatibility = BuildConfig.javaVersion
            targetCompatibility = BuildConfig.javaVersion
        }

        configureCoreLibDesugaring(this, coreLibDesugDep)

        if (":app" == project.path) {
            packagingOptions {
                jniLibs {
                    useLegacyPackaging = true
                }
            }

            flavorsAbis.forEach { (abi, _) ->
                // the common defaultConfig, not the flavor-specific
                defaultConfig.buildConfigField("String",
                    "ABI_${abi.replace('-', '_').uppercase()}",
                    "\"${abi}\"")
            }

            splits {
                abi {
                    reset()
                    isEnable = true
                    isUniversalApk = false
                    if (isFDroidBuild) {
                        include(FDroidConfig.fDroidBuildArch!!)
                    } else {
                        include(*flavorsAbis.keys.toTypedArray())
                    }
                }
            }

            extensions.getByType(ApplicationAndroidComponentsExtension::class.java).apply {
                onVariants { variant ->
                    variant.outputs.forEach { output ->

                        // version code increment
                        val verCodeIncr = flavorsAbis[output.getFilter(
                            FilterConfiguration.FilterType.ABI)?.identifier]
                            ?: throw UnsupportedOperationException("Universal APKs are not supported!")

                        output.versionCode.set(100 * projectVersionCode + verCodeIncr)
                    }
                }
            }
        } else {
            defaultConfig {
                ndk {
                    abiFilters.clear()
                    abiFilters += flavorsAbis.keys
                }
            }
        }

        buildTypes.getByName("debug") { isMinifyEnabled = false }
        buildTypes.getByName("release") {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }

        testOptions { unitTests.isIncludeAndroidResources = true }

        buildFeatures.viewBinding = true
        buildFeatures.buildConfig = true
    }
}

// Function to get the current date in YYYYMMDD format
private fun getCurrentDateVersion(): String {
    val dateFormatter = DateTimeFormatter.ofPattern("yyyyMMdd")
    return LocalDate.now().format(dateFormatter)
}

private fun Project.configureCoreLibDesugaring(baseExtension: BaseExtension,
    coreLibDesugDep: Dependency) {
    val coreLibDesugaringEnabled = project.path !in disableCoreLibDesugaringForModules

    baseExtension.compileOptions.isCoreLibraryDesugaringEnabled = coreLibDesugaringEnabled

    if (coreLibDesugaringEnabled) {
        configurations.getByName("coreLibraryDesugaring").apply {
            dependencies.add(coreLibDesugDep)
        }
    }
}
