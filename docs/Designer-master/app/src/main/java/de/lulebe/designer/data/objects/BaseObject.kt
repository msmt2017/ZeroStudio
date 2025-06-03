package de.lulebe.designer.data.objects

import android.content.Context
import android.graphics.*
import android.util.Log
import de.lulebe.designer.data.Deserializer
import de.lulebe.designer.data.ExportContainer
import de.lulebe.designer.data.UIDGenerator
import de.lulebe.designer.data.styles.BaseStyle
import de.lulebe.designer.data.styles.BoxStyle


abstract class BaseObject : IRenderable, Cloneable {


    enum class HorizontalOrigin {
        LEFT, RIGHT, CENTER
    }
    enum class VerticalOrigin {
        TOP, BOTTOM, CENTER
    }


    //listeners
    @Transient
    protected var listeners = mutableListOf<() -> Unit>()
    fun addChangeListener (l: () -> Unit) {
        listeners.add(l)
    }
    fun removeChangeListener (l: () -> Unit) {
        listeners.remove(l)
    }
    fun removeAllChangeListeners () {
        listeners.clear()
    }
    protected fun change () {
        hasChanged = true
        for (listener in listeners) {
            listener()
        }
    }

    @Transient
    protected var _parentBoard: BoardObject? = null
    var parentBoard: BoardObject?
        get() = _parentBoard
        set(value) {
            _parentBoard = value
        }

    @Transient
    private val handles = Array<Rect>(4, { Rect() })

    @Transient
    protected val renderables = mutableListOf<Renderable>()

    @Transient
    protected var _hasChanged = true
    @Transient
    var hasChanged = _hasChanged
        get() = _hasChanged

    @Transient
    var copies = 0

    //id
    val uid: Long = UIDGenerator.generateUID()


    //name
    protected var _name: String = "Object"
    var name: String
        get() = _name
        set(value) {
            _name = value
            change()
        }


    //xpos
    protected var _xpos: Int = 0
    var xpos: Int
        get() = _xpos
        set(value) {
            val oldActual = actualXpos
            _xpos = value
            if (xposMoving == oldActual)
                xposMoving = actualXpos
            change()
        }


    @Transient
    protected var _xposMoving: Int = actualXpos
    var xposMoving: Int
        get() = _xposMoving
        set(value) {
            _xposMoving = value
            calculateHandles()
        }

    //xpos origin
    protected var _xposOrigin = HorizontalOrigin.LEFT
    var xposOrigin: HorizontalOrigin
        get() = _xposOrigin
        set(value) {
            _xposOrigin = value
            _xposMoving = actualXpos
            calculateHandles()
            change()
        }

    //actual xpos
    val actualXpos: Int
        get() {
            if (_parentBoard == null || _xposOrigin == HorizontalOrigin.LEFT) return _xpos
            when (_xposOrigin) {
                HorizontalOrigin.RIGHT -> { return _parentBoard!!.width - _xpos - _width }
                else -> { return _parentBoard!!.width/2 - _width/2 + _xpos }
            }
        }


    //ypos
    protected var _ypos: Int = 0
    var ypos: Int
        get() = _ypos
        set(value) {
            val oldActual = actualYpos
            _ypos = value
            if (yposMoving == oldActual)
                yposMoving = actualYpos
            change()
        }

    @Transient
    protected var _yposMoving: Int = actualYpos
    var yposMoving: Int
        get() = _yposMoving
        set(value) {
            _yposMoving = value
            calculateHandles()
        }

    //ypos origin
    protected var _yposOrigin = VerticalOrigin.TOP
    var yposOrigin: VerticalOrigin
        get() = _yposOrigin
        set(value) {
            _yposOrigin = value
            _yposMoving = actualYpos
            calculateHandles()
            change()
        }

    //actual ypos
    val actualYpos: Int
        get() {
            if (_parentBoard == null || _yposOrigin == VerticalOrigin.TOP) return _ypos
            when (_yposOrigin) {
                VerticalOrigin.BOTTOM -> { return _parentBoard!!.height - _ypos - _height }
                else -> { return _parentBoard!!.height/2 - _height/2 + _ypos }
            }
        }


