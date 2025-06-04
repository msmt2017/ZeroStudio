

import org.gradle.api.Project
import java.io.File

/**
 * @author Akash Yadav
 */
object FDroidConfig {

  var hasRead: Boolean = false
    private set

  var isFDroidBuild: Boolean = false
    private set
    get() = hasRead && field

  var fDroidBuildArch: String? = null
    private set

  var fDroidVersionName: String? = null
    private set

  var fDroidVersionCode: Int? = null
    private set

  var aapt2Files: Map<String, String?> = emptyMap()
    private set

  const val PROP_FDROID_BUILD = "ide.build.fdroid"
  const val PROP_FDROID_BUILD_ARCH = "ide.build.fdroid.arch"
  const val PROP_FDROID_BUILD_VERSION = "ide.build.fdroid.version"
  const val PROP_FDROID_BUILD_VERCODE = "ide.build.fdroid.vercode"
  const val PROP_FDROID_AAPT2FILE_ARM64 = "ide.build.fdroid.aapt2File.arm64-v8a"
  const val PROP_FDROID_AAPT2FILE_ARM = "ide.build.fdroid.aapt2File.armeabi-v7a"
  const val PROP_FDROID_AAPT2FILE_X86_64 = "ide.build.fdroid.aapt2File.x86_64"

  fun load(project: Project) {
    val propsFile = File(project.rootDir, "fdroid.properties")
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

    fDroidBuildArch = properties.getProperty(PROP_FDROID_BUILD_ARCH, null)
    fDroidVersionName = properties.getProperty(PROP_FDROID_BUILD_VERSION, null)
    fDroidVersionCode = properties.getProperty(PROP_FDROID_BUILD_VERCODE, null)?.toInt()

    aapt2Files = mutableMapOf<String, String?>().also { files ->
      files[PROP_FDROID_AAPT2FILE_ARM64.substringAfterLast('.')] = properties.getProperty(
        PROP_FDROID_AAPT2FILE_ARM64, null)

      files[PROP_FDROID_AAPT2FILE_ARM.substringAfterLast('.')] = properties.getProperty(
        PROP_FDROID_AAPT2FILE_ARM, null)

      files[PROP_FDROID_AAPT2FILE_X86_64.substringAfterLast('.')] = properties.getProperty(
        PROP_FDROID_AAPT2FILE_X86_64,
        null)
    }
  }
}