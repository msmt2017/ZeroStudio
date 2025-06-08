package de.lulebe.designer.propertyEditing

import android.view.View
import android.view.ViewGroup
import de.lulebe.designer.BoardActivity
import de.lulebe.designer.R
import de.lulebe.designer.data.objects.BoardObject


class PropertiesEditorBoard(val mObject: BoardObject, val mView: ViewGroup, val mActivity: BoardActivity) : View.OnClickListener {

    private val mOpenView: View

    init {
        mOpenView = mView.findViewById(R.id.btn_object_openboard)

        mOpenView.setOnClickListener(this)
    }


    override fun onClick(v: View?) {
        when (v) {
            mOpenView -> {
                mActivity.openGroup(mObject)
            }
        }
    }
}