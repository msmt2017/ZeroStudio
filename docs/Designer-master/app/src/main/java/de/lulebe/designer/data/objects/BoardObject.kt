package de.lulebe.designer.data.objects

import android.content.Context
import android.graphics.*
import de.lulebe.designer.Renderer
import de.lulebe.designer.data.*
import de.lulebe.designer.data.styles.BaseStyle
import de.lulebe.designer.data.styles.Styles
import java.io.File


class BoardObject() : SourceObject() {



    //sizeListeners
    @Transient
    private var sizeListeners = mutableListOf<() -> Unit>()
    fun addSizeChangeListener (l: () -> Unit) {
        sizeListeners.add(l)
    }
    fun removeSizeChangeListener (l: () -> Unit) {
        sizeListeners.remove(l)
    }
    fun removeAllSizeChangeListeners () {
        sizeListeners.clear()
    }
    protected  fun sizeChange () {
        hasChanged = true
        hasChangedSize = true
        for (listener in sizeListeners) {
            listener()
        }
    }

    //gridListeners
    @Transient
    private var gridListeners = mutableListOf<() -> Unit>()
    fun addGridChangeListener (l: () -> Unit) {
        gridListeners.add(l)
    }
    fun removeGridChangeListener (l: () -> Unit) {
        gridListeners.remove(l)
    }
    fun removeAllGridChangeListeners () {
        gridListeners.clear()
    }
    private fun gridChange () {
        for (listener in gridListeners) {
            listener()
        }
    }


    @Transient
    private var hasChangedSize = true

    @Transient
    private var renderedBitmap: Bitmap? = null

    @Transient
    private var _storageManager: StorageManager? = null
    var storageManager: StorageManager?
        get() {
            if (_parentBoard == null)
                return _storageManager
            else
               return _parentBoard?.storageManager
        }
        set(value) {
            _storageManager = value
        }


    override var width: Int
        get() = _width
        set(value) {
            if (widthMoving == _width)
                widthMoving = value
            _width = value
            sizeChange()
            gridChange()
        }

    override var height: Int
        get() = _height
        set(value) {
            if (heightMoving == _height)
                heightMoving = value
            _height = value
            sizeChange()
            gridChange()
        }

    private var _gridSize = 4
    var gridSize: Int
        get() = _gridSize
        set(value) {
            _gridSize = value
            gridChange()
        }

    private var _gridInterval = 4
    var gridInterval: Int
        get() = _gridInterval
        set(value) {
            _gridInterval = value
            gridChange()
        }

    init {
        _name = "Group"
    }

    private val _styles = Styles()
    val styles: Styles
        get() {
            if (_parentBoard == null)
                return  _styles
            else
                 return _parentBoard!!.styles
        }

    private val _images: MutableMap<Long, String> = mutableMapOf()
    val images: MutableMap<Long, String>
        get() {
            if (_parentBoard == null)
                return _images
            else
                return _parentBoard!!.images
        }

    private val _fonts: MutableMap<Long, String> = mutableMapOf()
    val fonts: MutableMap<Long, String>
        get() {
            if (_parentBoard == null)
                return _fonts
            else
                return _parentBoard!!.fonts
        }

    private var _objects: MutableList<BaseObject> = mutableListOf()
    var objects: MutableList<BaseObject>
        get() = _objects
        set(value) {
            _objects = value
        }

    fun addObject (baseObject: BaseObject) {
        _objects.add(baseObject)
        baseObject.addChangeListener {
            change()
        }
        change()
    }

    fun unlockCopy (copyObject: CopyObject, ctx: Context) : SourceObject? {
        if (copyObject.source == null) return null
        val newObj = copyObject.source!!.clone() as SourceObject
        newObj.name = copyObject.name
        newObj.xpos = copyObject.xpos
        newObj.ypos = copyObject.ypos
        newObj.rotation = copyObject.rotation
        newObj.init(ctx, this)
        _objects.add(_objects.indexOf(copyObject), newObj)
        _objects.remove(copyObject)
        copyObject.close()
        copyObject.source!!.copies--
        newObj.addChangeListener {
            change()
        }
        change()
        return newObj
    }

    @Throws(CannotDeleteCopiedObjectException::class)
    fun removeObject (baseObject: BaseObject) {
        if (baseObject.copies == 0) {
            baseObject.close()
            if (baseObject is CopyObject) {
                baseObject.source?.copies?.minus(1)
            }
            _objects.remove(baseObject)
            change()
        } else {
            throw CannotDeleteCopiedObjectException()
        }
    }

    fun reorderedObjects () {
        change()
    }

    fun getImagePath(uid: Long) : String? {
        if (images.containsKey(uid) && storageManager != null)
            return storageManager!!.getPath() + File.separator + uid + "." + File(images[uid]).extension
        return null
    }

    fun getFontPath(uid: Long) : String? {
        if (fonts.containsKey(uid) && storageManager != null)
            return storageManager!!.getPath() + File.separator + uid + "." + File(fonts[uid]).extension
        return null
    }

