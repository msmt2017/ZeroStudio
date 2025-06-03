package de.lulebe.designer.data

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.support.v4.app.ShareCompat
import android.support.v4.content.FileProvider
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import de.lulebe.designer.Renderer
import de.lulebe.designer.data.objects.*
import de.lulebe.designer.external.RuntimeTypeAdapterFactory
import net.lingala.zip4j.core.ZipFile
import net.lingala.zip4j.model.ZipParameters
import net.lingala.zip4j.util.Zip4jConstants
import org.apache.commons.io.FileUtils
import java.io.*
import java.util.*


class StorageManager {

    private val mDir: File
    private val mFile: File



    private var mBoardObject: BoardObject? = null

    private val mGson: Gson

    constructor (path: String) {
        mDir = File(path)
        mFile = File(path + File.separator + "board.ldes")
        val boFactory: RuntimeTypeAdapterFactory<BaseObject> = RuntimeTypeAdapterFactory.of(BaseObject::class.java, "ObjectType")
        boFactory.registerSubtype(SourceObject::class.java)
        boFactory.registerSubtype(CopyObject::class.java)
        boFactory.registerSubtype(BoardObject::class.java)
        boFactory.registerSubtype(RectObject::class.java)
        boFactory.registerSubtype(TextObject::class.java)
        boFactory.registerSubtype(ImageObject::class.java)
        mGson = GsonBuilder().registerTypeAdapterFactory(boFactory).create()
    }

    constructor(ctx: Context, id: Long) : this(ctx.filesDir.path + File.separator + id.toString())

    fun exists () = mFile.exists()

    fun get (ctx: Context) : BoardObject {
        if (mBoardObject != null) return mBoardObject!!
        val ins = FileInputStream(mFile)
        val inr = InputStreamReader(ins)
        mBoardObject = mGson.fromJson(inr, BoardObject::class.java)
        inr.close()
        ins.close()
        mBoardObject!!.storageManager = this
        mBoardObject!!.init(ctx, null)
        return mBoardObject!!
    }

    fun save (boardObject: BoardObject) {
        mBoardObject = boardObject
        if (!mFile.exists()) {
            if (!mDir.exists())
                mDir.mkdirs()
            mFile.createNewFile()
        }
        val outs = FileOutputStream(mFile)
        val outw = OutputStreamWriter(outs)
        mGson.toJson(boardObject, BoardObject::class.java, outw)
        outw.close()
        outs.close()
        savePreview()
        BoardSaves.boardSaved(mDir.name)
    }

    fun savePreview () {
        val board = mBoardObject ?: return
        if (!mDir.exists()) return
        val f = File(mDir, "preview.png")
        val os = FileOutputStream(f)
        val ratio = 2F
        Renderer.renderPNG(board, ratio, os)
        os.close()
    }

    fun addImage (inp: InputStream, filename: String, uid: Long = UIDGenerator.generateUID()) : Long {
        if (mBoardObject == null) return 0L
        mBoardObject!!.images.put(uid, filename)
        val to = File(mDir.path + File.separator + uid + "." + File(filename).extension)
        if (!to.exists())
        FileUtils.copyToFile(inp, to)
        return uid;
    }

    fun removeImage (key: Long) {
        if (mBoardObject == null) return
        val filename = mBoardObject!!.images.remove(key)
        val f = File(mDir.path + File.separator + key + "." + File(filename).extension)
        if (f.exists())
            f.delete()
    }

    fun addFont (inp: InputStream, filename: String, uid: Long = UIDGenerator.generateUID()) : Long {
        if (mBoardObject == null) return 0L
        mBoardObject!!.fonts.put(uid, filename)
        val to = File(mDir.path + File.separator + uid + "." + File(filename).extension)
        if (!to.exists())
            FileUtils.copyToFile(inp, to)
        return uid
    }

    fun removeFont (key: Long) {
        if (mBoardObject == null) return
        val filename = mBoardObject!!.fonts.remove(key)
        val f = File(mDir.path + File.separator + key + "." + File(filename).extension)
        if (f.exists())
            f.delete()
    }

    fun close () {

    }

    fun delete () = mDir.deleteRecursively()

    fun duplicate (newDirPath: String) {
        FileUtils.copyDirectory(mDir, File(newDirPath))
    }

    fun share (act: Activity) : Intent {
        val zipPath = act.cacheDir.path + File.separator + mDir.name + ".zip"
        if (File(zipPath).exists())
            File(zipPath).delete()
        val zipFile = ZipFile(zipPath)
        val zipParams = ZipParameters()
        zipParams.compressionMethod = Zip4jConstants.COMP_DEFLATE
        zipParams.compressionLevel = Zip4jConstants.DEFLATE_LEVEL_NORMAL
        val l = ArrayList<Any>()
        l.addAll(mDir.listFiles())
        zipFile.createZipFile(l, zipParams)
        val uri = FileProvider.getUriForFile(act, "de.lulebe.designer", File(zipPath))
        return ShareCompat.IntentBuilder.from(act)
                .setType("application/zip")
                .setSubject(mBoardObject?.name ?: "Designer board file")
                .setStream(uri)
                .createChooserIntent()
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }

    fun getPath () = mDir.path

    companion object {
        @Throws(IllegalArgumentException::class)
        fun createWithNameInternal(name: String, path: String) : StorageManager {
            val sm = StorageManager(path)
            val board = BoardObject()
            board.name = name
            board.width = 360
            board.height = 360
            sm.save(board)
            return sm
        }

        fun createFromZipInput (ctx: Context, inp: InputStream, filename: String) : StorageManager {
            val zip = File(ctx.cacheDir.path + File.separator + Random().nextLong().toString() + ".zip")
            val tmpPath = ctx.cacheDir.path + File.separator + zip.nameWithoutExtension
            FileUtils.copyToFile(inp, zip)
            val zipFile = ZipFile(zip)
            zipFile.extractAll(tmpPath)
            zip.delete()
            val sm = StorageManager(tmpPath)
            val name = sm.get(ctx).name
            sm.close()
            val path = ctx.filesDir.path + File.separator + filename
            FileUtils.moveDirectory(File(tmpPath), File(path))
            return StorageManager(path)
        }
    }
}