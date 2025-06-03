package de.lulebe.designer

import android.graphics.*
import android.os.Build
import android.support.v4.view.GestureDetectorCompat
import android.util.TypedValue
import android.view.GestureDetector
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import de.lulebe.designer.data.BoardState
import de.lulebe.designer.data.Deserializer
import de.lulebe.designer.data.objects.BaseObject
import de.lulebe.designer.data.objects.BoardObject


open class BoardView(val mActivity: BoardActivity, val mBoardState: BoardState, val mBoardObject: BoardObject) : View(mActivity) {


    protected var mBufferBitmap: Bitmap? = null
    protected var mDipRatio = 1F
    protected val mDeserializer: Deserializer

    protected val mRulerPaint = Paint()
    protected val mGridPaint = Paint()

    protected val mHorizRulerPic = Picture()
    protected val mVertRulerPic = Picture()

    protected var mObjectdragTouchee: BaseObject? = null
    protected var mObjectdragToucheeHandle = 0
    protected var mObjectdragLastX: Int = 0
    protected var mObjectdragLastY: Int = 0


    protected val mGestureListener = object: GestureDetector.SimpleOnGestureListener() {

        override fun onDown(event: MotionEvent?): Boolean {
            if (event == null)
                return false
            if (mBoardState.selected.isNotEmpty()) {
                val x = eventXOnBoard(event)
                val y = eventYOnBoard(event)
                val handleAtPos = mBoardState.selected[0].getHandleAt(x, y)
                if (handleAtPos >= 0) {
                    mObjectdragTouchee = mBoardState.selected[0]
                    mObjectdragToucheeHandle = handleAtPos
                    mObjectdragLastX = x
                    mObjectdragLastY = y
                }
            }
            return true
        }

        override fun onSingleTapUp(event: MotionEvent): Boolean {
            mBoardState.selectedSet(mBoardObject.getObjectAtPosition(eventXOnBoard(event), eventYOnBoard(event)))
            return true
        }

        override fun onDoubleTap(event: MotionEvent): Boolean {
            if (mBoardState.importing) return false
            val tapped = mBoardObject.getObjectAtPosition(eventXOnBoard(event), eventYOnBoard(event))
            mBoardState.selectedSet(tapped)
            if (tapped is BoardObject)
                mActivity.openGroup(tapped)
            else {
                mBoardState.rightPanelExpanded = true
            }
            return true
        }

        override fun onLongPress(event: MotionEvent) {
            val pressed = mBoardObject.getObjectAtPosition(eventXOnBoard(event), eventYOnBoard(event)) ?: return
            performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            if (!mBoardState.selected.contains(pressed))
                    mBoardState.selectedAdd(pressed)
            else
                mBoardState.selectedRemove(pressed)
        }

        override fun onScroll(e1: MotionEvent?, e2: MotionEvent?, distanceX: Float, distanceY: Float): Boolean {
            if (!mBoardState.panningActive) return false
                mBoardState.boardScrollX += distanceX
                mBoardState.boardScrollY += distanceY
            return true
        }
    }//mGestureListener
    protected val mGestureDetector = GestureDetectorCompat(mActivity, mGestureListener)






