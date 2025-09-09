package com.itsaky.androidide.fragments.sidebar.datatree

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.itsaky.androidide.tasks.executeAsync
import com.unnamed.b.atv.view.AndroidTreeView

internal class FileTreeViewModel : ViewModel() {
    private val _treeState = MutableLiveData<String?>()
    val treeState: MutableLiveData<String?>
        get() = _treeState

    val savedState: String
        get() = _treeState.value ?: ""

    fun saveState(treeView: AndroidTreeView?) {
        treeView?.let { tree ->
            // Use the two-lambda version of executeAsync:
            // executeAsync({ background task }, { ui callback })
            executeAsync(
                { tree.saveState }, // Background task
                { result -> _treeState.value = result } // UI thread callback
            )
        }
    }
}