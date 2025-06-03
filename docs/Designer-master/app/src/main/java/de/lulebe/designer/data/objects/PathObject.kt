package de.lulebe.designer.data.objects

import android.content.Context
import android.graphics.*
import de.lulebe.designer.data.Deserializer
import de.lulebe.designer.data.ExportContainer
import de.lulebe.designer.data.styles.ColorStyle


class PathObject : SourceObject() {

    enum class PointType {
        START, LINE, QUAD
    }

    val points: MutableList<Pair<Point, PointType>> = mutableListOf()


    //fillColor
    private var _fillColor: Int = Color.BLACK
    var fillColor: Int
        get() = _fillColor
        set(value) {
            if (_fillColorStyle != null)
                fillColorStyle = null
            _fillColor = value
            change()
        }



    //strokeColor
    private var _strokeColor: Int = Color.BLACK
    var strokeColor: Int
        get() = _strokeColor
        set(value) {
            if (_strokeColorStyle != null)
                strokeColorStyle = null
            _strokeColor = value
            change()
        }


    //strokeWidth
    private var _strokeWidth: Int = 0
    var strokeWidth: Int
        get() = _strokeWidth
        set(value) {
            _strokeWidth = value
            change()
        }

    private var _fillColorStyleUID: Long? = null
    @Transient
    private var _fillColorStyle: ColorStyle? = null
    var fillColorStyle: ColorStyle?
        get() = _fillColorStyle
        set(value) {
            _fillColorStyle?.removeChangeListener(fillColorStyleChangeListener)
            if (value != null) {
                _fillColorStyle = value
                _fillColorStyleUID = value.uid
                value.addChangeListener(fillColorStyleChangeListener)
                fillColorStyleChangeListener()
            } else {
                _fillColorStyle = null
                _fillColorStyleUID = null
                change()
            }
        }

    @Transient
    private var fillColorStyleChangeListener = {
        _fillColor = _fillColorStyle!!.color
        change()
    }


    private var _strokeColorStyleUID: Long? = null
    @Transient
    private var _strokeColorStyle: ColorStyle? = null
    var strokeColorStyle: ColorStyle?
        get() = _strokeColorStyle
        set(value) {
            _strokeColorStyle?.removeChangeListener(strokeColorStyleChangeListener)
            if (value != null) {
                _strokeColorStyle = value
                _strokeColorStyleUID = value.uid
                value.addChangeListener(strokeColorStyleChangeListener)
                strokeColorStyleChangeListener()
            } else {
                _strokeColorStyle = null
                _strokeColorStyleUID = null
                change()
            }
        }

    @Transient
    private var strokeColorStyleChangeListener = {
        _strokeColor = _strokeColorStyle!!.color
        change()
    }




    fun extractFillcolorStyle() : ColorStyle {
        val cs = ColorStyle()
        cs.name = name + " fill color"
        cs.color = fillColor
        return cs
    }

    fun extractStrokecolorStyle() : ColorStyle {
        val cs = ColorStyle()
        cs.name = name + " stroke color"
        cs.color = strokeColor
        return cs
    }



    init {
        _name = "Path"
    }

    override fun getRenderables(d: Deserializer, forceReload: Boolean): Array<Renderable> {
        if (!forceReload && !hasChanged)
            return renderables.toTypedArray()
        renderables.clear()
        if (alpha == 0 || points.size == 0) {
            hasChanged = false
            return emptyArray()
        }
        val pos = Renderable.Position(d.dipToPxF(xpos), d.dipToPxF(ypos),
                rotation, d.dipToPxF((xpos+width)/2), d.dipToPxF((ypos+height)/2))
        val path = createPath(d)
        if (strokeWidth > 0) {
            val paint = Paint()
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = d.dipToPxF(strokeWidth)
            paint.color = strokeColor
            renderables.add(Renderable(Renderable.Type.PATH, path, pos, paint))
        }
        val paint = Paint()
        paint.style = Paint.Style.FILL
        paint.color = fillColor
        renderables.add(Renderable(Renderable.Type.PATH, path, pos, paint))
        return renderables.toTypedArray()
    }

    private fun createPath (d: Deserializer? = null) : Path {
        val path = Path()
        if (d != null)
            points.forEachIndexed { i, it  ->
                when (it.second) {
                    PointType.START -> { path.moveTo(d.dipToPxF(it.first.x), d.dipToPxF(it.first.y)) }
                    PointType.LINE -> { path.lineTo(d.dipToPxF(it.first.x), d.dipToPxF(it.first.y)) }
                    PointType.QUAD -> {
                        if (i > 0)
                            path.quadTo(d.dipToPxF(it.first.x), d.dipToPxF(it.first.y),
                                    d.dipToPxF((points[i-1].first.x+it.first.x)/2),
                                    d.dipToPxF((points[i-1].first.y+it.first.y)/2)
                            )
                        else
                            path.lineTo(d.dipToPxF(it.first.x), d.dipToPxF(it.first.y))
                    }
                }
            }
        else
            points.forEachIndexed { i, it  ->
                when (it.second) {
                    PointType.START -> { path.moveTo(it.first.x.toFloat(), it.first.y.toFloat()) }
                    PointType.LINE -> { path.lineTo(it.first.x.toFloat(), it.first.y.toFloat()) }
                    PointType.QUAD -> {
                        if (i > 0)
                            path.quadTo(it.first.x.toFloat(), it.first.y.toFloat(),
                                    ((points[i-1].first.x+it.first.x)/2).toFloat(),
                                    ((points[i-1].first.y+it.first.y)/2).toFloat()
                            )
                        else
                            path.lineTo(it.first.x.toFloat(), it.first.y.toFloat())
                    }
                }
            }
        return path
    }

    private fun calcSizes () {
        val path = createPath()
        val bounds = RectF()
        path.computeBounds(bounds, true)
        _xpos = bounds.left.toInt()
        _ypos = bounds.top.toInt()
        width = (bounds.right-bounds.left).toInt()
        height = (bounds.top-bounds.bottom).toInt()
    }

    override fun init(ctx: Context, board: BoardObject?) {
        super.init(ctx, board)
    }

    override fun clone(): PathObject {
        val po = PathObject()
        applyBaseClone(po)
        points.forEach { po.points.add(Pair(Point(it.first.x, it.first.y), it.second)) }
        po.fillColor = _fillColor
        po.strokeColor = _strokeColor
        po.strokeWidth = _strokeWidth
        po.fillColorStyle = fillColorStyle
        po.strokeColorStyle = strokeColorStyle
        return po
    }

    override fun export(ec: ExportContainer, saveToContainer: Boolean): List<BaseObject> {
        val list = super.export(ec, saveToContainer)
        val newObj = list[0] as PathObject
        newObj.strokeColorStyle = strokeColorStyle?.export(ec)
        newObj.fillColorStyle = fillColorStyle?.export(ec)
        return list
    }
}