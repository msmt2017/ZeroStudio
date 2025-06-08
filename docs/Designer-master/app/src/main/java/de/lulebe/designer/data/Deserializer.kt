package de.lulebe.designer.data


class Deserializer(val pixelFac: Float) {

    fun dipToPxF(dip: Int) : Float {
        return (dip * pixelFac)
    }
    fun dipToPxI(dip: Int) : Int {
        return (dip * pixelFac).toInt()
    }
    fun dipToPxF(dip: Float) : Float {
        return (dip * pixelFac)
    }
    fun dipToPxI(dip: Float) : Int {
        return (dip * pixelFac).toInt()
    }

}