    //width
    protected var _width: Int = 50
    open var width: Int
        get() = _width
        set(value) {
            val oldWidth = _width
            _width = value
            _lockToParentWidth = false
            _xposMoving = actualXpos
            if (_boxStyle != null)
                boxStyle = null
            if (widthMoving == oldWidth)
                widthMoving = value
            change()
        }

    @Transient
    protected var _widthMoving: Int = _width
    var widthMoving: Int
        get() = _widthMoving
        set(value) {
            _widthMoving = value
            calculateHandles()
        }

    //locked width
    protected var _lockToParentWidth = false
    var lockToParentWidth: Boolean
        get() = _lockToParentWidth
        set(value) {
            if (_parentBoard == null || !canDirectlyChangeWidth()) return
            if (_boxStyle != null)
                boxStyle = null
            width = _parentBoard!!.width
            _lockToParentWidth = value
        }


    //height
    protected var _height: Int = 50
    open var height: Int
        get() = _height
        set(value) {
            val oldHeight = _height
            _height = value
            _lockToParentHeight = false
            _yposMoving = actualYpos
            if (_boxStyle != null)
                boxStyle = null
            if (heightMoving == oldHeight)
                heightMoving = value
            change()
        }

    @Transient
    protected var _heightMoving: Int = _height
    var heightMoving: Int
        get() = _heightMoving
        set(value) {
            _heightMoving = value
            calculateHandles()
        }

    //locked height
    protected var _lockToParentHeight = false
    var lockToParentHeight: Boolean
        get() = _lockToParentHeight
        set(value) {
            if (_parentBoard == null || !canDirectlyChangeHeight()) return
            if (_boxStyle != null)
                boxStyle = null
            height = _parentBoard!!.height
            _lockToParentHeight = value
        }

    @Transient
    private val rotationMatrix = Matrix()
    // rotation
    private var _rotation: Float = 0F
    var rotation: Float
        get() = _rotation
        set(value) {
            _rotation = value
            calculateHandles()
            change()
        }


    private var _boxStyleUID: Long? = null
    @Transient
    protected var _boxStyle: BoxStyle? = null
    var boxStyle: BoxStyle?
        get() = _boxStyle
        set(value) {
            if (!canAcceptBoxStyle()) {
                _boxStyle = null
                _boxStyleUID = null
                change()
            } else {
                _boxStyle?.removeChangeListener(_boxStyleChangeListener)
                if (value != null) {
                    _lockToParentWidth = false
                    _lockToParentHeight = false
                    _boxStyle = value
                    _boxStyleUID = value.uid
                    value.addChangeListener(_boxStyleChangeListener)
                    _boxStyleChangeListener()
                } else {
                    _boxStyle = null
                    _boxStyleUID = null
                    change()
                }
            }
        }

    @Transient
    private var _boxStyleChangeListener = {
        if (canDirectlyChangeWidth()) {
            if (_widthMoving == _width)
                _widthMoving = boxStyle!!.width
            _width = boxStyle!!.width
        }
        if (canDirectlyChangeHeight()) {
            if (_heightMoving == _height)
                _heightMoving = boxStyle!!.height
            _height = boxStyle!!.height
        }
        calculateHandles()
        change()
    }
    open protected var boxStyleChangeListener: () -> Unit
        get() = _boxStyleChangeListener
        set(value) {
            _boxStyle?.removeChangeListener(_boxStyleChangeListener)
            _boxStyleChangeListener = value
            _boxStyle?.addChangeListener(value)
        }


    open fun close () {
        removeAllChangeListeners()
    }

    open fun canDirectlyChangeWidth() : Boolean {
        return true
    }
    open fun canDirectlyChangeHeight() : Boolean {
        return true
    }
    open fun canAcceptBoxStyle() = true

    open fun extractBoxStyle() : BoxStyle {
        val bs = BoxStyle()
        bs.name = name + " Box style"
        bs.width = width
        bs.height = height
        return bs
    }


    open fun styleIsUsed (style: BaseStyle) : Boolean {
        return style == boxStyle
    }


