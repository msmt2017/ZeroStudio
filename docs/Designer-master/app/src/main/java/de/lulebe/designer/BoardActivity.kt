package de.lulebe.designer

import android.app.Activity
import android.content.Intent
import android.os.AsyncTask
import android.os.Bundle
import android.preference.PreferenceManager
import android.provider.OpenableColumns
import android.support.design.widget.CoordinatorLayout
import android.support.design.widget.Snackbar
import android.support.v4.app.ShareCompat
import android.support.v4.content.FileProvider
import android.support.v7.app.AlertDialog
import android.support.v7.app.AppCompatActivity
import android.support.v7.widget.Toolbar
import android.util.TypedValue
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import de.lulebe.designer.data.*
import de.lulebe.designer.data.objects.BaseObject
import de.lulebe.designer.data.objects.BoardObject
import de.lulebe.designer.propertyEditing.PropertyPanelManager
import de.lulebe.designer.styleEditing.StylePanelManager
import kotterknife.bindView
import org.jetbrains.anko.doAsync
import org.jetbrains.anko.uiThread
import java.io.File
import java.io.FileOutputStream
import java.util.*
import java.util.concurrent.Future

class BoardActivity : AppCompatActivity() {

    val REQUEST_CODE_IMAGE = 1
    val REQUEST_CODE_GROUP = 2
    val REQUEST_CODE_FONT = 3
    val REQUEST_CODE_IMPORT = 4

    private val mMainView: View by bindView(R.id.main)
    private val mToolbar: Toolbar by bindView(R.id.toolbar)
    private val mLeftpane: Pane by bindView(R.id.leftpane)
    private val mRightpane: Pane by bindView(R.id.rightpane)
    private val mBottompane: Pane by bindView(R.id.bottompane)
    private val mLayout: FrameLayout by bindView(R.id.layout)

    private var asyncABHider: Future<Unit>? = null

    private var mStorageManager: StorageManager? = null


    private var mPropertyPanelManager: PropertyPanelManager? = null
    private var mLeftPanelManager: LeftPanelManager? = null
    private var mStylePanelManager: StylePanelManager? = null

    private var mBoardObject: BoardObject? = null
    private var mBoardState: BoardState? = null
    private var mBoardKey = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        initUI()

