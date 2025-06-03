package de.lulebe.designer

import android.app.Activity
import android.content.ContentValues
import android.content.DialogInterface
import android.content.Intent
import android.net.Uri
import android.os.AsyncTask
import android.os.Bundle
import android.preference.PreferenceManager
import android.support.design.widget.FloatingActionButton
import android.support.v4.view.MenuItemCompat
import android.support.v7.app.AlertDialog
import android.support.v7.app.AppCompatActivity
import android.support.v7.widget.GridLayoutManager
import android.support.v7.widget.RecyclerView
import android.support.v7.widget.Toolbar
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.widget.EditText
import de.lulebe.designer.adapters.BoardsAdapter
import de.lulebe.designer.data.BoardMeta
import de.lulebe.designer.data.DBHelper
import de.lulebe.designer.data.IncludedFiles
import de.lulebe.designer.data.StorageManager
import de.lulebe.designer.online.AccountManager
import java.io.File
import java.util.*

class SelectBoardActivity : AppCompatActivity() {

    private val REQUEST_CODE_IMPORT = 1

    private val mAccountManager = AccountManager(this)

    private val mAdapter = BoardsAdapter {board, longClicked ->
        if (longClicked) {
            openActionsDialog(board)
        } else {
            val intent = Intent(this, BoardActivity::class.java)
            intent.putExtra("dbId", board._id)
            intent.putExtra("path", filesDir.path + File.separator + board._id.toString())
            startActivity(intent)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        initUI()
        IncludedBoardsLoader().execute()
    }

    override fun onResume() {
        super.onResume()
        BoardsLoader().execute()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (data == null || resultCode != Activity.RESULT_OK || requestCode != REQUEST_CODE_IMPORT) return
        BoardImporter(data.data).execute()
    }


    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu_select_board, menu)
        if (mAccountManager.isSignedIn)
            menu?.getItem(R.id.menu_signin)?.isVisible = false
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem?): Boolean {
        when (item?.itemId) {
            R.id.menu_settings -> {
                startActivity(Intent(this, SettingsActivity::class.java))
                return true
            }
            else -> return super.onOptionsItemSelected(item)
        }
    }


    private fun initUI () {
        setContentView(R.layout.activity_select_board)
        val toolbar = findViewById<Toolbar?>(R.id.toolbar)
        setSupportActionBar(toolbar)
        val fabAdd = findViewById<FloatingActionButton>(R.id.fab_add)
        fabAdd.setOnClickListener {
            val dialogView = layoutInflater.inflate(R.layout.dialog_create_board, null)
            AlertDialog.Builder(this).setTitle(R.string.create_new_board).setView(dialogView)
                    .setPositiveButton(R.string.create, { di: DialogInterface, i: Int ->
                        val name = dialogView.findViewById<EditText>(R.id.field_board_name).text.toString()
                        if (name.length > 0)
                            BoardCreator(name).execute()
                        di.dismiss()
                    })
                    .setNegativeButton(android.R.string.cancel, { di: DialogInterface, i: Int ->
                        di.cancel()
                    })
                    .create().show()
        }
        val fabImport = findViewById<FloatingActionButton>(R.id.fab_import)
        fabImport.setOnClickListener {
            val intent = Intent()
            intent.action = Intent.ACTION_GET_CONTENT
            intent.type = "application/zip"
            startActivityForResult(intent, REQUEST_CODE_IMPORT)
        }
        val boardsList = findViewById<RecyclerView>(R.id.boardslist)
        boardsList.layoutManager = GridLayoutManager(this, resources.getInteger(R.integer.mainpage_grid_rows))
        boardsList.adapter = mAdapter
    }

    private fun openActionsDialog (boardMeta: BoardMeta) {
        AlertDialog.Builder(this)
                .setTitle(R.string.edit_board)
                .setMessage(R.string.choose_option)
                .setNegativeButton(android.R.string.cancel, DialogInterface.OnClickListener { dialogInterface, i ->
                    dialogInterface.cancel()
                })
                .setNeutralButton(R.string.delete, DialogInterface.OnClickListener { dialogInterface, i ->
                    dialogInterface.dismiss()
                    BoardDeleter(boardMeta).execute()
                })
                .setPositiveButton(R.string.duplicate, DialogInterface.OnClickListener { dialogInterface, i ->
                    dialogInterface.dismiss()
                    duplicateBoard(boardMeta)
                })
                .show()
    }

    private fun duplicateBoard (boardMeta: BoardMeta) {
        val v = LayoutInflater.from(this).inflate(R.layout.dialog_namechooser, null)
        AlertDialog.Builder(this)
            .setTitle(R.string.duplicate_board)
            .setView(v)
            .setNegativeButton(android.R.string.cancel) { dialogInterface, i ->
                dialogInterface.cancel()
            }
            .setPositiveButton(android.R.string.ok) { dialogInterface, i ->
                val name = v.findViewById<EditText>(R.id.field_name).text.toString()
                dialogInterface.dismiss()
                BoardDuplicator(boardMeta, name).execute()
            }
            .show()
    }

    private inner class IncludedBoardsLoader() : AsyncTask<Void, Void, Void>() {
        private var dialog: AlertDialog? = null
        private val sp = PreferenceManager.getDefaultSharedPreferences(this@SelectBoardActivity)
        private var cancelled = false
        override fun onPreExecute() {
            if (sp.getInt("included-boards-version", 0) >= IncludedFiles.includedBoardsVersion)
                cancelled = true
            else {
                dialog = AlertDialog.Builder(this@SelectBoardActivity)
                        .setTitle("Setting up Boards...")
                        .setMessage("please wait...")
                        .show()
            }
        }
        override fun doInBackground(vararg p0: Void?): Void? {
            if (cancelled) return null
            IncludedFiles.setupBoards(this@SelectBoardActivity)
            return null
        }

        override fun onPostExecute(result: Void?) {
            if (cancelled) return
            val editor = sp.edit()
            editor.putInt("included-boards-version", IncludedFiles.includedBoardsVersion)
            editor.apply()
            dialog?.dismiss()
        }
    }

    private inner class BoardCreator(val name: String) : AsyncTask<Void, Void, Long>() {
        override fun doInBackground(vararg params: Void?): Long? {
            try {
                val dbh = DBHelper(this@SelectBoardActivity)
                val db = dbh.writableDatabase
                val cv = ContentValues()
                cv.put("name", name)
                cv.put("lastOpened", Date().time)
                val _id = db.insert("boards", null, cv)
                val sm = StorageManager.createWithNameInternal(name, filesDir.path + File.separator + _id.toString())
                db.close()
                dbh.close()
                return _id
            } catch (e: IllegalArgumentException) {
                return null
            }
        }
        override fun onPostExecute (id: Long?) {
            val intent = Intent(this@SelectBoardActivity, BoardActivity::class.java)
            intent.putExtra("dbId", id)
            intent.putExtra("path", filesDir.path + File.separator + id.toString())
            startActivity(intent)
        }
    }

    private inner class BoardDuplicator(val boardMeta: BoardMeta, val newName: String) : AsyncTask<Void, Void, Void>() {
        val loadDialog: AlertDialog = AlertDialog.Builder(this@SelectBoardActivity)
                .setTitle(R.string.duplicating)
                .setView(R.layout.dialog_loadingtext)
                .setCancelable(false)
                .show()
        override fun doInBackground(vararg p0: Void?): Void? {
            val dbh = DBHelper(this@SelectBoardActivity)
            val db = dbh.writableDatabase
            val cv = ContentValues()
            cv.put("name", newName)
            cv.put("lastOpened", Date().time)
            val newId = db.insert("boards", null, cv)
            val origSM = StorageManager(filesDir.path + File.separator + boardMeta._id.toString())
            origSM.duplicate(filesDir.path + File.separator + newId.toString())
            val newSM = StorageManager(filesDir.path + File.separator + newId.toString())
            val board = newSM.get(this@SelectBoardActivity)
            board.name = newName
            newSM.save(board)
            return null
        }

        override fun onPostExecute(result: Void?) {
            loadDialog.dismiss()
            BoardsLoader().execute()
        }
    }

    private inner class BoardImporter (val uri: Uri) : AsyncTask<Void, Void, Long>() {
        override fun doInBackground(vararg p0: Void?): Long? {
            val dbh = DBHelper(this@SelectBoardActivity)
            val db = dbh.writableDatabase
            val c = db.rawQuery("SELECT _id FROM boards ORDER BY _id DESC LIMIT 1", null)
            val _id: Long
            if (c.count > 0) {
                c.moveToFirst()
                _id = c.getLong(c.getColumnIndex("_id")) + 1
            } else
                _id = 1L
            c.close()
            val input = contentResolver.openInputStream(uri)
            val sm = StorageManager.createFromZipInput(this@SelectBoardActivity, input, _id.toString())
            val cv = ContentValues()
            cv.put("_id", _id)
            cv.put("name", sm.get(this@SelectBoardActivity).name)
            cv.put("lastOpened", Date().time)
            db.insert("boards", null, cv)
            db.close()
            dbh.close()
            return _id
        }

        override fun onPostExecute(id: Long?) {
            val intent = Intent(this@SelectBoardActivity, BoardActivity::class.java)
            intent.putExtra("dbId", id)
            intent.putExtra("path", filesDir.path + File.separator + id.toString())
            startActivity(intent)
        }
    }


    private inner class BoardDeleter(val board: BoardMeta) : AsyncTask<Void, Void, Void>() {
        override fun doInBackground(vararg params: Void?): Void? {
            val dbh = DBHelper(this@SelectBoardActivity)
            val db = dbh.writableDatabase
            db.delete("boards", "_id=?", Array(1, {board._id.toString()}))
            StorageManager(filesDir.path + File.separator + board._id).delete()
            db.close()
            dbh.close()
            return null
        }
        override fun onPostExecute(result: Void?) {
            BoardsLoader().execute()
        }
    }



    private inner class BoardsLoader : AsyncTask<Void, Void, MutableList<BoardMeta>>() {
        override fun doInBackground(vararg params: Void?): MutableList<BoardMeta> {
            val items = mutableListOf<BoardMeta>()
            val dbh = DBHelper(this@SelectBoardActivity)
            val db = dbh.readableDatabase
            val c = db.rawQuery("SELECT * FROM boards ORDER BY lastOpened DESC", null)
            while(c.moveToNext()) {
                val item = BoardMeta()
                item._id = c.getLong(c.getColumnIndex("_id"))
                item.name = c.getString(c.getColumnIndex("name"))
                item.lastOpened = c.getLong(c.getColumnIndex("lastOpened"))
                items.add(item)
            }
            c.close()
            db.close()
            dbh.close()
            return items
        }
        override fun onPostExecute (list: MutableList<BoardMeta>) {
            mAdapter.setItems(list)
        }
    }
}
