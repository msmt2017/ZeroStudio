package de.lulebe.designer.propertyEditing

import android.view.LayoutInflater
import android.view.ViewGroup
import de.lulebe.designer.BoardActivity
import de.lulebe.designer.R
import de.lulebe.designer.data.BoardState
import de.lulebe.designer.data.objects.*


class PropertyPanelManager(val mActivity: BoardActivity, val mLayout: ViewGroup, val mBoardObject: BoardObject, val mBoardState: BoardState) {

    init {
        mBoardState.addListener(object : BoardState.BoardStateListener() {
            override fun onSelectChange(objs: List<BaseObject>) {
                updateUI()
            }
        })
        updateUI()
    }


    private fun updateUI () {
        mLayout.removeAllViews()
        val layoutInflater = LayoutInflater.from(mLayout.context)
        if (mBoardState.selected.size == 0)
            layoutInflater.inflate(R.layout.properties_null, mLayout)
        else {
            val selected = mBoardState.selected[0]
            when (selected) {
                is CopyObject -> {
                    layoutInflater.inflate(R.layout.properties_copy, mLayout)
                    PropertiesEditorCopy(selected, mLayout, mBoardObject, mBoardState)
                }
                is SourceObject -> {
                    layoutInflater.inflate(R.layout.properties_object, mLayout)
                    PropertiesEditorObject(selected, mLayout, mBoardObject, mBoardState)
                    when (selected) {
                        is RectObject -> {
                            layoutInflater.inflate(R.layout.properties_object_rect, mLayout)
                            PropertiesEditorRect(selected, mLayout, mBoardObject)
                        }
                        is TextObject -> {
                            layoutInflater.inflate(R.layout.properties_object_text, mLayout)
                            PropertiesEditorText(selected, mLayout, mBoardObject, mActivity)
                        }
                        is ImageObject -> {
                            layoutInflater.inflate(R.layout.properties_object_image, mLayout)
                            PropertiesEditorImage(selected, mLayout, mBoardObject, mActivity)
                        }
                        is BoardObject -> {
                            layoutInflater.inflate(R.layout.properties_object_board, mLayout)
                            PropertiesEditorBoard(selected, mLayout, mActivity)
                        }
                    }
                }
            }
        }
    }

}