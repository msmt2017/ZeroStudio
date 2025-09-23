package org.jetbrains.kotlin.utils

import android.annotation.SuppressLint
import android.content.Context
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.PathManager
import com.itsaky.androidide.utils.Environment
import java.io.File
import java.io.IOException
import java.nio.file.Paths
import java.util.jar.JarFile
import java.util.logging.Logger
import java.util.regex.Pattern
import org.jetbrains.jps.model.java.impl.JavaSdkUtil

object PathUtil {
    // 持有application context以访问ClassLoader和包信息
    @SuppressLint("StaticFieldLeak") // Application context在这里是安全的
    private var context: Context? = null
    private val LOG = Logger.getLogger(PathUtil::class.java.name)

    /**
     * 使用application context初始化PathUtil。
     * 必须在应用启动时调用一次。
     */
    fun initialize(appContext: Context) {
        context = appContext.applicationContext
    }

    const val ZERO = "android_zero"
    const val JS_LIB_NAME = "kotlin-stdlib-js"
    const val JS_LIB_JAR_NAME = "$JS_LIB_NAME.jar"

    const val JS_LIB_10_JAR_NAME = "kotlin-jslib.jar"
    const val ALLOPEN_PLUGIN_NAME = "allopen-compiler-plugin"
    const val ALLOPEN_PLUGIN_JAR_NAME = "$ALLOPEN_PLUGIN_NAME.jar"
    const val NOARG_PLUGIN_NAME = "noarg-compiler-plugin"
    const val NOARG_PLUGIN_JAR_NAME = "$NOARG_PLUGIN_NAME.jar"
    const val SAM_WITH_RECEIVER_PLUGIN_NAME = "sam-with-receiver-compiler-plugin"
    const val SAM_WITH_RECEIVER_PLUGIN_JAR_NAME = "$SAM_WITH_RECEIVER_PLUGIN_NAME.jar"
    const val SERIALIZATION_PLUGIN_NAME = "kotlinx-serialization-compiler-plugin"
    const val SERIALIZATION_PLUGIN_JAR_NAME = "$SERIALIZATION_PLUGIN_NAME.jar"
    const val LOMBOK_PLUGIN_NAME = "lombok-compiler-plugin"
    const val ANDROID_EXTENSIONS_RUNTIME_PLUGIN_JAR_NAME = "android-extensions-runtime.jar"
    const val PARCELIZE_RUNTIME_PLUGIN_JAR_NAME = "parcelize-runtime.jar"
    const val JS_LIB_SRC_JAR_NAME = "kotlin-stdlib-js-sources.jar"

    const val KOTLIN_JAVA_RUNTIME_JRE7_NAME = "kotlin-stdlib-jre7"
    const val KOTLIN_JAVA_RUNTIME_JRE7_JAR = "$KOTLIN_JAVA_RUNTIME_JRE7_NAME.jar"
    const val KOTLIN_JAVA_RUNTIME_JRE7_SRC_JAR = "$KOTLIN_JAVA_RUNTIME_JRE7_NAME-sources.jar"

    const val KOTLIN_JAVA_RUNTIME_JDK7_NAME = "kotlin-stdlib-jdk7"
    const val KOTLIN_JAVA_RUNTIME_JDK7_JAR = "$KOTLIN_JAVA_RUNTIME_JDK7_NAME.jar"
    const val KOTLIN_JAVA_RUNTIME_JDK7_SRC_JAR = "$KOTLIN_JAVA_RUNTIME_JDK7_NAME-sources.jar"

    const val KOTLIN_JAVA_RUNTIME_JRE8_NAME = "kotlin-stdlib-jre8"
    const val KOTLIN_JAVA_RUNTIME_JRE8_JAR = "$KOTLIN_JAVA_RUNTIME_JRE8_NAME.jar"
    const val KOTLIN_JAVA_RUNTIME_JRE8_SRC_JAR = "$KOTLIN_JAVA_RUNTIME_JRE8_NAME-sources.jar"