    fun movingTo(x: Int = xpos, y: Int = ypos) {
        xposMoving = x
        yposMoving = y
        calculateHandles()
    }
    open fun applyMovement() {
        if (_xposOrigin == HorizontalOrigin.LEFT || _parentBoard == null)
            _xpos = xposMoving
        else if (_xposOrigin == HorizontalOrigin.RIGHT)
            _xpos = _parentBoard!!.width - xposMoving - widthMoving
        else if (_xposOrigin == HorizontalOrigin.CENTER)
            _xpos = xposMoving + widthMoving/2 - _parentBoard!!.width/2
        if (_yposOrigin == VerticalOrigin.TOP || _parentBoard == null)
            _ypos = yposMoving
        else if (_yposOrigin == VerticalOrigin.BOTTOM)
            _ypos = _parentBoard!!.height - yposMoving - heightMoving
        else if (_yposOrigin == VerticalOrigin.CENTER)
            _ypos = yposMoving + heightMoving/2 - _parentBoard!!.height/2
        _width = widthMoving
        _height = heightMoving
        calculateHandles()
        change()
    }
    fun resetHandles() {
        xposMoving = actualXpos
        yposMoving = actualYpos
        widthMoving = width
        heightMoving = height
        calculateHandles()
    }
    fun setHandlePosition (handle: Int, x: Int, y: Int) {
        when (handle) {
            1 -> {
                if (x >= xposMoving + widthMoving)
                    return
                _widthMoving += -(x - xposMoving)
                _xposMoving = x
            }
            2 -> {
                if (y >= yposMoving + heightMoving)
                    return
                _heightMoving += -(y - yposMoving)
                _yposMoving = y
            }
            3 -> {
                val add = x - (xposMoving + widthMoving)
                if (-add >= widthMoving)
                    return
                _widthMoving += add
            }
            4 -> {
                val add = y - (yposMoving + heightMoving)
                if (-add >= heightMoving)
                    return
                _heightMoving += add
            }
        }
        calculateHandles()
    }


    open fun init (ctx: Context, board: BoardObject?) {
        parentBoard = board
        if (board != null)
            boxStyle = board.styles.boxStyles[_boxStyleUID]
        _xposMoving = actualXpos
        _yposMoving = actualYpos
        _widthMoving = width
        _heightMoving = height
        calculateHandles()
    }


    override fun getBoundRect (d: Deserializer, xOffset: Float, yOffset: Float): RectF {
        return RectF(d.dipToPxF(xposMoving) + xOffset,
                d.dipToPxF(yposMoving) + yOffset,
                d.dipToPxF(xposMoving+widthMoving) + xOffset,
                d.dipToPxF(yposMoving+heightMoving) + yOffset)
    }


    protected fun calculateHandles () {
        var rotRad = Math.toRadians(rotation.toDouble())
        val rad90deg = Math.toRadians(90.0)
        val centerX = xposMoving + widthMoving/2
        val centerY = yposMoving + heightMoving/2
        calculateHandle(handles[0], centerX - (Math.cos(rotRad)*widthMoving/2).toInt(),
                centerY - (Math.sin(rotRad)*widthMoving/2).toInt())
        rotRad += rad90deg
        calculateHandle(handles[1], centerX - (Math.cos(rotRad)*heightMoving/2).toInt(),
                centerY - (Math.sin(rotRad)*heightMoving/2).toInt())
        rotRad += rad90deg
        calculateHandle(handles[2], centerX - (Math.cos(rotRad)*widthMoving/2).toInt(),
                centerY - (Math.sin(rotRad)*widthMoving/2).toInt())
        rotRad += rad90deg
        calculateHandle(handles[3], centerX - (Math.cos(rotRad)*heightMoving/2).toInt(),
                centerY - (Math.sin(rotRad)*heightMoving/2).toInt())
        Log.d("HANDLES", "calculated")
    }

    private fun calculateHandle (handle: Rect, cx: Int, cy: Int) {
        handle.left = cx - 12
        handle.top = cy - 12
        handle.right = cx + 12
        handle.bottom = cy + 12
    }

    fun getHandleAt (x: Int, y: Int) : Int {
        var found = -1
        var i = 1
        val boundPath = Path()
        boundPath.addRect(actualXpos.toFloat(), actualYpos.toFloat(), (actualXpos+width).toFloat(), (actualYpos+height).toFloat(), Path.Direction.CW)
        val matrix = Matrix()
        matrix.setRotate(rotation, (actualXpos+(width/2F)).toFloat(), (actualXpos+(height/2F)).toFloat())
        boundPath.transform(matrix)
        val pointPath = Path()
        pointPath.addRect(x.toFloat(), y.toFloat(), x.toFloat()+0.1F, y.toFloat()+0.1F, Path.Direction.CW)
        if (boundPath.op(pointPath, Path.Op.INTERSECT) && !boundPath.isEmpty)
            found = 0
        if (rotation == 0F)
            for (handle in handles) {
                if ((i % 2 == 1 && !canDirectlyChangeWidth()) || (i % 2 == 0 && !canDirectlyChangeHeight()))
                    continue
                if (x >= handle.left && x <= handle.right && y >= handle.top && y <= handle.bottom) {
                    found = i
                    break
                }
                i++
            }
        return found
    }

