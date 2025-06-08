package de.lulebe.designer

import android.graphics.*
import android.text.Layout
import de.lulebe.designer.data.Deserializer
import de.lulebe.designer.data.objects.BoardObject
import de.lulebe.designer.data.objects.RectObject
import de.lulebe.designer.data.objects.Renderable
import java.io.OutputStream


class Renderer {
    companion object {



        fun drawRenderables (renderables: Array<Renderable>, canvas: Canvas?, clearWhite: Boolean = true, translate: Boolean= true) {
            if (canvas != null) {
                if (clearWhite)
                    canvas.drawColor(Color.WHITE)
                else
                    canvas.drawColor(0, PorterDuff.Mode.CLEAR)
                for (rend in renderables) {
                    drawRenderable(rend, canvas, translate)
                }
            }
        }



        fun drawRenderable(rend: Renderable, canvas: Canvas, translate: Boolean = true) {
            canvas.save()
            if (translate)
                canvas.translate(rend.position.xPos, rend.position.yPos)
            canvas.rotate(rend.position.rotation, rend.position.rotXOrigin, rend.position.rotYOrigin)
            when (rend.type) {
                Renderable.Type.RECT -> {
                    val shape = rend.shape as RectF
                    canvas.drawRect(shape.left, shape.top, shape.right, shape.bottom, rend.paint)
                }
                Renderable.Type.ROUNDRECT -> {
                    val shape = rend.shape as RectObject.RoundRect
                    canvas.drawRoundRect(shape.rectF, shape.radius, shape.radius, rend.paint)
                }
                Renderable.Type.OVAL -> {
                    canvas.drawOval(rend.shape as RectF, rend.paint)
                }
                Renderable.Type.TEXT -> {
                    val text = rend.shape as Layout
                    text.draw(canvas)
                }
                Renderable.Type.IMAGE -> {
                    val bitmap = rend.shape as Bitmap
                    canvas.drawBitmap(bitmap, 0F, 0F, rend.paint)
                }
                Renderable.Type.PATH -> {
                    val path = rend.shape as Path
                    canvas.drawPath(path, rend.paint)
                }
            }
            canvas.restore()
        }



        fun renderPNG(board: BoardObject, resolutionMultiplier: Float, outputStream: OutputStream) {
            val deserializer = Deserializer(resolutionMultiplier)
            val bitmap = Bitmap.createBitmap(
                    (board.width * resolutionMultiplier).toInt(),
                    (board.height * resolutionMultiplier).toInt(),
                    Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            drawRenderables(board.getRenderables(deserializer, true), canvas)
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
        }



    }
}