    const val KOTLIN_JAVA_RUNTIME_JDK8_NAME = "kotlin-stdlib-jdk8"
    const val KOTLIN_JAVA_RUNTIME_JDK8_JAR = "$KOTLIN_JAVA_RUNTIME_JDK8_NAME.jar"
    const val KOTLIN_JAVA_RUNTIME_JDK8_SRC_JAR = "$KOTLIN_JAVA_RUNTIME_JDK8_NAME-sources.jar"

    const val KOTLIN_JAVA_STDLIB_NAME = "kotlin-stdlib"
    const val KOTLIN_JAVA_STDLIB_JAR = "$KOTLIN_JAVA_STDLIB_NAME.jar"
    const val KOTLIN_JAVA_STDLIB_SRC_JAR = "$KOTLIN_JAVA_STDLIB_NAME-sources.jar"

    const val KOTLIN_JAVA_REFLECT_NAME = "kotlin-reflect"
    const val KOTLIN_JAVA_REFLECT_JAR = "$KOTLIN_JAVA_REFLECT_NAME.jar"
    const val KOTLIN_REFLECT_SRC_JAR = "$KOTLIN_JAVA_REFLECT_NAME-sources.jar"

    const val KOTLIN_JAVA_SCRIPT_RUNTIME_NAME = "kotlin-script-runtime"
    const val KOTLIN_JAVA_SCRIPT_RUNTIME_JAR = "$KOTLIN_JAVA_SCRIPT_RUNTIME_NAME.jar"
    const val KOTLIN_SCRIPTING_COMMON_NAME = "kotlin-scripting-common"
    const val KOTLIN_SCRIPTING_COMMON_JAR = "$KOTLIN_SCRIPTING_COMMON_NAME.jar"
    const val KOTLIN_SCRIPTING_JVM_NAME = "kotlin-scripting-jvm"
    const val KOTLIN_SCRIPTING_JVM_JAR = "$KOTLIN_SCRIPTING_JVM_NAME.jar"
    const val KOTLIN_DAEMON_NAME = "kotlin-daemon"
    const val KOTLIN_DAEMON_JAR = "$KOTLIN_SCRIPTING_JVM_NAME.jar"
    const val KOTLIN_SCRIPTING_COMPILER_PLUGIN_NAME = "kotlin-scripting-compiler"
    const val KOTLIN_SCRIPTING_COMPILER_PLUGIN_JAR = "$KOTLIN_SCRIPTING_COMPILER_PLUGIN_NAME.jar"
    const val KOTLINX_COROUTINES_CORE_NAME = "kotlinx-coroutines-core-jvm"
    const val KOTLINX_COROUTINES_CORE_JAR = "$KOTLINX_COROUTINES_CORE_NAME.jar"
    const val KOTLIN_SCRIPTING_COMPILER_IMPL_NAME = "kotlin-scripting-compiler-impl"
    const val KOTLIN_SCRIPTING_COMPILER_IMPL_JAR = "$KOTLIN_SCRIPTING_COMPILER_IMPL_NAME.jar"
    const val MAIN_KTS_NAME = "kotlin-main-kts"

    val KOTLIN_SCRIPTING_PLUGIN_CLASSPATH_JARS = arrayOf(
        KOTLIN_SCRIPTING_COMPILER_PLUGIN_JAR, KOTLIN_SCRIPTING_COMPILER_IMPL_JAR,
        KOTLIN_SCRIPTING_COMMON_JAR, KOTLIN_SCRIPTING_JVM_JAR,
    )

    const val KOTLIN_TEST_NAME = "kotlin-test"
    const val KOTLIN_TEST_JAR = "$KOTLIN_TEST_NAME.jar"
    const val KOTLIN_TEST_SRC_JAR = "$KOTLIN_TEST_NAME-sources.jar"

    const val KOTLIN_TEST_JS_NAME = "kotlin-test-js"
    const val KOTLIN_TEST_JS_JAR = "$KOTLIN_TEST_JS_NAME.jar"

    const val KOTLIN_JAVA_STDLIB_SRC_JAR_OLD = "kotlin-runtime-sources.jar"

