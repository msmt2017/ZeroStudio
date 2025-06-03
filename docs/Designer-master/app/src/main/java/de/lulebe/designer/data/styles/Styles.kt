package de.lulebe.designer.data.styles


class Styles {
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
        for (listener in listeners) {
            listener()
        }
    }

    private val _colorStyles: MutableMap<Long, ColorStyle> = mutableMapOf()
    val colorStyles: MutableMap<Long, ColorStyle>
        get() = _colorStyles

    private val _boxStyles: MutableMap<Long, BoxStyle> = mutableMapOf()
    val boxStyles: MutableMap<Long, BoxStyle>
        get() = _boxStyles

    private val _textStyles: MutableMap<Long, TextStyle> = mutableMapOf()
    val textStyles: MutableMap<Long, TextStyle>
        get() = _textStyles

    fun addColorStyle (cs: ColorStyle) {
        colorStyles.put(cs.uid, cs)
        cs.addChangeListener {
            change()
        }
        change()
    }
    fun removeColorStyle (cs: ColorStyle) {
        colorStyles.remove(cs.uid)
        cs.removeAllChangeListeners()
        change()
    }

    fun addBoxStyle (bs: BoxStyle) {
        boxStyles.put(bs.uid, bs)
        bs.addChangeListener {
            change()
        }
        change()
    }
    fun removeBoxStyle (bs: BoxStyle) {
        boxStyles.remove(bs.uid)
        bs.removeAllChangeListeners()
        change()
    }

    fun addTextStyle (ts: TextStyle) {
        textStyles.put(ts.uid, ts)
        ts.addChangeListener {
            change()
        }
        change()
    }
    fun removeTextStyle (ts: TextStyle) {
        textStyles.remove(ts.uid)
        ts.removeAllChangeListeners()
        change()
    }

    fun init () {
        for (style in _colorStyles.values) {
            style.addChangeListener { change() }
        }
        for (style in _boxStyles.values) {
            style.addChangeListener { change() }
        }
        for (style in _textStyles.values) {
            style.addChangeListener { change() }
        }
    }

}