package de.lulebe.designer.data

import android.content.Context
import de.lulebe.designer.data.objects.BaseObject
import de.lulebe.designer.data.objects.BoardObject
import de.lulebe.designer.data.styles.BoxStyle
import de.lulebe.designer.data.styles.ColorStyle
import de.lulebe.designer.data.styles.TextStyle
import java.io.File
import java.io.FileInputStream


class ExportContainer {
    val objects = mutableMapOf<Long, BaseObject>()
    val newUIDs = mutableMapOf<Long, Long>()
    val boxStyles = mutableMapOf<Long, BoxStyle>()
    val colorStyles = mutableMapOf<Long, ColorStyle>()
    val textStyles = mutableMapOf<Long, TextStyle>()
    val images = mutableSetOf<Long>()
    val fonts = mutableSetOf<Long>()

    fun exportTo (ctx: Context, board: BoardObject, fromBoard: BoardObject) : Boolean {
        val storage = board.storageManager ?: return false
        //add all images & fonts
        images.forEach {
            val imgPath = fromBoard.getImagePath(it)
            if (imgPath != null) {
                val imgFile = File(imgPath)
                if (imgFile.exists() && imgFile.canRead()) {
                    storage.addImage(FileInputStream(imgFile), fromBoard.images[it]!!, it)
                }
            }
        }
        fonts.forEach {
            val fontPath = fromBoard.getFontPath(it)
            if (fontPath != null) {
                val fontFile = File(fontPath)
                if (fontFile.exists() && fontFile.canRead()) {
                    storage.addFont(FileInputStream(fontFile), fromBoard.fonts[it]!!, it)
                }
            }
        }
        //add all styles
        boxStyles.forEach {
            board.styles.addBoxStyle(it.value)
        }
        colorStyles.forEach {
            board.styles.addColorStyle(it.value)
        }
        textStyles.forEach {
            board.styles.addTextStyle(it.value)
        }
        //add all objects
        objects.keys.forEachIndexed { index, id ->
                objects[id]!!.init(ctx, board)
                board.addObject(objects[id]!!)
        }
        return true
    }
}