        if (intent.getBooleanExtra("isRoot", true)) {
            mStorageManager = StorageManager(this, intent.getLongExtra("dbId", 0))
            LoadBoard(savedInstanceState).execute()
        } else {
            val key = intent.getIntExtra("boardKey", 0)
            if (key == 0)
                finish()
            else {
                mBoardObject = (application as Designer).boards[key]
                postLoadBoard(savedInstanceState)
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle?) {
        if (outState != null) {
            mBoardState?.saveInstanceState(outState)
        }
        super.onSaveInstanceState(outState)
    }

    override fun onStop () {
        if (intent.getBooleanExtra("isRoot", true)) {
            saveBoard()
        }
        super.onStop()
    }

    override fun onDestroy () {
        val key = intent.getIntExtra("boardKey", 0)
        if (key != 0)
            setResult(Activity.RESULT_OK, intent)
        if (intent.getBooleanExtra("isRoot", true)) {
            closeBoard()
        }
        super.onDestroy()
    }



    private fun initUI () {
        val dp64 = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 64F, resources.displayMetrics).toInt()
        setContentView(R.layout.activity_board)
        setSupportActionBar(mToolbar)
        supportActionBar!!.title = "Board"
        supportActionBar!!.setDisplayHomeAsUpEnabled(true)
        mLeftpane.addOpenListener { open ->
            mBoardState?.leftPanelExpanded = open
            if (open && !mLeftpane.isLocked() && !mRightpane.isLocked() && mRightpane.isExpanded())
                mRightpane.expand(false)
        }
        mLeftpane.addLockListener { locked ->
            mBoardState?.leftPanelLocked = locked
            mLeftpane.post {
                val lp = mLayout.layoutParams as CoordinatorLayout.LayoutParams
                if (locked)
                    lp.setMargins(mLeftpane.width, lp.topMargin, lp.rightMargin, lp.bottomMargin)
                else
                    lp.setMargins(dp64, lp.topMargin, lp.rightMargin, lp.bottomMargin)
                mLayout.layoutParams = lp
            }
        }
        mRightpane.addOpenListener { open ->
            mBoardState?.rightPanelExpanded = open
            if (open && !mRightpane.isLocked() && !mLeftpane.isLocked() && mLeftpane.isExpanded())
                mLeftpane.expand(false)
        }
        mRightpane.addLockListener { locked ->
            mBoardState?.rightPanelLocked = locked
            mRightpane.post {
                val lp = mLayout.layoutParams as CoordinatorLayout.LayoutParams
                if (locked)
                    lp.setMargins(lp.leftMargin, lp.topMargin, mRightpane.width, lp.bottomMargin)
                else
                    lp.setMargins(lp.leftMargin, lp.topMargin, dp64, lp.bottomMargin)
                mLayout.layoutParams = lp
            }
        }
        mBottompane.addOpenListener { open ->
            mBoardState?.bottomPanelExpanded = open
        }
        mBottompane.addLockListener { locked ->
            mBoardState?.bottomPanelLocked = locked
            mBottompane.post {
                val lpL = mLayout.layoutParams as CoordinatorLayout.LayoutParams
                if (locked)
                    lpL.setMargins(lpL.leftMargin, lpL.topMargin, lpL.rightMargin, mBottompane.height)
                else
                    lpL.setMargins(lpL.leftMargin, lpL.topMargin, lpL.rightMargin, dp64)
                mLayout.layoutParams = lpL
                val lpLp = mLeftpane.layoutParams as CoordinatorLayout.LayoutParams
                if (locked)
                    lpLp.setMargins(lpLp.leftMargin, lpLp.topMargin, lpLp.rightMargin, mBottompane.height)
                else
                    lpLp.setMargins(lpLp.leftMargin, lpLp.topMargin, lpLp.rightMargin, dp64)
                mLeftpane.layoutParams = lpLp
                val lpRp = mRightpane.layoutParams as CoordinatorLayout.LayoutParams
                if (locked)
                    lpRp.setMargins(lpRp.leftMargin, lpRp.topMargin, lpRp.rightMargin, mBottompane.height)
                else
                    lpRp.setMargins(lpRp.leftMargin, lpRp.topMargin, lpRp.rightMargin, dp64)
                mRightpane.layoutParams = lpRp
            }
        }
    }


    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.boardobject, menu)
        if (!intent.getBooleanExtra("isRoot", true)) {
            menu?.findItem(R.id.menu_share)?.isVisible = false
            menu?.findItem(R.id.menu_export)?.isVisible = false
        }
        return true
    }
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            android.R.id.home -> {
                if (mBoardState != null && mBoardState!!.importing) {
                    val data = Intent()
                    val objs = mBoardState!!.selected.map(BaseObject::uid).toLongArray()
                    data.putExtra("selectedObjects", objs)
                    data.putExtra("boardDbId", intent.getLongExtra("dbId", 0))
                    setResult(Activity.RESULT_OK, data)
                }
                finish()
                return true
            }
            R.id.menu_showui -> {
                if (mBoardState == null) return false
                item.isChecked = !item.isChecked
                mBoardState?.showUI = item.isChecked
                if (mBoardState!!.showUI)
                    mBoardState!!.boardScrollX += TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 64F, resources.displayMetrics)
                else
                    mBoardState!!.boardScrollX += -TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 64F, resources.displayMetrics)
                return true
            }
            R.id.menu_resize -> {
                resizeDialog()
                return true
            }
            R.id.menu_export -> {
                if (mBoardObject == null) return false
                doAsync {
                    val tmpFile = File.createTempFile("designer_img", ".png", cacheDir)
                    val os = FileOutputStream(tmpFile)
                    Renderer.renderPNG(mBoardObject!!, 2F, os)
                    os.close()
                    val intent = ShareCompat.IntentBuilder.from(this@BoardActivity)
                            .setType("image/png")
                            .setStream(FileProvider.getUriForFile(this@BoardActivity, "de.lulebe.designer", tmpFile))
                            .createChooserIntent()
                    uiThread { startActivity(intent) }
                }
                return true
            }
            R.id.menu_share -> {
                if (intent.getBooleanExtra("isRoot", true) && mStorageManager != null && mBoardObject != null) {
                    doAsync {
                        mStorageManager!!.save(mBoardObject!!)
                        val intent = mStorageManager!!.share(this@BoardActivity)
                        uiThread {
                            startActivity(intent)
                        }
                    }
                    return true
                }
                return false
            }
            else -> return false
        }
    }

    private fun resizeDialog () {
        val dialogView = layoutInflater.inflate(R.layout.dialog_board_size, null)
        val xField = dialogView.findViewById<EditText>(R.id.field_board_sizex)
        val yField = dialogView.findViewById<EditText>(R.id.field_board_sizey)
        val gridsizeField = dialogView.findViewById<EditText>(R.id.field_board_gridsize)
        val gridintervalField = dialogView.findViewById<EditText>(R.id.field_board_gridlargeinterval)
        xField.setText(mBoardObject?.width.toString())
        yField.setText(mBoardObject?.height.toString())
        gridsizeField.setText(mBoardObject?.gridSize.toString())
        gridintervalField.setText(mBoardObject?.gridInterval.toString())
        AlertDialog.Builder(this).setTitle(R.string.board_settings).setView(dialogView)
                .setPositiveButton(android.R.string.ok, { dialogInterface, someInt ->

                    mBoardObject?.width = xField.text.toString().toInt()
                    mBoardObject?.height = yField.text.toString().toInt()
                    mBoardObject?.gridSize = gridsizeField.text.toString().toInt()
                    mBoardObject?.gridInterval = gridintervalField.text.toString().toInt()
                    dialogInterface.dismiss()
                })
                .setNegativeButton(android.R.string.cancel, { dialogInterface, someInt ->
                    dialogInterface.cancel()
                })
                .create().show()
    }

    private var imageRequestCallback: ((uid: Long) -> Unit)? = null

    fun requestImage (cb: (uid: Long) -> Unit) : Boolean {
        if (imageRequestCallback != null) return false
        imageRequestCallback = cb
        val intent = Intent()
        intent.action = Intent.ACTION_GET_CONTENT
        intent.type = "image/*"
        startActivityForResult(intent, REQUEST_CODE_IMAGE)
        return true
    }

    private var fontRequestCallback: ((uid: Long) -> Unit)? = null

    fun requestFont (cb: (uid: Long) -> Unit) : Boolean {
        if (fontRequestCallback != null) return false
        fontRequestCallback = cb
        val intent = Intent()
        intent.action = Intent.ACTION_GET_CONTENT
        intent.type = "*/*"
        startActivityForResult(intent, REQUEST_CODE_FONT)
        return true
    }


    fun openGroup (group: BoardObject) {
        val key = Random().nextInt()
        (application as Designer).boards.put(key, group)
        val intent = Intent(this, BoardActivity::class.java)
        intent.putExtra("isRoot", false)
        intent.putExtra("boardKey", key)
        startActivityForResult(intent, REQUEST_CODE_GROUP)
    }

    fun importFromBoard (bm: BoardMeta) {
        val intent = Intent(this, BoardActivity::class.java)
        intent.putExtra("dbId", bm._id)
        intent.putExtra("import", true)
        startActivityForResult(intent, REQUEST_CODE_IMPORT)
    }


    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        when (requestCode) {
            REQUEST_CODE_GROUP -> {
                if (data == null || resultCode != Activity.RESULT_OK) return
                val key = data.getIntExtra("boardKey", 0)
                if (key != 0)
                    (application as Designer).boards.remove(key)
            }
            REQUEST_CODE_IMAGE -> {
                if (data == null || resultCode != Activity.RESULT_OK) return
                doAsync {
                    if (mBoardObject?.storageManager != null) {
                        val sm = mBoardObject!!.storageManager!!
                        val input = contentResolver.openInputStream(data.data)
                        val meta = contentResolver.query(data.data, null, null, null, null, null)
                        try {
                            if (meta != null && meta.moveToFirst()) {
                                val name = meta.getString(meta.getColumnIndex(OpenableColumns.DISPLAY_NAME))
                                val imageUID = sm.addImage(input, name)
                                uiThread {
                                    if (imageRequestCallback != null)
                                        imageRequestCallback!!(imageUID)
                                    imageRequestCallback = null
                                }
                            }
                        } finally {
                            meta.close()
                        }
                    }
                }
            }
            REQUEST_CODE_FONT -> {
                if (data == null || resultCode != Activity.RESULT_OK) return
                doAsync {
                    if (mBoardObject?.storageManager != null) {
                        val sm = mBoardObject!!.storageManager!!
                        val input = contentResolver.openInputStream(data.data)
                        val meta = contentResolver.query(data.data, null, null, null, null, null)
                        try {
                            if (meta != null && meta.moveToFirst()) {
                                val name = meta.getString(meta.getColumnIndex(OpenableColumns.DISPLAY_NAME))
                                val fontUID = sm.addFont(input, name)
                                uiThread {
                                    if (fontRequestCallback != null)
                                        fontRequestCallback!!(fontUID)
                                    fontRequestCallback = null
                                }
                            }
                        } finally {
                            meta.close()
                        }
                    }
                }
            }
            REQUEST_CODE_IMPORT -> {
                if (data != null && resultCode == Activity.RESULT_OK) {
                    doAsync({ it.printStackTrace() }) {
                        val toBoard = mBoardObject
                        if (toBoard != null) {
                            val sm = StorageManager(this@BoardActivity, data.getLongExtra("boardDbId", 0))
                            val fromBoard = sm.get(this@BoardActivity)
                            val ec = ExportContainer()
                            data.getLongArrayExtra("selectedObjects").forEach {
                                fromBoard.getObjectWithUID(it)?.export(ec, true)
                            }
                            ec.exportTo(this@BoardActivity, toBoard, fromBoard)
                            sm.close()
                        }
                    }
                }
            }
        }
    }

    private fun updateLastEditedTime () {
        if (intent.getBooleanExtra("isRoot", true)) {
            val dbh = DBHelper(this@BoardActivity)
            val id = intent.getLongExtra("dbId", 0)
            dbh.updateLastEditedTime(id)
            dbh.close()
        }
    }

    private inner class LoadBoard(val savedInstanceState: Bundle?) : AsyncTask<Void, Void, Boolean>() {
        override fun doInBackground(vararg params: Void?): Boolean {
            if (mStorageManager == null || !mStorageManager!!.exists()) return false
            mBoardObject = mStorageManager?.get(this@BoardActivity)
            mBoardKey = Random().nextInt()
            updateLastEditedTime()
            (application as Designer).boards.put(mBoardKey, mBoardObject!!)
            return true
        }
        override fun onPostExecute(boardFound: Boolean) {
            if (!boardFound) {
                finish()
            } else {
                postLoadBoard(savedInstanceState)
            }
        }
    }

    private fun postLoadBoard (savedInstanceState: Bundle?) {
        if (mBoardObject != null) {
            supportActionBar?.title = mBoardObject!!.name
            mBoardState = BoardState.fromInstanceState(savedInstanceState, mBoardObject!!)
            mBoardState!!.importing = intent.getBooleanExtra("import", false)
            val sp = PreferenceManager.getDefaultSharedPreferences(this)
            val boardView: View
            if (sp.getBoolean("surfaceview", true))
                boardView = BoardViewAsync(this, mBoardState!!, mBoardObject!!)
            else
                boardView = BoardView(this, mBoardState!!, mBoardObject!!)
            val lp = CoordinatorLayout.LayoutParams(CoordinatorLayout.LayoutParams.MATCH_PARENT, CoordinatorLayout.LayoutParams.MATCH_PARENT)
            boardView.layoutParams = lp
            mLayout.addView(boardView)
            val origLP = mLayout.layoutParams as CoordinatorLayout.LayoutParams
            val rightpane = mRightpane.findViewById<ViewGroup>(R.id.layout_properties)
            mPropertyPanelManager = PropertyPanelManager(this, rightpane, mBoardObject!!, mBoardState!!)
            mLeftPanelManager = LeftPanelManager(mLeftpane, mBoardState!!, mBoardObject!!, this)
            mStylePanelManager = StylePanelManager(mBottompane, mBoardObject!!, mBoardState!!, this)
            if (!mBoardState!!.importing) {
                mLeftpane.visibility = View.VISIBLE
                mRightpane.visibility = View.VISIBLE
                mBottompane.visibility = View.VISIBLE
            } else {
                val lp = mLayout.layoutParams as CoordinatorLayout.LayoutParams
                lp.setMargins(0, lp.topMargin, 0, 0)
                mLayout.layoutParams = lp
                Snackbar.make(mMainView, "Select one object (or multiple by long-clicking) and go back to the previous board to import them", Snackbar.LENGTH_LONG).show()
            }
            findViewById<View>(R.id.loading)?.visibility = View.GONE
            if (mBoardState!!.leftPanelLocked)
                mLeftpane.lock(true, false)
            else
                mLeftpane.expand(mBoardState!!.leftPanelExpanded, false)
            if (mBoardState!!.rightPanelLocked)
                mRightpane.lock(true, false)
            else
                mRightpane.expand(mBoardState!!.rightPanelExpanded, false)
            if (mBoardState!!.bottomPanelLocked)
                mBottompane.lock(true, false)
            else
                mBottompane.expand(mBoardState!!.bottomPanelExpanded, false)
            mBoardState?.addListener(object: BoardState.BoardStateListener() {
                override fun onShowUI(shown: Boolean) {
                    if (mBoardState!!.importing) return
                    if (shown) {
                        mLeftpane.visibility = View.VISIBLE
                        mRightpane.visibility = View.VISIBLE
                        mBottompane.visibility = View.VISIBLE
                        mLayout.layoutParams = origLP
                    } else {
                        mLeftpane.visibility = View.GONE
                        mRightpane.visibility = View.GONE
                        mBottompane.visibility = View.GONE
                        val lp = CoordinatorLayout.LayoutParams(CoordinatorLayout.LayoutParams.MATCH_PARENT, CoordinatorLayout.LayoutParams.MATCH_PARENT)
                        lp.topMargin = origLP.topMargin
                        mLayout.layoutParams = lp
                    }
                }
            })
        }
    }

    private fun saveBoard () {
        doAsync {
            if (mStorageManager != null && mBoardObject != null) {
                mStorageManager?.save(mBoardObject!!)
                updateLastEditedTime()
            }
        }
    }

    private fun closeBoard () {
        doAsync { mStorageManager?.close() }
    }
}
