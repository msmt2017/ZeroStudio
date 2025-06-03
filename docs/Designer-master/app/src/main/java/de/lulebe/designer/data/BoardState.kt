package de.lulebe.designer.data

import android.os.Bundle
import de.lulebe.designer.data.objects.BaseObject
import de.lulebe.designer.data.objects.BoardObject


class BoardState {

    open class BoardStateListener () {
        open fun onSelectChange (objs: List<BaseObject>) {}
        open fun onImporting (importing: Boolean) {}
        open fun onPanningActive (active: Boolean) {}
        open fun onShowGrid (shown: Boolean) {}
        open fun onShowUI (shown: Boolean) {}
        open fun onBoardScrollX (scrollX: Float) {}
        open fun onBoardScrollY (scrollY: Float) {}
        open fun onExpandLeftPanel (expanded: Boolean) {}
        open fun onLockLeftPanel (locked: Boolean) {}
        open fun onExpandRightPanel (expanded: Boolean) {}
        open fun onLockRightPanel (locked: Boolean) {}
        open fun onExpandBottomPanel (expanded: Boolean) {}
        open fun onLockBottomPanel (locked: Boolean) {}
    }

    //listeners
    @Transient
    private var mListeners = mutableListOf<BoardStateListener>()
    fun addListener (l: BoardStateListener) {
        mListeners.add(l)
    }
    fun removeListener (l: BoardStateListener) {
        mListeners.remove(l)
    }
    fun removeAllListeners () {
        mListeners.clear()
    }
    


    private val _selected: MutableList<BaseObject> = mutableListOf()
    val selected: List<BaseObject>
        get() = _selected
    fun selectedClear () {
        _selected.clear()
        for (l in mListeners)
            l.onSelectChange(selected)
    }
    fun selectedSet (obj: BaseObject?) {
        _selected.clear()
        if (obj != null)
            _selected.add(obj)
        for (l in mListeners)
            l.onSelectChange(selected)
    }
    fun selectedAdd (obj: BaseObject?) {
        if (obj == null || _selected.contains(obj))
            return
        _selected.add(obj)
        for (l in mListeners)
            l.onSelectChange(selected)
    }
    fun selectedRemove (obj: BaseObject) {
        _selected.remove(obj)
        for (l in mListeners)
            l.onSelectChange(selected)
    }


    private var _importing: Boolean = false
    var importing: Boolean
        get() = _importing
        set(value) {
            _importing = value
            for (l in mListeners)
                l.onImporting(value)
        }


    private var _panningActive: Boolean = true
    var panningActive: Boolean
        get() = _panningActive
        set(value) {
            if (!value && _importing) return
            _panningActive = value
            for (l in mListeners)
                l.onPanningActive(value)
        }


    private var _showGrid: Boolean = true
    var showGrid: Boolean
        get() = _showGrid
        set(value) {
            _showGrid = value
            for (l in mListeners)
                l.onShowGrid(value)
        }


    private var _showUI: Boolean = true
    var showUI: Boolean
        get() = _showUI
        set(value) {
            _showUI = value
            for (l in mListeners)
                l.onShowUI(value)
        }

    private var _boardScrollX: Float = 0F
    var boardScrollX: Float
        get() = _boardScrollX
        set(value) {
            _boardScrollX = value
            for (l in mListeners)
                l.onBoardScrollX(value)
        }

    private var _boardScrollY: Float = 0F
    var boardScrollY: Float
        get() = _boardScrollY
        set(value) {
            _boardScrollY = value
            for (l in mListeners)
                l.onBoardScrollY(value)
        }

    private var _leftPanelExpanded: Boolean = false
    var leftPanelExpanded: Boolean
        get() = _leftPanelExpanded
        set(value) {
            _leftPanelExpanded = value
            for (l in mListeners)
                l.onExpandLeftPanel(value)
        }

    private var _leftPanelLocked: Boolean = false
    var leftPanelLocked: Boolean
        get() = _leftPanelLocked
        set(value) {
            _leftPanelLocked = value
            for (l in mListeners)
                l.onLockLeftPanel(value)
        }

    private var _rightPanelExpanded: Boolean = false
    var rightPanelExpanded: Boolean
        get() = _rightPanelExpanded
        set(value) {
            _rightPanelExpanded = value
            for (l in mListeners)
                l.onExpandRightPanel(value)
        }

    private var _rightPanelLocked: Boolean = false
    var rightPanelLocked: Boolean
        get() = _rightPanelLocked
        set(value) {
            _rightPanelLocked = value
            for (l in mListeners)
                l.onLockRightPanel(value)
        }

    private var _bottomPanelExpanded: Boolean = false
    var bottomPanelExpanded: Boolean
        get() = _bottomPanelExpanded
        set(value) {
            _bottomPanelExpanded = value
            for (l in mListeners)
                l.onExpandBottomPanel(value)
        }

    private var _bottomPanelLocked: Boolean = false
    var bottomPanelLocked: Boolean
        get() = _bottomPanelLocked
        set(value) {
            _bottomPanelLocked = value
            for (l in mListeners)
                l.onLockBottomPanel(value)
        }


    fun saveInstanceState (instanceState: Bundle) {
        val b = Bundle()
        b.putBoolean("importing", importing)
        b.putBoolean("panningActive", panningActive)
        b.putBoolean("showGrid", showGrid)
        b.putBoolean("showUI", showUI)
        b.putFloat("boardScrollX", boardScrollX)
        b.putFloat("boardScrollY", boardScrollY)
        b.putBoolean("leftPanelExpanded", leftPanelExpanded)
        b.putBoolean("leftPanelLocked", leftPanelLocked)
        b.putBoolean("rightPanelExpanded", rightPanelExpanded)
        b.putBoolean("rightPanelLocked", rightPanelLocked)
        b.putBoolean("bottomPanelExpanded", bottomPanelExpanded)
        b.putBoolean("bottomPanelLocked", bottomPanelLocked)
        b.putLongArray("selectedUIDs", selected.map { it.uid }.toLongArray())
        instanceState.putBundle("BoardState", b)
    }


    companion object {
        fun fromInstanceState (sis: Bundle?, boardObject: BoardObject) : BoardState {
            if (sis == null || !sis.containsKey("BoardState"))
                return BoardState()
            val bs = BoardState()
            val b = sis.getBundle("BoardState")
            bs.importing = b.getBoolean("importing")
            bs.panningActive = b.getBoolean("panningActive")
            bs.showGrid = b.getBoolean("showGrid")
            bs.showUI = b.getBoolean("showUI")
            bs.boardScrollX = b.getFloat("boardScrollX")
            bs.boardScrollY = b.getFloat("boardScrollY")
            bs.leftPanelExpanded = b.getBoolean("leftPanelExpanded")
            bs.leftPanelLocked = b.getBoolean("leftPanelLocked")
            bs.rightPanelExpanded = b.getBoolean("rightPanelExpanded")
            bs.rightPanelLocked = b.getBoolean("rightPanelLocked")
            bs.bottomPanelExpanded = b.getBoolean("bottomPanelExpanded")
            bs.bottomPanelLocked = b.getBoolean("bottomPanelLocked")
            val s = b.getLongArray("selectedUIDs")
            s.map { boardObject.getObjectWithUID(it) }.forEach {
                if (it != null)
                    bs.selectedAdd(it)
            }
            return bs
        }
    }
}