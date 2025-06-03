package de.lulebe.designer.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import java.util.*


class DBHelper(val context: Context) : SQLiteOpenHelper(context, DBHelper.DB_NAME, null, DBHelper.DB_VERSION) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE boards (_id INTEGER PRIMARY KEY AUTOINCREMENT, name VARCHAR(100), lastOpened INT)")
        indexIncludedImages(db)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        indexIncludedImages(db)
    }

    fun indexIncludedImages (db: SQLiteDatabase) {
        db.execSQL("DROP TABLE IF EXISTS included_images")
        db.execSQL("CREATE TABLE included_images (_id INTEGER PRIMARY KEY AUTOINCREMENT, source VARCHAR(200), dir VARCHAR(200), file VARCHAR(200))")
        indexLibrary(db, "GOOGLE")
        indexLibrary(db, "IOS")
    }

    fun indexLibrary (db: SQLiteDatabase, path: String) {
        var dirline = true
        var d = ""
        var f = ""
        val input = context.assets.open(path + "/included_images.txt")
        val scan = Scanner(input)
        db.beginTransaction()
        try {
            while (scan.hasNext()) {
                if (dirline) {
                    d = scan.next()
                } else {
                    f = scan.next()
                    db.execSQL("INSERT INTO included_images (source, dir, file) VALUES ('" + path + "', '" + d + "', '" + f + "')")
                }
                dirline = !dirline
            }
            db.setTransactionSuccessful()
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            db.endTransaction()
            scan.close()
            input.close()
        }
    }

    fun updateLastEditedTime (id: Long) {
        val db = writableDatabase
        db.execSQL("UPDATE boards SET lastOpened=" + Date().time.toString() + " WHERE _id=" + id.toString())
        db.close()
    }

    companion object {
        private val DB_NAME = "Designer.db"
        private val DB_VERSION = 3
    }
}
