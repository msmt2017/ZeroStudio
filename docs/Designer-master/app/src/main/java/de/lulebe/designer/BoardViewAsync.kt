package de.lulebe.designer

import android.graphics.Bitmap
import android.graphics.Canvas
import de.lulebe.designer.data.BoardState
import de.lulebe.designer.data.objects.BoardObject
import kotlin.concurrent.thread


class BoardViewAsync(mActivity: BoardActivity, mBoardState: BoardState, mBoardObject: BoardObject) : BoardView(mActivity, mBoardState, mBoardObject) {


    override fun initRendering() {
        asyncThread()
        mBoardObject.addSizeChangeListener {
            mBoardState.boardScrollX = 0F
            mBoardState.boardScrollY = 0F
        }
        mBoardObject.addGridChangeListener {
            drawRulerPictures()
            invalidate()
        }
    }//initRendering


    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (mBufferBitmap != null)
            canvas.drawBitmap(mBufferBitmap, -mBoardState.boardScrollX, -mBoardState.boardScrollY, null)
        if (mBoardState.showUI) {
            drawRulers(canvas)
            drawSelection(canvas)
        }
    }//onDraw


    private fun asyncThread () {
        thread {
            mBoardObject.addSizeChangeListener {
                mBufferBitmap = Bitmap.createBitmap((mBoardObject.width * mDipRatio).toInt(), (mBoardObject.height * mDipRatio).toInt(), Bitmap.Config.ARGB_8888)
                Renderer.drawRenderables(mBoardObject.getRenderables(mDeserializer), Canvas(mBufferBitmap), true, false)
                postInvalidate()
            }
            mBoardObject.addChangeListener {
                Renderer.drawRenderables(mBoardObject.getRenderables(mDeserializer), Canvas(mBufferBitmap), true, false)
                postInvalidate()
            }
            mBufferBitmap = Bitmap.createBitmap((mBoardObject.width * mDipRatio).toInt(), (mBoardObject.height * mDipRatio).toInt(), Bitmap.Config.ARGB_8888)
            Renderer.drawRenderables(mBoardObject.getRenderables(mDeserializer), Canvas(mBufferBitmap), true, false)
            postInvalidate()
        }
    }//asyncThread
}