    const val KOTLIN_COMPILER_NAME = "kotlin-compiler"
    const val KOTLIN_COMPILER_JAR = "$KOTLIN_COMPILER_NAME.jar"

    @JvmField
    val KOTLIN_RUNTIME_JAR_PATTERN: Pattern = Pattern.compile("kotlin-(stdlib|runtime)(-\\d[\\d.]+(-.+)?)?\\.jar")
    val KOTLIN_STDLIB_JS_JAR_PATTERN: Pattern = Pattern.compile("kotlin-stdlib-js.*\\.jar")
    val KOTLIN_STDLIB_COMMON_JAR_PATTERN: Pattern = Pattern.compile("kotlin-stdlib-common.*\\.jar")
    val KOTLIN_JS_LIBRARY_JAR_PATTERN: Pattern = Pattern.compile("kotlin-js-library.*\\.jar")

    const val HOME_FOLDER_NAME = "kotlinc"
    private val NO_PATH = File("<no_path>")

    @JvmStatic
    val kotlinPathsForIdeaPlugin: KotlinPaths
        get() = if (ApplicationManager.getApplication().isUnitTestMode)
            kotlinPathsForDistDirectory
        else
            KotlinPathsFromHomeDir(compilerPathForIdeaPlugin)

    @JvmStatic
    val kotlinPathsForCompiler: KotlinPaths
        get() = if (!pathUtilJar.isFile || !pathUtilJar.name.startsWith(KOTLIN_COMPILER_NAME)) {
            // PathUtil.class is located not in the kotlin-compiler*.jar, so it must be a test and we'll take KotlinPaths from "dist/"
            // (when running tests, PathUtil.class is in its containing module's artifact, i.e. util-{version}.jar)
            kotlinPathsForDistDirectory
        }
        else KotlinPathsFromHomeDir(compilerPathForCompilerJar)

    @JvmStatic
    val kotlinPathsForDistDirectory: KotlinPaths
        get() = KotlinPathsFromHomeDir(File("dist", HOME_FOLDER_NAME))

    private val compilerPathForCompilerJar: File
        get() {
            val jar = pathUtilJar
            if (!jar.exists()) return NO_PATH

            if (jar.name == KOTLIN_COMPILER_JAR) {
                val lib = jar.parentFile
                return lib.parentFile
            }

            return NO_PATH
        }

    private val compilerPathForIdeaPlugin: File
        get() {
            val jar = pathUtilJar
            if (!jar.exists()) return NO_PATH

            if (jar.name == "kotlin-plugin.jar") {
                val lib = jar.parentFile
                val pluginHome = lib.parentFile

                return File(pluginHome, HOME_FOLDER_NAME)
            }

            return NO_PATH
        }

    val pathUtilJar: File
        get() = getResourcePathForClass(PathUtil::class.java)

