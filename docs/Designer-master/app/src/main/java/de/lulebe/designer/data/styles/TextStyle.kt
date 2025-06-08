package de.lulebe.designer.data.styles

import android.text.Layout
import de.lulebe.designer.data.ExportContainer


class TextStyle : BaseStyle() {


    private var _fontSize = 16
    var fontSize: Int
        get() = _fontSize
        set(value) {
            _fontSize = value
            change()
        }


    private var _font: Long = 0L
    var font: Long
        get() = _font
        set(value) {
            _font = value
            change()
        }

    private var _alignment = Layout.Alignment.ALIGN_NORMAL
    var alignment: Layout.Alignment
        get() = _alignment
        set(value) {
            _alignment = value
            change()
        }

    override fun clone(): TextStyle {
        val ts = TextStyle()
        ts.name = _name
        ts.fontSize = _fontSize
        ts.font = _font
        ts.alignment = _alignment
        return ts
    }

    override fun export(ec: ExportContainer): TextStyle {
        if (ec.newUIDs.containsKey(uid))
            return ec.textStyles[ec.newUIDs[uid]]!!
        val newStyle = clone()
        ec.newUIDs.put(uid, newStyle.uid)
        ec.textStyles.put(newStyle.uid, newStyle)
        return newStyle
    }

}