    fun removeUnusedFiles () {
        if (parentBoard != null || storageManager == null) return
        val sm = storageManager!!
        for ((key) in images) {
            if (!isImageUsed(objects, key.toString())) {
                sm.removeImage(key)
                images.remove(key)
            }
        }
        for ((key) in fonts) {
            if (!isFontUsed(objects, key)) {
                sm.removeFont(key)
                fonts.remove(key)
                FontCache.fonts.remove(key)
            }
        }
    }

    private fun isImageUsed (objs: List<BaseObject>, ImageUID: String) : Boolean {
        objs.forEach {
            if (it is ImageObject && it.imageSource == ImageSource.USER && it.src == ImageUID)
                return true
            if (it is BoardObject && isImageUsed(it.objects, ImageUID))
                return true
        }
        return false
    }

    private fun isFontUsed (objs: List<BaseObject>, FontUID: Long) : Boolean {
        objs.forEach {
            if (it is TextObject && it.fontUID == FontUID)
                return true
            if (it is BoardObject && isFontUsed(it.objects, FontUID))
                return true
        }
        return false
    }

    override fun init (ctx: Context, board: BoardObject?) {
        styles.init()
        super.init(ctx, board)
        for (obj in _objects) {
            obj.init(ctx, this)
            obj.addChangeListener {
                change()
            }
        }
        _xposMoving = xpos
        _yposMoving = ypos
        _widthMoving = width
        _heightMoving = height
        calculateHandles()
    }

    override fun close () {
        super.close()
        renderedBitmap?.recycle()
    }

    override fun canDirectlyChangeWidth () = false
    override fun canDirectlyChangeHeight () = false

    override fun getRenderables (d: Deserializer, forceReload: Boolean) : Array<Renderable> {
        if (!forceReload && !hasChanged)
            return renderables.toTypedArray()
        if (alpha == 0)
            return emptyArray()
        renderables.clear()
        for (obj in _objects) {
            renderables.addAll((obj as IRenderable).getRenderables(d, forceReload))
        }
        if (renderedBitmap == null)
            renderedBitmap = Bitmap.createBitmap(d.dipToPxI(width), d.dipToPxI(height), Bitmap.Config.ARGB_8888)
        else if (hasChangedSize) {
            renderedBitmap = Bitmap.createBitmap(d.dipToPxI(width), d.dipToPxI(height), Bitmap.Config.ARGB_8888)
        }
        Renderer.drawRenderables(renderables.toTypedArray(), Canvas(renderedBitmap), false)
        renderables.clear()
        val paint = Paint()
        paint.alpha = alpha
        val position = Renderable.Position(d.dipToPxF(actualXpos), d.dipToPxF(actualYpos), rotation, d.dipToPxF(width)/2F, d.dipToPxF(height)/2F)
        renderables.add(Renderable(Renderable.Type.IMAGE, renderedBitmap!!, position, paint))
        return renderables.toTypedArray()
    }

    fun getObjectAtPosition (x: Int, y: Int) : BaseObject? {
        var i = _objects.size
        var boundPath: Path
        var pointPath: Path
        val matrix = Matrix()
        while (--i >= 0) {
            val o = _objects[i]
            boundPath = Path()
            pointPath = Path()
            boundPath.addRect(o.actualXpos.toFloat(), o.actualYpos.toFloat(), (o.actualXpos+o.width).toFloat(), (o.actualYpos+o.height).toFloat(), Path.Direction.CW)
            matrix.setRotate(o.rotation, (o.actualXpos+(o.width/2F)).toFloat(), (o.actualYpos+(o.height/2F)).toFloat())
            boundPath.transform(matrix)
            pointPath.addRect(x.toFloat(), y.toFloat(), x.toFloat()+0.1F, y.toFloat()+0.1F, Path.Direction.CW)
            if (boundPath.op(pointPath, Path.Op.INTERSECT) && !boundPath.isEmpty)
                return o
        }
        return null
    }

    fun getObjectWithUID (uid: Long) : BaseObject? {
        for (obj in _objects) {
            if (obj.uid == uid)
                return obj
        }
        return null
    }


    override fun styleIsUsed(style: BaseStyle): Boolean {
        if (super.styleIsUsed(style)) return true
        for (obj in _objects) {
            if (obj.styleIsUsed(style))
                return true
        }
        return false
    }


    override fun clone(): BoardObject {
        val obj = BoardObject()
        applyBaseClone(obj)
        obj.gridSize = gridSize
        obj.gridInterval = gridInterval
        for (o in objects) {
            obj.addObject(o.clone())
        }
        return obj
    }

    override fun export(ec: ExportContainer, saveToContainer: Boolean) : List<BaseObject> {
        val list = super.export(ec, saveToContainer)
        val newObj = list[0] as BoardObject
        val newList = mutableListOf<BaseObject>()
        newObj.objects.forEach {
            var copyOnlyFirst = true
            if (it is CopyObject && it.source != null && !newList.contains(it.source!!))
                copyOnlyFirst = false
            it.export(ec, false).forEachIndexed { index, exp ->
                if (!copyOnlyFirst || index == 0)
                    newList.add(exp)
            }
            it.close()
            if (it is CopyObject) {
                it.source?.copies?.minus(1)
            }
        }
        newObj.objects = newList
        return list
    }


    class CannotDeleteCopiedObjectException : Exception() {}

}
