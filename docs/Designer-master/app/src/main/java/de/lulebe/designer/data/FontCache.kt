package de.lulebe.designer.data

import android.content.Context
import android.graphics.Typeface
import de.lulebe.designer.data.objects.BoardObject
import org.jetbrains.anko.doAsync
import org.jetbrains.anko.uiThread
import java.io.File
import java.lang.ref.WeakReference


object FontCache {
    val fonts: MutableMap<Long, WeakReference<Typeface>> = mutableMapOf()

    fun loadFont (uid: Long, board: BoardObject, ctx: Context, cb: (font: Typeface) -> Unit) {
        if (FontCache.fonts.containsKey(uid) && FontCache.fonts[uid]!!.get() != null) {
            cb(FontCache.fonts[uid]!!.get()!!)
        } else {
            doAsync {
                val typeFace: Typeface
                if (uid >= 200L) {
                    val path = File(board.getFontPath(uid))
                    if (path.exists() && path.canRead()) {
                        typeFace = Typeface.createFromFile(path)
                        fonts.put(uid, WeakReference(typeFace))
                    } else {
                        typeFace = Typeface.createFromAsset(ctx.assets, "fonts/roboto.ttf")
                    }
                } else {
                    typeFace = Typeface.createFromAsset(ctx.assets, "fonts/"+IncludedFiles.fonts[uid])
                    fonts.put(uid, WeakReference(typeFace))
                }
                uiThread {
                    cb(typeFace)
                }
            }
        }
    }

}