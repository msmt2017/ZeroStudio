package android.zero.studio.uidesigner.adapters.models

import android.graphics.drawable.Drawable
import android.zero.studio.uidesigner.utils.FileUtil

data class DrawableFile(var versions: Int, var drawable: Drawable, var path: String) {
    var name: String = FileUtil.getLastSegmentFromPath(path)
}
