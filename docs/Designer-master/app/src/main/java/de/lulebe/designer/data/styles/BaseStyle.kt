package de.lulebe.designer.data.styles

import de.lulebe.designer.data.ExportContainer
import de.lulebe.designer.data.UIDGenerator


abstract class BaseStyle : Cloneable {

    //listeners
    @Transient
    private var listeners = mutableListOf<() -> Unit>()
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
        for (listener in listeners) {
            listener()
        }
    }


    val uid: Long = UIDGenerator.generateUID()


    protected var _name = "Style"
    var name: String
        get() = _name
        set(value) {
            _name = value
            change()
        }

    override public abstract fun clone () : BaseStyle

    abstract fun export (ec: ExportContainer) : BaseStyle

}