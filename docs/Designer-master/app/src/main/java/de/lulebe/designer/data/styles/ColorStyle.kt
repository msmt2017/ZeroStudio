package de.lulebe.designer.data.styles

import android.graphics.Color
import de.lulebe.designer.data.ExportContainer


class ColorStyle: BaseStyle() {


    private var _color = Color.BLACK
    var color: Int
        get() = _color
        set(value) {
            _color = value
            change()
        }


    override fun clone () : ColorStyle {
        val cs = ColorStyle()
        cs.name = _name
        cs.color = _color
        return cs
    }

    override fun export(ec: ExportContainer): ColorStyle {
        if (ec.newUIDs.containsKey(uid))
            return ec.colorStyles[ec.newUIDs[uid]]!!
        val newStyle = clone()
        ec.newUIDs.put(uid, newStyle.uid)
        ec.colorStyles.put(newStyle.uid, newStyle)
        return newStyle
    }


}