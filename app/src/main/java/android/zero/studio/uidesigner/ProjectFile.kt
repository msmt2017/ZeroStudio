package android.zero.studio.uidesigner

import android.os.Parcel
import android.os.Parcelable
import android.os.Parcelable.Creator
import android.zero.studio.uidesigner.managers.PreferencesManager
import android.zero.studio.uidesigner.utils.Constants
import android.zero.studio.uidesigner.utils.FileUtil
import org.jetbrains.annotations.Contract
import java.io.File

class ProjectFile : Parcelable {
    var path: String
        private set

    @JvmField
    var name: String

    @JvmField
    var date: String? = null

    constructor(path: String, date: String?) {
        this.path = path
        this.date = date
        this.name = FileUtil.getLastSegmentFromPath(path)
    }

    fun rename(newPath: String) {
        val newFile = File(newPath)
        val oldFile = File(path)
        oldFile.renameTo(newFile)

        path = newPath
        name = FileUtil.getLastSegmentFromPath(path)
    }

    val drawablePath: String
        get() = "$path/drawable/"

    val fontPath: String
        get() = "$path/font/"

    val colorsPath: String
        get() = "$path/values/colors.xml"

    val stringsPath: String
        get() = "$path/values/strings.xml"

    val layoutPath: String
        get() = "$path/layout/"

    val drawables: Array<out File>?
        get() {
            val file = File("$path/drawable/")

            if (!file.exists()) {
                FileUtil.makeDir("$path/drawable/")
            }

            return file.listFiles()
        }

    val fonts: Array<out File>?
        get() {
            val file = File("$path/font/")

            if (!file.exists()) {
                FileUtil.makeDir("$path/font/")
            }

            return file.listFiles()
        }

    val layouts: Array<out File>?
        get() {
            val file = File(layoutPath)
            if (!file.exists()) {
                FileUtil.makeDir(layoutPath)
            }
            return file.listFiles()
        }

    val allLayouts: MutableList<LayoutFile>
        get() {
            val list: MutableList<LayoutFile> = mutableListOf()
            layouts?.forEach { list.add(LayoutFile(it.absolutePath)) }
            return list
        }

    val mainLayout: LayoutFile
        get() = LayoutFile("$path/layout/layout_main.xml")

    var currentLayout: LayoutFile
        get() {
            val currentLayoutPath = PreferencesManager.prefs.getString(Constants.CURRENT_LAYOUT, "")
            return LayoutFile(currentLayoutPath)
        }
        set(value) {
            PreferencesManager.prefs.edit().putString(Constants.CURRENT_LAYOUT, value.path).apply()
        }

    fun createDefaultLayout() {
        FileUtil.writeFile(layoutPath + "layout_main.xml", "")
    }

    override fun describeContents(): Int {
        return 0
    }

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeString(path)
        parcel.writeString(name)
    }

    private constructor(parcel: Parcel) {
        path = parcel.readString().toString()
        name = parcel.readString().toString()
    }

    companion object {
        @JvmField
        val CREATOR: Creator<ProjectFile> = object : Creator<ProjectFile> {
            @Contract("_ -> new")
            override fun createFromParcel(`in`: Parcel): ProjectFile {
                return ProjectFile(`in`)
            }

            @Contract(value = "_ -> new", pure = true)
            override fun newArray(size: Int): Array<ProjectFile?> {
                return arrayOfNulls(size)
            }
        }
    }
}
