package de.lulebe.designer.data.objects

import android.content.Context
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import de.lulebe.designer.data.Deserializer
import de.lulebe.designer.data.ExportContainer
import de.lulebe.designer.data.FontCache
import de.lulebe.designer.data.styles.BaseStyle
import de.lulebe.designer.data.styles.ColorStyle
import de.lulebe.designer.data.styles.TextStyle


class TextObject : SourceObject() {

    @Transient
    private var ctx: Context? = null

    private var _text = "Text"
    var text: String
        get() = _text
        set(value) {
            _text = value
            calcSizes()
            change()
        }


    private var _alignment = Layout.Alignment.ALIGN_NORMAL
    var alignment: Layout.Alignment
        get() = _alignment
        set(value) {
            if (_textStyle != null)
                textStyle = null
            _alignment = value
            calcSizes()
            change()
        }


    private var _textColor: Int = Color.BLACK
    var textColor: Int
        get() = _textColor
        set(value) {
            if (_textColorStyle != null)
                textColorStyle = null
            _textColor = value
            change()
        }


    private var _fontSize: Int = 16
    var fontSize: Int
        get() = _fontSize
        set(value) {
            if (_textStyle != null)
                textStyle = null
            _fontSize = value
            calcSizes()
            change()
        }

    private var _fontUID: Long = 0
    var fontUID: Long
        get() = _fontUID
        set(value) {
            if (ctx != null && _parentBoard != null) {
                FontCache.loadFont(value, _parentBoard!!, ctx!!) {
                    if (_textStyle != null)
                        textStyle = null
                    _fontUID = value
                    typeFace = it
                    calcSizes()
                    change()
                }
            }
        }

    @Transient
    private var typeFace: Typeface = Typeface.DEFAULT

    override var height: Int
        get() = super.height
        set(value) {
        }

    override var width: Int
        get() = super.width
        set(value) {
            _width = value
            calcSizes()
            change()
        }

    init {
        _name = "Text"
    }


    private var _textStyleUID: Long? = null
    @Transient
    private var _textStyle: TextStyle? = null
    var textStyle: TextStyle?
        get() = _textStyle
        set(value) {
            _textStyle?.removeChangeListener(textStyleChangeListener)
            if (value != null) {
                _textStyle = value
                _textStyleUID = value.uid
                value.addChangeListener(textStyleChangeListener)
                textStyleChangeListener()
            } else {
                _textStyleUID = null
                _textStyle = null
            }
        }

    @Transient
    private var textStyleChangeListener = {
        _alignment = textStyle!!.alignment
        _fontSize = textStyle!!.fontSize
        if (_parentBoard != null && ctx != null) {
            _fontUID = textStyle!!.font
            FontCache.loadFont(textStyle!!.font, _parentBoard!!, ctx!!) {
                typeFace = it
                calcSizes()
                change()
            }
        }
    }


    private var _textColorStyleUID: Long? = null
    @Transient
    private var _textColorStyle: ColorStyle? = null
    var textColorStyle: ColorStyle?
        get() = _textColorStyle
        set(value) {
            _textColorStyle?.removeChangeListener(textColorStyleChangeListener)
            if (value != null) {
                _textColorStyle = value
                _textColorStyleUID = value.uid
                value.addChangeListener(textColorStyleChangeListener)
                textColorStyleChangeListener()
            } else {
                _textColorStyleUID = null
                _textColorStyle = null
                change()
            }
        }

    @Transient
    private var textColorStyleChangeListener = {
        _textColor = textColorStyle!!.color
        change()
    }




    init {
        _width = 100
    }

    override fun canDirectlyChangeHeight() = false


    fun extractTextStyle(): TextStyle {
        val ts = TextStyle()
        ts.name = name + " Text style"
        ts.alignment = alignment
        ts.fontSize = fontSize
        ts.font = fontUID
        return ts
    }

    fun extractTextColorStyle(): ColorStyle {
        val cs = ColorStyle()
        cs.name = name + " Color style"
        cs.color = textColor
        return cs
    }


    override fun applyMovement() {
        _xpos = xposMoving
        _ypos = yposMoving
        _width = widthMoving
        calcSizes()
        calculateHandles()
        change()
    }

    override fun getRenderables(d: Deserializer, forceReload: Boolean): Array<Renderable> {
        if (!forceReload && !hasChanged)
            return renderables.toTypedArray()
        renderables.clear()

        if (alpha == 0) {
            hasChanged = false
            return emptyArray()
        }
        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG)
        paint.color = textColor
        paint.textSize = d.dipToPxF(fontSize)
        paint.alpha = (alpha * paint.alpha) / 255
        paint.typeface = typeFace
        val layout = StaticLayout(_text, paint, d.dipToPxI(width), alignment, 1F, 0F, false)
        val position = Renderable.Position(d.dipToPxF(actualXpos), d.dipToPxF(actualYpos), rotation, d.dipToPxF(layout.width)/2F, d.dipToPxF(layout.height)/2F)
        val r = Renderable(Renderable.Type.TEXT, layout, position, paint)
        renderables.add(r)
        hasChanged = false
        return renderables.toTypedArray()
    }

    private fun calcSizes () {
        val paint = TextPaint()
        paint.textSize = fontSize.toFloat()
        paint.typeface = typeFace
        val layout = StaticLayout(_text, paint, width.toInt(), alignment, 1F, 0F, false)
        _width = layout.width
        _widthMoving = layout.width
        _height = layout.height
        _heightMoving = layout.height
        calculateHandles()
    }

    override fun getMainColor(): Int {
        return textColor
    }


    override fun styleIsUsed(style: BaseStyle): Boolean {
        if (super.styleIsUsed(style) || style == textStyle) return true
        return false
    }

    override fun init(ctx: Context, board: BoardObject?) {
        super.init(ctx, board)
        this.ctx = ctx
        if (board != null) {
            textStyle = board.styles.textStyles[_textStyleUID]
            textColorStyle = board.styles.colorStyles[_textColorStyleUID]
            FontCache.loadFont(_fontUID, board, ctx) {
                typeFace = it
                calcSizes()
                change()
            }
        }
    }

    override fun clone(): TextObject {
        val obj = TextObject()
        applyBaseClone(obj)
        obj.text = _text
        obj.alignment = _alignment
        obj.textColor = _textColor
        obj.fontSize = _fontSize
        obj.textStyle = textStyle
        obj.textColorStyle = textColorStyle
        return obj
    }

    override fun export(ec: ExportContainer, saveToContainer: Boolean) : List<BaseObject> {
        val list = super.export(ec, saveToContainer)
        val newObj = list[0] as TextObject
        if (fontUID != 0L)
            ec.fonts.add(fontUID)
        newObj.textStyle = textStyle?.export(ec)
        newObj.textColorStyle = textColorStyle?.export(ec)
        return list
    }

}