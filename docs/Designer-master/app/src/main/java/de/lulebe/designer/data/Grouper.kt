package de.lulebe.designer.data

import android.content.Context
import de.lulebe.designer.data.objects.BaseObject
import de.lulebe.designer.data.objects.BoardObject
import de.lulebe.designer.data.objects.CopyObject


object Grouper {
    fun group (ctx: Context, oldBoard: BoardObject, objs: List<BaseObject>) : BoardObject {
        val objects = objs.filter {
            it !is CopyObject
        }
        val newBoard = BoardObject()
        val sizes = calculateSizes(objects)
        newBoard.xpos = sizes[0]
        newBoard.ypos = sizes[1]
        newBoard.width = sizes[2]
        newBoard.height = sizes[3]
        objects.forEach {
            oldBoard.removeObject(it)
            it.xpos += -sizes[0]
            it.ypos += -sizes[1]
            newBoard.addObject(it)
        }
        oldBoard.addObject(newBoard)
        newBoard.init(ctx, oldBoard)
        return newBoard
    }
    fun calculateSizes (objects: List<BaseObject>) : IntArray {
        var minX = Int.MAX_VALUE
        var minY = Int.MAX_VALUE
        var maxX = Int.MIN_VALUE
        var maxY = Int.MIN_VALUE
        objects.forEach {
            minX = Math.min(it.xpos, minX)
            minY = Math.min(it.ypos, minY)
            maxX = Math.max(it.xpos+it.width, maxX)
            maxY = Math.max(it.ypos+it.height, maxY)
        }
        maxX += -minX
        maxY += -minY
        return intArrayOf(minX, minY, maxX, maxY)
    }
}