    /**
     * **修改后的实现**
     *
     * 获取给定类的资源路径。
     * 1. 优先在 `Environment.KOTLIN_LSP_LIBS_JAR_DIR` 目录下的JAR文件中查找。
     * 2. 然后尝试通过Android应用的ClassLoader在内存中（APK内）查找。
     * 3. 最后回退到原始的IntelliJ PathManager逻辑。
     * 4. 如果全部失败，抛出详细的异常。
     */
    @JvmStatic
    fun getResourcePathForClass(aClass: Class<*>): File {
        val className = aClass.name
        val classPath = className.replace('.', '/') + ".class" // e.g., org/jetbrains/kotlin/utils/PathUtil.class
        val appContext = context

        // --- 步骤 1: 在 KOTLIN_LSP_LIBS_JAR_DIR 目录的 JAR 文件中查找 ---
        LOG.info("Step 1: Attempting to find '$classPath' in external JARs...")
        val lspLibsDir = Environment.KOTLIN_LSP_LIBS_JAR_DIR
        if (lspLibsDir != null && lspLibsDir.isDirectory) {
            val jarFiles = lspLibsDir.listFiles { _, name -> name.endsWith(".jar") }
            if (jarFiles != null) {
                for (jarFile in jarFiles) {
                    try {
                        JarFile(jarFile).use { jar ->
                            if (jar.getJarEntry(classPath) != null) {
                                LOG.info("SUCCESS: Found '$classPath' in JAR '${jarFile.absolutePath}'")
                                return jarFile // 成功！返回这个JAR文件
                            }
                        }
                    } catch (e: IOException) {
                        LOG.warning("Could not read JAR file ${jarFile.absolutePath}: ${e.message}")
                    }
                }
            }
        } else {
            LOG.info("KOTLIN_LSP_LIBS_JAR_DIR is not a valid directory, skipping JAR search.")
        }

        // --- 步骤 2: 在当前运行的应用内存中 (ClassLoader) 查找 ---
        LOG.info("Step 2: Attempting to find '$className' via Android ClassLoader...")
        if (appContext != null) {
            try {
                // 尝试加载类。如果这不抛出异常，说明类存在于我们的DEX文件中。
                appContext.classLoader.loadClass(className)
                
                // 在这种情况下，“资源根”就是APK文件本身。
                val sourceDir = appContext.packageManager.getApplicationInfo(appContext.packageName, 0).sourceDir
                LOG.info("SUCCESS: Found class '$className' via ClassLoader. Resource root is APK: $sourceDir")
                return File(sourceDir) // 成功！返回APK文件
            } catch (e: ClassNotFoundException) {
                LOG.warning("Class '$className' not found in Android ClassLoader. Proceeding to next step...")
            }
        } else {
            LOG.warning("PathUtil context has not been initialized. Skipping Android ClassLoader search.")
        }

        // --- 步骤 3: 使用原始逻辑 (PathManager) 作为最后的回退 ---
        LOG.info("Step 3: Attempting to find '$classPath' via IntelliJ PathManager...")
        try {
            val path = "/" + classPath.removeSuffix(".class")
            val resourceRoot = PathManager.getResourceRoot(aClass, path)
            if (resourceRoot != null) {
                LOG.info("SUCCESS: Found '$classPath' via PathManager in '$resourceRoot'")
                return File(resourceRoot).absoluteFile // 成功！
            }
        } catch (e: Exception) {
            // PathManager 可能会抛出各种异常，这里统一捕获
            LOG.warning("PathManager failed to find '$classPath': ${e.message}")
        }

        // --- 步骤 4: 所有方法都失败了，抛出带有详细信息的异常 ---
        throw IllegalStateException(
            """
            FATAL: Could not find resource for class '$className' after trying all methods:
            1. Searched for '$classPath' in JARs under '${lspLibsDir?.absolutePath ?: "N/A"}' -> FAILED.
            2. Searched for '$className' in Android app ClassLoader (context is ${if (appContext == null) "null" else "not null"}) -> FAILED.
            3. Searched for '$classPath' using IntelliJ PathManager -> FAILED.
            Please ensure Kotlin compiler JARs are correctly placed in the KOTLIN_LSP_LIBS_JAR_DIR or packaged within the APK.
            """.trimIndent()
        )
    }

    // --- 所有其他原始函数保持不变 ---
    @JvmStatic
    fun getJdkClassesRootsFromCurrentJre(): List<File> =
            getJdkClassesRootsFromJre(System.getProperty("java.home"))

    @JvmStatic
    fun getJdkClassesRootsFromJre(javaHome: String): List<File> =
            JavaSdkUtil.getJdkClassesRoots(Paths.get(javaHome), true).map { it.toFile() }

    @JvmStatic
    fun getJdkClassesRoots(jdkHome: File): List<File> =
            JavaSdkUtil.getJdkClassesRoots(jdkHome.toPath(), false).map { it.toFile() }

    @JvmStatic
    fun getJdkClassesRootsFromJdkOrJre(javaRoot: File): List<File> {
        val isJdk = File(javaRoot, "jre/lib").exists()
        return JavaSdkUtil.getJdkClassesRoots(javaRoot.toPath(), !isJdk).map { it.toFile() }
    }
}