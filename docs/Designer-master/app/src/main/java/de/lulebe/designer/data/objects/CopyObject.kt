package de.lulebe.designer.data.objects

import android.content.Context
import de.lulebe.designer.data.Deserializer
import de.lulebe.designer.data.ExportContainer


class CopyObject : BaseObject() {

    @Transient
    var source: SourceObject? = null

    @Transient
    var sourceListener: () -> Unit = {}

    var sourceId: Long = 0L


    override fun canDirectlyChangeWidth() = false
    override fun canDirectlyChangeHeight() = false
    override fun canAcceptBoxStyle() = false

    override fun init(ctx: Context, board: BoardObject?) {
        if (board != null)
            this.source = board.getObjectWithUID(sourceId) as SourceObject
        if (source != null) {
            source!!.copies++
            _width = source!!.width
            _height = source!!.height
            sourceListener = {
                _width = source!!.width
                _height = source!!.height
                change()
                _widthMoving = width
                _heightMoving = height
                calculateHandles()
            }
            source!!.addChangeListener(sourceListener)
        }
        super.init(ctx, board)
    }

    override fun close() {
        source?.removeChangeListener(sourceListener)
        super.close()
    }

    override fun clone(): CopyObject {
        val c = CopyObject()
        c.sourceId = sourceId
        c.rotation = rotation
        return c
    }

    override fun getRenderables(d: Deserializer, forceReload: Boolean): Array<Renderable> {
        if (source == null)
            return emptyArray()
        val src = source!!
        if (!src.hasChanged && !forceReload)
            return renderables.toTypedArray()
        renderables.clear()
        val srcRend = src.getRenderables(d, forceReload)
        for (rend in srcRend) {
            val position = Renderable.Position(d.dipToPxF(actualXpos), d.dipToPxF(actualYpos), rotation, d.dipToPxF(width)/2F, d.dipToPxF(height)/2F)
            renderables.add(Renderable(rend.type, rend.shape, position, rend.paint))
        }
        return renderables.toTypedArray()
    }

    override fun getMainColor(): Int {
        if (source == null) return super.getMainColor()
        return source!!.getMainColor()
    }

    override fun export(ec: ExportContainer, saveToContainer: Boolean) : List<BaseObject> {
        val originalList = super.export(ec, saveToContainer)
        val mutList = originalList.toMutableList()
        val newObj = originalList[0] as CopyObject
        if (source != null) {
            val newSrc = source!!.export(ec, saveToContainer)[0]
            newObj.sourceId = newSrc.uid
            mutList.add(newSrc)
        }
        return mutList.toList()
    }
}