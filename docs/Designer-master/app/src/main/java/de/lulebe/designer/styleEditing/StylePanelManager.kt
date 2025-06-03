package de.lulebe.designer.styleEditing

import android.view.ViewGroup
import de.lulebe.designer.BoardActivity
import de.lulebe.designer.data.BoardState
import de.lulebe.designer.data.objects.BoardObject


class StylePanelManager(val mView: ViewGroup, val mBoardObject: BoardObject, val mBoardState: BoardState, val mActivity: BoardActivity) {
    init {
        ColorStyleManager(mView, mBoardObject, mBoardState)
        BoxStyleManager(mView, mBoardObject, mBoardState)
        TextStyleManager(mView, mBoardObject, mBoardState, mActivity)
    }
}