    override fun getHandleRenderables(d: Deserializer, xOffset: Float, yOffset: Float, showHandles: Boolean): Array<Renderable> {
        val elems = mutableListOf<Renderable>()
        val boxPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        boxPaint.color = Color.parseColor("#FF8800")
        boxPaint.style = Paint.Style.STROKE
        boxPaint.strokeWidth = d.pixelFac
        val position = Renderable.Position(0F, 0F, rotation,
                d.dipToPxF(xposMoving+(widthMoving/2))+xOffset,
                d.dipToPxF(yposMoving+(heightMoving/2))+yOffset)
        elems.add(Renderable(Renderable.Type.RECT, getBoundRect(d, xOffset, yOffset), position, boxPaint))
        if (!showHandles)
            return elems.toTypedArray()
        if (rotation == 0F && (canDirectlyChangeHeight() || canDirectlyChangeWidth())) {
            val handlePaint = Paint(Paint.ANTI_ALIAS_FLAG)
            handlePaint.color = Color.parseColor("#FF8800")
            if (canDirectlyChangeWidth()) {
                var shape = RectF(d.dipToPxF(handles[0].left) + xOffset,
                        d.dipToPxF(handles[0].top) + yOffset,
                        d.dipToPxF(handles[0].right) + xOffset,
                        d.dipToPxF(handles[0].bottom) + yOffset)
                elems.add(Renderable(Renderable.Type.OVAL, shape, Renderable.Position(0F, 0F, 0F, 0F, 0F), handlePaint))
                shape = RectF(d.dipToPxF(handles[2].left) + xOffset,
                        d.dipToPxF(handles[2].top) + yOffset,
                        d.dipToPxF(handles[2].right) + xOffset,
                        d.dipToPxF(handles[2].bottom) + yOffset)
                elems.add(Renderable(Renderable.Type.OVAL, shape, Renderable.Position(0F, 0F, 0F, 0F, 0F), handlePaint))
            }
            if (canDirectlyChangeHeight()) {
                var shape = RectF(d.dipToPxF(handles[1].left) + xOffset,
                        d.dipToPxF(handles[1].top) + yOffset,
                        d.dipToPxF(handles[1].right) + xOffset,
                        d.dipToPxF(handles[1].bottom) + yOffset)
                elems.add(Renderable(Renderable.Type.OVAL, shape, Renderable.Position(0F, 0F, 0F, 0F, 0F), handlePaint))
                shape = RectF(d.dipToPxF(handles[3].left) + xOffset,
                        d.dipToPxF(handles[3].top) + yOffset,
                        d.dipToPxF(handles[3].right) + xOffset,
                        d.dipToPxF(handles[3].bottom) + yOffset)
                elems.add(Renderable(Renderable.Type.OVAL, shape, Renderable.Position(0F, 0F, 0F, 0F, 0F), handlePaint))
            }
        }
        return elems.toTypedArray()
    }


    open fun getMainColor () : Int {
        return Color.BLACK
    }

    open fun export (ec: ExportContainer, saveToContainer: Boolean) : List<BaseObject> {
        if (ec.newUIDs.containsKey(uid))
            return listOf(ec.objects[ec.newUIDs[uid]]!!)
        val newObj = this.clone()
        if (saveToContainer)
            ec.objects.put(newObj.uid, newObj)
        ec.newUIDs.put(uid, newObj.uid)
        newObj.boxStyle = boxStyle?.export(ec)
        return listOf(newObj)
    }

    open fun updateStyleToNewId(oldStyle: BaseStyle, newStyle: BaseStyle) {
        if (oldStyle is BoxStyle && this.boxStyle?.uid == oldStyle.uid)
            this.boxStyle = newStyle as BoxStyle
    }

    override public abstract fun clone () : BaseObject

}