package de.lulebe.designer.data

import android.content.Context
import java.io.File
import java.io.FilenameFilter


object IncludedFiles {
    val fonts = mapOf<Long, String>(
            Pair(0L, "Roboto-Regular.ttf"),
            Pair(1L, "Roboto-Light.ttf"),
            Pair(2L, "Roboto-Bold.ttf"),
            Pair(3L, "Roboto-Italic.ttf"),
            Pair(4L, "RobotoCondensed-Regular.ttf"),
            Pair(5L, "RobotoCondensed-Light.ttf"),
            Pair(6L, "RobotoCondensed-Bold.ttf"),
            Pair(7L, "RobotoCondensed-Italic.ttf"),
            Pair(8L, "RobotoSlab-Regular.ttf"),
            Pair(9L, "RobotoSlab-Light.ttf"),
            Pair(10L, "RobotoSlab-Bold.ttf"),
            Pair(11L, "RobotoMono-Regular.ttf"),
            Pair(12L, "RobotoMono-Light.ttf"),
            Pair(13L, "RobotoMono-Bold.ttf"),
            Pair(14L, "RobotoMono-Italic.ttf"),
            Pair(15L, "HelveticaNeue-Regular.ttf"),
            Pair(16L, "HelveticaNeue-Light.ttf"),
            Pair(17L, "HelveticaNeue-Bold.ttf"),
            Pair(18L, "HelveticaNeue-Italic.ttf"),
            Pair(19L, "HelveticaNeue-BlackCond.ttf")
    )
    val boards = mapOf<Long, String>(
            Pair(-1L, "Material Design")
    )
    val includedBoardsVersion = 2

    fun setupBoards(ctx: Context) {
        ctx.filesDir.list(FilenameFilter { dir, fileName ->
            try {
                val id = fileName.toLong()
                if (id < 0)
                    return@FilenameFilter true
            } catch (e: Exception) {
                return@FilenameFilter false
            }
            false
        }).forEach {
            File(ctx.filesDir.path + File.separator + it).deleteRecursively()
        }
        boards.keys.forEach { key ->
            StorageManager.createFromZipInput(ctx, ctx.assets.open("boards/" + key.toString() + ".zip"), key.toString()).close()
        }
    }
}