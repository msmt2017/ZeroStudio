package de.lulebe.designer

import android.support.v4.content.res.ResourcesCompat
import android.support.v7.app.AlertDialog
import android.support.v7.widget.GridLayoutManager
import android.support.v7.widget.LinearLayoutManager
import android.support.v7.widget.RecyclerView
import android.view.View
import android.widget.ImageView
import com.woxthebox.draglistview.DragListView
import de.lulebe.designer.adapters.BoardImportAdapter
import de.lulebe.designer.adapters.BoardObjectsAdapter
import de.lulebe.designer.data.BoardState
import de.lulebe.designer.data.Grouper
import de.lulebe.designer.data.objects.*
import org.jetbrains.anko.doAsync


class LeftPanelManager(val mPanel: Pane, val mBoardState: BoardState, val mBoardObject: BoardObject, val mActivity: BoardActivity) : View.OnClickListener {


    private val mBtnAddRect: View
    private val mBtnAddText: View
    private val mBtnAddImage: View
    private val mBtnAddGroup: View

    private val mBtnImport: View
    private val mBtnJoin: View

    private val mBtnToggleEditpan: ImageView
    private val mBtnToggleGrid: ImageView
    private val mBtnRemoveUnusedFiles: View

    private val mObjectslistView: DragListView


    init {
        mBtnAddRect = mPanel.findViewById(R.id.btn_add_rect)
        mBtnAddText = mPanel.findViewById(R.id.btn_add_text)
        mBtnAddImage = mPanel.findViewById(R.id.btn_add_image)
        mBtnAddGroup = mPanel.findViewById(R.id.btn_add_group)

        mBtnImport = mPanel.findViewById(R.id.btn_import)
        mBtnJoin = mPanel.findViewById(R.id.btn_join)

        mBtnToggleEditpan = mPanel.findViewById<ImageView>(R.id.btn_toggle_editpan)
        mBtnToggleGrid = mPanel.findViewById<ImageView>(R.id.btn_toggle_grid)
        mBtnRemoveUnusedFiles = mPanel.findViewById(R.id.btn_remove_unused)


        mObjectslistView = mPanel.findViewById<DragListView>(R.id.list_objects)

        initCheatSheets()

        mBtnAddRect.setOnClickListener(this)
        mBtnAddText.setOnClickListener(this)
        mBtnAddImage.setOnClickListener(this)
        mBtnAddGroup.setOnClickListener(this)

        mBtnImport.setOnClickListener(this)
        mBtnJoin.setOnClickListener(this)

        mBtnToggleEditpan.setOnClickListener(this)
        mBtnToggleGrid.setOnClickListener(this)
        mBtnRemoveUnusedFiles.setOnClickListener(this)


        initObjectslist()


        mBoardState.addListener(object : BoardState.BoardStateListener() {
            override fun onShowGrid(shown: Boolean) {
                applyGridButtonState()
            }
            override fun onPanningActive (panningActive: Boolean) {
                applyEditpanButtonState()
            }
        })
        applyEditpanButtonState()
        applyGridButtonState()
    }



    override fun onClick(v: View?) {
        when (v) {
            mBtnAddRect -> {
                val obj = RectObject()
                createObj(obj)
            }
            mBtnAddText -> {
                val obj = TextObject()
                createObj(obj)
            }
            mBtnAddImage -> {
                val obj = ImageObject(mPanel.context)
                createObj(obj)
            }
            mBtnAddGroup -> {
                val obj = BoardObject()
                createObj(obj)
            }
            mBtnImport -> {
                importObjs()
            }
            mBtnJoin -> {
                mBoardState.selectedSet(Grouper.group(mPanel.context, mBoardObject, mBoardState.selected))
            }
            mBtnToggleGrid -> {
                mBoardState.showGrid = !mBoardState.showGrid
            }
            mBtnToggleEditpan -> {
                mBoardState.panningActive = !mBoardState.panningActive
            }
            mBtnRemoveUnusedFiles -> {
                doAsync {
                    mBoardObject.removeUnusedFiles()
                }
            }
        }
        mPanel.expand(false)
    }

    private fun createObj(obj: BaseObject) {
        obj.init(mPanel.context, mBoardObject)
        mBoardObject.addObject(obj)
        mBoardState.selectedSet(obj)
    }


    private fun importObjs () {
        val rv = RecyclerView(mPanel.context)
        rv.layoutManager = GridLayoutManager(mPanel.context, 2)
        val dialog = AlertDialog.Builder(mPanel.context)
                .setTitle("Import Objects")
                .setView(rv)
                .setNegativeButton(android.R.string.cancel) { dialogInterface, i ->
                    dialogInterface.cancel()
                }
                .show()
        val adapter = BoardImportAdapter(mPanel.context, mActivity.intent.getLongExtra("dbId", 0)) {
            dialog.dismiss()
            mActivity.importFromBoard(it)
        }
        rv.adapter = adapter
    }


    private fun applyGridButtonState () {
        if (mBoardState.showGrid)
            mBtnToggleGrid.setImageDrawable(ResourcesCompat.getDrawable(mBtnToggleGrid.resources, R.drawable.ic_grid_white_24dp, null))
        else
            mBtnToggleGrid.setImageDrawable(ResourcesCompat.getDrawable(mBtnToggleGrid.resources, R.drawable.ic_grid_off_white_24dp, null))
    }

    private fun applyEditpanButtonState () {
        if (mBoardState.panningActive)
            mBtnToggleEditpan.setImageDrawable(ResourcesCompat.getDrawable(mBtnToggleGrid.resources, R.drawable.ic_move_resize_variant_white_24dp, null))
        else
            mBtnToggleEditpan.setImageDrawable(ResourcesCompat.getDrawable(mBtnToggleGrid.resources, R.drawable.ic_cursor_move_white_24dp, null))
    }


    private fun initObjectslist () {
        mObjectslistView.setLayoutManager(LinearLayoutManager(mObjectslistView.context))
        val divider = ResourcesCompat.getDrawable(mObjectslistView.resources, R.drawable.listdivider, null)
        mObjectslistView.recyclerView.addItemDecoration(DividerItemDecoration(divider!!))
        val adapter = BoardObjectsAdapter(mBoardObject, mBoardState)
        adapter.setHasStableIds(true)
        mObjectslistView.setAdapter(adapter, true)
        mObjectslistView.setCanDragHorizontally(false)
        mObjectslistView.setDragListListener(object: DragListView.DragListListener {
            override fun onItemDragging(itemPosition: Int, x: Float, y: Float) {}
            override fun onItemDragStarted(position: Int) {}
            override fun onItemDragEnded(fromPosition: Int, toPosition: Int) {
                mBoardObject.reorderedObjects()
            }
        })
    }

    private fun initCheatSheets () {
        CheatSheet.setup(mBtnAddRect)
        CheatSheet.setup(mBtnAddText)
        CheatSheet.setup(mBtnAddImage)
        CheatSheet.setup(mBtnAddGroup)
        CheatSheet.setup(mBtnImport)
        CheatSheet.setup(mBtnJoin)
        CheatSheet.setup(mBtnToggleEditpan)
        CheatSheet.setup(mBtnToggleGrid)
        CheatSheet.setup(mBtnRemoveUnusedFiles)
    }
}