    init {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP)
            setLayerType(LAYER_TYPE_SOFTWARE, null)
        mDipRatio = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 1F, resources.displayMetrics)
        mDeserializer = Deserializer(mDipRatio)

        setBackgroundColor(Color.LTGRAY)
        mRulerPaint.color = Color.BLACK
        mRulerPaint.strokeWidth = mDipRatio
        mRulerPaint.style = Paint.Style.STROKE
        mGridPaint.color = Color.GRAY
        mGridPaint.alpha = 30
        mGridPaint.strokeWidth = mDipRatio
        mGridPaint.style = Paint.Style.STROKE

        mBoardState.addListener(object: BoardState.BoardStateListener() {
            override fun onShowGrid (shown: Boolean) {
                drawRulerPictures()
                invalidate()
            }
            override fun onShowUI (shown: Boolean) {
                invalidate()
            }
            override fun onBoardScrollX(scrollX: Float) {
                invalidate()
            }
            override fun onBoardScrollY(scrollY: Float) {
                invalidate()
            }
            override fun onSelectChange(objs: List<BaseObject>) {
                invalidate()
            }
            override fun onPanningActive(active: Boolean) {
                mObjectdragTouchee?.resetHandles()
                invalidate()
            }
        })

        drawRulerPictures()

        initRendering()
    }//init

    open protected fun initRendering() {
        mBoardObject.addSizeChangeListener {
            mBoardState.boardScrollX = 0F
            mBoardState.boardScrollY = 0F
            mBufferBitmap?.recycle()
            mBufferBitmap = Bitmap.createBitmap((mBoardObject.width * mDipRatio).toInt(), (mBoardObject.height * mDipRatio).toInt(), Bitmap.Config.ARGB_8888)
            Renderer.drawRenderables(mBoardObject.getRenderables(mDeserializer), Canvas(mBufferBitmap), true, false)
        }
        mBoardObject.addGridChangeListener {
            drawRulerPictures()
            invalidate()
        }
        mBoardObject.addChangeListener {
            Renderer.drawRenderables(mBoardObject.getRenderables(mDeserializer), Canvas(mBufferBitmap), true, false)
            invalidate()
        }
        mBufferBitmap?.recycle()
        mBufferBitmap = Bitmap.createBitmap((mBoardObject.width * mDipRatio).toInt(), (mBoardObject.height * mDipRatio).toInt(), Bitmap.Config.ARGB_8888)
        Renderer.drawRenderables(mBoardObject.getRenderables(mDeserializer), Canvas(mBufferBitmap), true, false)
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        drawRulerPictures()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (mBufferBitmap == null)
            return
        canvas.drawBitmap(mBufferBitmap, -mBoardState.boardScrollX, -mBoardState.boardScrollY, null)
        if (mBoardState.showUI) {
            drawRulers(canvas)
            drawSelection(canvas)
        }
    }


    override fun onTouchEvent(event: MotionEvent?): Boolean {
        if (event == null) {
            return false
        }
        if (mGestureDetector.onTouchEvent(event)) {
            return true
        }
        if (!mBoardState.panningActive) {
            return pan(event)
        }
        return false
    }


    protected fun eventXOnBoard (event: MotionEvent) : Int {
        return ((event.x + mBoardState.boardScrollX) / mDipRatio).toInt()
    }
    protected fun eventYOnBoard (event: MotionEvent) : Int {
        return ((event.y + mBoardState.boardScrollY) / mDipRatio).toInt()
    }


    protected fun drawRulers (canvas: Canvas) {
        val interval = mDipRatio * mBoardObject.gridSize * mBoardObject.gridInterval
        canvas.save()
        canvas.translate(- (mBoardState.boardScrollX % interval), 0F)
        canvas.drawPicture(mVertRulerPic)
        canvas.restore()
        canvas.save()
        canvas.translate(0F, - (mBoardState.boardScrollY % interval))
        canvas.drawPicture(mHorizRulerPic)
        canvas.restore()
    }


    protected fun drawSelection(canvas: Canvas) {
        mBoardState.selected.forEachIndexed { index, sel ->
            sel.getHandleRenderables(mDeserializer, -mBoardState.boardScrollX, -mBoardState.boardScrollY, !mBoardState.panningActive && index == 0)
                    .forEach { rend ->
                        if (index != 0)
                            rend.paint.color = Color.DKGRAY
                        Renderer.drawRenderable(rend, canvas, true)
                    }
        }
    }



    protected fun drawRulerPictures () {
        val size = mBoardObject.gridSize
        val interval = mBoardObject.gridInterval
        val dipSize = mDipRatio * size
        val width = this.width + 2*dipSize*interval
        val height = this.height + 2*dipSize*interval
        var canvas = mVertRulerPic.beginRecording(width.toInt(), height.toInt())
        var curLine = 0F
        var steps = 0
        while (curLine < width) {
            if (steps == 0) {
                canvas.drawLine(curLine, 0F, curLine, mDipRatio * 4 * 3, mRulerPaint)
                if (mBoardState.showGrid)
                    canvas.drawLine(curLine, 0F, curLine, height, mGridPaint)
            } else {
                canvas.drawLine(curLine, 0F, curLine, mDipRatio * 4, mRulerPaint)
            }
            curLine += dipSize
            steps++
            if (steps == interval)
                steps = 0
        }
        mVertRulerPic.endRecording()

        canvas = mHorizRulerPic.beginRecording(width.toInt(), height.toInt())
        curLine = 0F
        steps = 0
        while (curLine < height) {
            if (steps == 0) {
                canvas.drawLine(0F, curLine, mDipRatio * 4 * 3, curLine, mRulerPaint)
                if (mBoardState.showGrid)
                    canvas.drawLine(0F, curLine, width, curLine, mGridPaint)
            } else {
                canvas.drawLine(0F, curLine, mDipRatio * 4, curLine, mRulerPaint)
            }
            curLine += dipSize
            steps++
            if (steps == interval)
                steps = 0
        }
        mHorizRulerPic.endRecording()
    }



    protected fun pan (event: MotionEvent) : Boolean {
        if (mObjectdragTouchee != null && event.action == MotionEvent.ACTION_MOVE) {
            val target = mObjectdragTouchee!!
            val rawX = eventXOnBoard(event)
            val rawY = eventYOnBoard(event)
            val snappedX: Int
            val snappedY: Int
            when (mObjectdragToucheeHandle) {
                0 -> {
                    snappedX = snap(rawX, target.xposMoving + (rawX - mObjectdragLastX))
                    snappedY = snap(rawY, target.yposMoving + (rawY - mObjectdragLastY))
                    mBoardState.selected.forEach {
                        it.movingTo(it.xposMoving + (snappedX - mObjectdragLastX), it.yposMoving + (snappedY - mObjectdragLastY))
                    }
                }
                1 -> {
                    snappedX = snap(rawX, target.xposMoving + (rawX - mObjectdragLastX))
                    snappedY = rawY
                    target.setHandlePosition(1, snappedX, snappedY)
                }
                2 -> {
                    snappedX = rawX
                    snappedY = snap(rawY, target.yposMoving + (rawY - mObjectdragLastY))
                    target.setHandlePosition(2, snappedX, snappedY)
                }
                3 -> {
                    snappedX = snap(rawX, target.xposMoving + target.widthMoving + (rawX - mObjectdragLastX))
                    snappedY = rawY
                    target.setHandlePosition(3, snappedX, snappedY)
                }
                else -> { //4
                    snappedX = rawX
                    snappedY = snap(rawY, target.yposMoving + target.heightMoving + (rawY - mObjectdragLastY))
                    target.setHandlePosition(4, snappedX, snappedY)
                }
            }

            mObjectdragLastX = snappedX
            mObjectdragLastY = snappedY
            invalidate()
            return true
        }
        if (mObjectdragTouchee != null && event.action == MotionEvent.ACTION_UP) {
            mBoardState.selected.forEach { it.applyMovement() }
            mObjectdragTouchee = null
            return true
        }
        return false
    }


    protected fun snap (raw: Int, snapPoint: Int) : Int {
        if (mBoardState.showGrid) {
            val gridfac = mBoardObject.gridSize * mBoardObject.gridInterval
            val gridsnapmargin = gridfac / 4
            val distance = Math.abs(snapPoint % gridfac)
            val distanceInverted = gridfac - distance
            if (distance <= gridsnapmargin) { //snap to left/top
                if (snapPoint > 0)
                    return raw - distance
                else
                    return raw + distance
            }
            if (distanceInverted <= gridsnapmargin) { //snap to right/bottom
                if (snapPoint > 0)
                    return raw + distanceInverted
                else
                    return raw - distanceInverted
            }
        }
        return raw //don't snap
    }
}
