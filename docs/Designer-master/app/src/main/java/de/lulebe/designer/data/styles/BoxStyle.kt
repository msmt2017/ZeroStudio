package de.lulebe.designer.data.styles

import de.lulebe.designer.data.ExportContainer


class BoxStyle : BaseStyle() {


    private var _width = 50
    var width: Int
        get() = _width
        set(value) {
            _width = value
            change()
        }


    private var _height = 50
    var height: Int
        get() = _height
        set(value) {
            _height = value
            change()
        }


    private var _cornerRadius = 0
    var cornerRadius: Int
        get() = _cornerRadius
        set(value) {
            _cornerRadius = value
            change()
        }


    override fun clone () : BoxStyle {
        val bs = BoxStyle()
        bs.name = _name
        bs.width = _width
        bs.height = _height
        bs.cornerRadius = _cornerRadius
        return bs
    }

    override fun export(ec: ExportContainer): BoxStyle {
        if (ec.newUIDs.containsKey(uid))
            return ec.boxStyles[ec.newUIDs[uid]]!!
        val newStyle = clone()
        ec.newUIDs.put(uid, newStyle.uid)
        ec.boxStyles.put(newStyle.uid, newStyle)
        return newStyle
    }

}