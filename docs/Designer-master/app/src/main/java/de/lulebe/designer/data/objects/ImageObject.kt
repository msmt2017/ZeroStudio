package de.lulebe.designer.data.objects

import android.content.Context
import android.graphics.*
import android.support.v7.graphics.Palette
import de.lulebe.designer.data.Deserializer
import de.lulebe.designer.data.ExportContainer
import de.lulebe.designer.data.ImageSource
import de.lulebe.designer.data.styles.BaseStyle
import de.lulebe.designer.data.styles.ColorStyle
import java.io.File
import java.lang.ref.WeakReference
import kotlin.concurrent.thread


class ImageObject() : SourceObject() {


    constructor(context: Context) : this() {
        ctx = WeakReference(context)
    }


    @Transient
    private var ctx: WeakReference<Context>? = null

    @Transient
    private var board: WeakReference<BoardObject>? = null

    @Transient
    private var mainColorCached = Color.BLACK

    private var _imageSource: ImageSource = ImageSource.USER
    var imageSource: ImageSource
        get() = _imageSource
        set(value) {
            _imageSource = value
            change()
        }

    private var _src: String = ""
    var src: String
        get() = _src
        set(value) {
            _src = value
            change()
        }

    private var _keepRatio: Boolean = true
    var keepRatio: Boolean
        get() = _keepRatio
        set(value) {
            _keepRatio = value
            change()
        }

    private var _tintColor: Int = Color.BLACK
    var tintColor: Int
        get() = _tintColor
        set(value) {
            _tintColor = value
            tintColorStyle = null
            change()
        }

    private var _tinted: Boolean = false
    var tinted: Boolean
        get() = _tinted
        set(value) {
            _tinted = value
            change()
        }

    init {
        _name = "Image"
    }

    private var _tintColorStyleUID: Long? = null
    @Transient
    private var _tintColorStyle: ColorStyle? = null
    var tintColorStyle: ColorStyle?
        get() = _tintColorStyle
        set(value) {
            _tintColorStyle?.removeChangeListener(tintColorStyleChangeListener)
            if (value != null) {
                _tintColorStyle = value
                _tintColorStyleUID = value.uid
                value.addChangeListener(tintColorStyleChangeListener)
                tintColorStyleChangeListener()
            } else {
                _tintColorStyleUID = null
                _tintColorStyle = null
                change()
            }
        }

    @Transient
    private var tintColorStyleChangeListener = {
        _tintColor = tintColorStyle!!.color
        change()
    }


    fun setImage (imageSource: ImageSource, file: String) {
        _imageSource = imageSource
        _src = file
        change()
    }

    override fun close () {
        super.close()

    }

    override fun getRenderables(d: Deserializer, forceReload: Boolean): Array<Renderable> {
        if (!forceReload && !hasChanged)
            return renderables.toTypedArray()
        renderables.clear()
        if (alpha == 0 || ctx == null || ctx!!.get() == null || src.equals("")) {
            hasChanged = false
            return emptyArray()
        }
        val rawBmp : Bitmap
        if (imageSource != ImageSource.USER) {
            val path = imageSource.name + File.separator + src
            rawBmp = BitmapFactory.decodeStream(ctx!!.get().assets.open(path))
        } else {
            var imageUID = 0L
            try {
                imageUID = src.toLong()
            } catch (e: NumberFormatException) {}
            if (imageUID == 0L || board == null || board!!.get() == null) {
                hasChanged = false
                return emptyArray()
            }
            val path = board!!.get().getImagePath(imageUID)
            if (!File(path).exists() || !File(path).canRead()) {
                hasChanged = false
                return emptyArray()
            }
            rawBmp = BitmapFactory.decodeFile(path)
        }
        val maxWidth = d.dipToPxI(width)
        val maxHeight = d.dipToPxI(height)
        val finalWidth: Int
        val finalHeight: Int
        var finalXPos = d.dipToPxF(actualXpos)
        var finalYPos = d.dipToPxF(actualYpos)
        if (keepRatio) {
            val targetRatio = width.toFloat() / height.toFloat()
            val sourceRatio = rawBmp.width.toFloat() / rawBmp.height.toFloat()
            if (sourceRatio < targetRatio) { //fit height
                finalHeight = maxHeight
                finalWidth = (rawBmp.width.toFloat() * (maxHeight.toFloat() / rawBmp.height.toFloat())).toInt()
                finalXPos += (maxWidth - finalWidth) / 2
            } else { //fit width
                finalWidth = maxWidth
                finalHeight = (rawBmp.height.toFloat() * (maxWidth.toFloat() / rawBmp.width.toFloat())).toInt()
                finalYPos += (maxHeight - finalHeight) / 2
            }
        } else {
            finalWidth = maxWidth
            finalHeight = maxHeight
        }
        val bmp = Bitmap.createScaledBitmap(
                rawBmp,
                finalWidth,
                finalHeight,
                true)
        rawBmp.recycle()
        thread {
            try {
                val palette = Palette.from(bmp).clearFilters().maximumColorCount(1).generate()
                if (palette.swatches.size > 0)
                    mainColorCached = palette.swatches[0].rgb
            } catch (e: Exception) {}
        }
        val paint = Paint()
        paint.alpha = alpha
        if (tinted)
            paint.colorFilter = PorterDuffColorFilter(tintColor, PorterDuff.Mode.SRC_IN)
        val position = Renderable.Position(finalXPos, finalYPos, rotation, d.dipToPxF(width)/2F, d.dipToPxF(height)/2F)
        renderables.add(Renderable(Renderable.Type.IMAGE, bmp, position, paint))
        hasChanged = false
        return renderables.toTypedArray()
    }

    override fun init(ctx: Context, board: BoardObject?) {
        super.init(ctx, board)
        this.ctx = WeakReference(ctx)
        if (board != null)
            this.board = WeakReference(board)
        if (board != null) {
            tintColorStyle = board.styles.colorStyles[_tintColorStyleUID]
        }
    }


    fun extractTintcolorStyle() : ColorStyle {
        val cs = ColorStyle()
        cs.name = name + " tint color"
        cs.color = tintColor
        return cs
    }


    override fun getMainColor(): Int {
        if (tinted)
            return _tintColor
        return mainColorCached
    }


    override fun clone(): ImageObject {
        val obj = ImageObject()
        applyBaseClone(obj)
        obj.imageSource = _imageSource
        obj.src = _src
        obj.tintColor = _tintColor
        obj.tinted = _tinted
        obj.tintColorStyle = tintColorStyle
        obj.keepRatio = _keepRatio
        return obj
    }

    override fun export(ec: ExportContainer, saveToContainer: Boolean) : List<BaseObject> {
        val list = super.export(ec, saveToContainer)
        val newObj = list[0] as ImageObject
        if (imageSource == ImageSource.USER && src != "")
            try {
                ec.images.add(src.toLong())
            } catch (e: NumberFormatException) {}
        newObj.tintColorStyle = tintColorStyle?.export(ec)
        return list
    }

    override fun updateStyleToNewId(oldStyle: BaseStyle, newStyle: BaseStyle) {
        super.updateStyleToNewId(oldStyle, newStyle)
        if (oldStyle is ColorStyle && tintColorStyle?.uid == oldStyle.uid)
            tintColorStyle = newStyle as ColorStyle
    }

}