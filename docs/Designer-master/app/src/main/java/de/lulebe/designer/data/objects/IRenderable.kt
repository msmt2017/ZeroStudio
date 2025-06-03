package de.lulebe.designer.data.objects

import android.graphics.RectF
import de.lulebe.designer.data.Deserializer


interface IRenderable {
    fun getRenderables(d: Deserializer, forceReload: Boolean = false) : Array<Renderable>
    fun getBoundRect(deserializer: Deserializer, xOffset: Float = 0F, yOffset: Float = 0F) : RectF
    fun getHandleRenderables(d: Deserializer, xOffset: Float = 0F, yOffset: Float = 0F, showHandles: Boolean = true) : Array<Renderable>
}