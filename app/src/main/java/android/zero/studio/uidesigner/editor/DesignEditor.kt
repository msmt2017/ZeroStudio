package android.zero.studio.uidesigner.editor

import android.animation.LayoutTransition
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.util.AttributeSet
import android.view.DragEvent
import android.view.GestureDetector
import android.view.GestureDetector.SimpleOnGestureListener
import android.view.MotionEvent
import android.view.View
import android.view.View.OnDragListener
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.widget.TooltipCompat
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.constraintlayout.widget.ConstraintSet
import androidx.core.view.children
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.blankj.utilcode.util.VibrateUtils
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.color.MaterialColors
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import android.zero.studio.uidesigner.R
import android.zero.studio.uidesigner.adapters.AppliedAttributesAdapter
import android.zero.studio.uidesigner.databinding.ShowAttributesDialogBinding
import android.zero.studio.uidesigner.editor.dialogs.AttributeDialog
import android.zero.studio.uidesigner.editor.dialogs.BooleanDialog
import android.zero.studio.uidesigner.editor.dialogs.ColorDialog
import android.zero.studio.uidesigner.editor.dialogs.DimensionDialog
import android.zero.studio.uidesigner.editor.dialogs.EnumDialog
import android.zero.studio.uidesigner.editor.dialogs.FlagDialog
import android.zero.studio.uidesigner.editor.dialogs.IdDialog
import android.zero.studio.uidesigner.editor.dialogs.NumberDialog
import android.zero.studio.uidesigner.editor.dialogs.SizeDialog
import android.zero.studio.uidesigner.editor.dialogs.StringDialog
import android.zero.studio.uidesigner.editor.dialogs.ViewDialog
import android.zero.studio.uidesigner.editor.initializer.AttributeInitializer
import android.zero.studio.uidesigner.editor.initializer.AttributeMap
import android.zero.studio.uidesigner.editor.palette.layouts.ConstraintLayoutDesign
import android.zero.studio.uidesigner.managers.IdManager
import android.zero.studio.uidesigner.managers.IdManager.addId
import android.zero.studio.uidesigner.managers.IdManager.getViewId
import android.zero.studio.uidesigner.managers.IdManager.removeId
import android.zero.studio.uidesigner.managers.PreferencesManager.isEnableVibration
import android.zero.studio.uidesigner.managers.PreferencesManager.isShowStroke
import android.zero.studio.uidesigner.managers.UndoRedoManager
import android.zero.studio.uidesigner.tools.XmlLayoutGenerator
import android.zero.studio.uidesigner.tools.XmlLayoutParser
import android.zero.studio.uidesigner.utils.ArgumentUtil.parseType
import android.zero.studio.uidesigner.utils.Constants
import android.zero.studio.uidesigner.utils.FileUtil
import android.zero.studio.uidesigner.utils.InvokeUtil
import android.zero.studio.uidesigner.utils.Utils
import android.zero.studio.uidesigner.views.StructureView
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.sqrt

class DesignEditor : LinearLayout {
    var viewType: ViewType? = null
        set(value) {
            isBlueprint = viewType == ViewType.BLUEPRINT
            setBlueprintOnChildren()
            invalidate()
            field = value
        }
    var deviceConfiguration: DeviceConfiguration? = null
    var apiLevel: APILevel? = null

    lateinit var viewAttributeMap: HashMap<View, AttributeMap>
        private set

    private lateinit var paint: Paint
    private lateinit var shadow: View

    private lateinit var attributes: HashMap<String, List<HashMap<String, Any>>>
    private lateinit var parentAttributes: HashMap<String, List<HashMap<String, Any>>>
    private lateinit var initializer: AttributeInitializer

    private var isBlueprint = false
    private var structureView: StructureView? = null
    private var undoRedoManager: UndoRedoManager? = null

    constructor(context: Context) : super(context) {
        init(context)
    }

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs) {
        init(context)
    }

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(
        context,
        attrs,
        defStyleAttr
    ) {
        init(context)
    }

    private fun init(context: Context) {
        viewType = ViewType.DESIGN
        isBlueprint = false
        deviceConfiguration = DeviceConfiguration(DeviceSize.LARGE)
        initAttributes()
        shadow = View(context)
        paint = Paint()

        shadow.setBackgroundColor(
            MaterialColors.getColor(this, com.google.android.material.R.attr.colorOutline)
        )
        shadow.layoutParams = ViewGroup.LayoutParams(
            Utils.pxToDp(context, 50),
            Utils.pxToDp(context, 35)
        )
        paint.strokeWidth = Utils.pxToDp(context, 3).toFloat()

        orientation = VERTICAL
        setTransition(this)
        setDragListener(this)

        toggleStrokeWidgets()
        setBlueprintOnChildren()
    }

    override fun dispatchDraw(canvas: Canvas) {
        super.dispatchDraw(canvas)
        when (viewType) {
            ViewType.BLUEPRINT -> drawBlueprint(canvas)
            ViewType.DESIGN -> drawDesign(canvas)
            else -> drawDesign(canvas)
        }
        when (deviceConfiguration!!.size) {
            DeviceSize.SMALL -> {
                scaleX = 0.75f
                scaleY = 0.75f
            }

            DeviceSize.MEDIUM -> {
                scaleX = 0.85f
                scaleY = 0.85f
            }

            DeviceSize.LARGE, DeviceSize.SCREEN, DeviceSize.CUSTOM -> {
                scaleX = 1.0f
                scaleY = 1.0f
            }
        }
    }

    private fun drawBlueprint(canvas: Canvas) {
        paint.color = Constants.BLUEPRINT_DASH_COLOR
        setBackgroundColor(Constants.BLUEPRINT_BACKGROUND_COLOR)
        Utils.drawDashPathStroke(this, canvas, (paint))
    }

    private fun drawDesign(canvas: Canvas) {
        paint.color = Constants.DESIGN_DASH_COLOR
        setBackgroundColor(Utils.getSurfaceColor(context))
        Utils.drawDashPathStroke(this, canvas, (paint))
    }

    fun previewLayout(deviceConfiguration: DeviceConfiguration?, apiLevel: APILevel?) {
        this.deviceConfiguration = deviceConfiguration
        this.apiLevel = apiLevel
    }

    fun resizeLayout(deviceConfiguration: DeviceConfiguration?) {
        this.deviceConfiguration = deviceConfiguration
        invalidate()
    }

    private fun setTransition(group: ViewGroup) {
        if (group is RecyclerView) return
        LayoutTransition().apply {
            disableTransitionType(LayoutTransition.CHANGE_DISAPPEARING)
            enableTransitionType(LayoutTransition.CHANGING)
            setDuration(150)
        }.also { group.layoutTransition = it }
    }

    private fun toggleStrokeWidgets() {
        try {
            for (view in viewAttributeMap.keys) {
                val cls = view.javaClass
                val method = cls.getMethod("setStrokeEnabled", Boolean::class.javaPrimitiveType)
                method.invoke(view, isShowStroke)
            }
        } catch (e: Exception) {
            // Ignore
        }
    }

    private fun setBlueprintOnChildren() {
        try {
            for (view in viewAttributeMap.keys) {
                val cls = view.javaClass
                val method = cls.getMethod("setBlueprint", Boolean::class.javaPrimitiveType)
                method.invoke(view, isBlueprint)
            }
        } catch (e: Exception) {
            // Ignore
        }
    }

    private fun setDragListener(group: ViewGroup) {
        group.setOnDragListener(
            OnDragListener { host, event ->
                var parent = host as ViewGroup
                val draggedView =
                    if (event.localState is View) event.localState as View else null

                when (event.action) {
                    DragEvent.ACTION_DRAG_STARTED -> {
                        if (isEnableVibration) VibrateUtils.vibrate(100)
                        if ((draggedView != null
                                    && !(draggedView is AdapterView<*> && parent is AdapterView<*>))
                        ) parent.removeView(draggedView)
                    }

                    DragEvent.ACTION_DRAG_EXITED -> {
                        removeWidget(shadow)
                        updateUndoRedoHistory()
                    }

                    DragEvent.ACTION_DRAG_ENDED -> if (!event.result && draggedView != null) {
                        removeId(draggedView, draggedView is ViewGroup)
                        removeViewAttributes(draggedView)
                        viewAttributeMap.remove(draggedView)
                        updateStructure()
                    }

                    DragEvent.ACTION_DRAG_LOCATION, DragEvent.ACTION_DRAG_ENTERED -> if (shadow.parent == null) addWidget(
                        shadow,
                        parent,
                        event
                    )
                    else {
                        if (parent is LinearLayout) {
                            val index = parent.indexOfChild(shadow)
                            val newIndex = getIndexForNewChildOfLinear(parent, event)

                            if (index != newIndex) {
                                parent.removeView(shadow)
                                try {
                                    parent.addView(shadow, newIndex)
                                } catch (_: IllegalStateException) {
                                }
                            }
                        } else {
                            if (shadow.parent !== parent) addWidget(shadow, parent, event)
                        }
                    }

                    DragEvent.ACTION_DROP -> {
                        removeWidget(shadow)
                        if (childCount >= 1) {
                            if (getChildAt(0) !is ViewGroup) {
                                Toast.makeText(
                                    context,
                                    "A container is needed to add more than one widget.",
                                    Toast.LENGTH_SHORT
                                ).show()
                                return@OnDragListener true
                            } else {
                                if (parent is DesignEditor) parent = getChildAt(0) as ViewGroup
                            }
                        } else {
                            val rootLayout = InvokeUtil.createView(
                                "android.zero.studio.uidesigner.editor.palette.layouts.ConstraintLayoutDesign", context
                            ) as ViewGroup
                            rootLayout.layoutParams = ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT
                            )
                            rearrangeListeners(rootLayout)
                            setDragListener(rootLayout)
                            setTransition(rootLayout)

                            val rootLayoutMap = AttributeMap()
                            val rootId = getIdForNewView("root_container")
                            IdManager.addNewId(rootLayout, rootId)
                            rootLayoutMap.putValue("android:id", "@+id/$rootId")
                            rootLayoutMap.putValue("android:layout_width", "match_parent")
                            rootLayoutMap.putValue("android:layout_height", "match_parent")
                            viewAttributeMap[rootLayout] = rootLayoutMap

                            this.addView(rootLayout)
                            parent = rootLayout
                        }

                        if (draggedView == null) {
                            @Suppress("UNCHECKED_CAST") val data: HashMap<String, Any> =
                                event.localState as HashMap<String, Any>
                            val newView =
                                InvokeUtil.createView(
                                    data[Constants.KEY_CLASS_NAME].toString(), context
                                ) as View

                            newView.layoutParams = ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.WRAP_CONTENT,
                                ViewGroup.LayoutParams.WRAP_CONTENT
                            )
                            rearrangeListeners(newView)

                            if (newView is ViewGroup) {
                                setDragListener(newView)
                                setTransition(newView)
                            }
                            newView.minimumWidth = Utils.pxToDp(context, 20)
                            newView.minimumHeight = Utils.pxToDp(context, 20)

                            if (newView is EditText) newView.isFocusable = false

                            val map = AttributeMap()
                            val id = getIdForNewView(
                                newView.javaClass.superclass.simpleName
                                    .replace(" ".toRegex(), "_")
                                    .lowercase()
                            )
                            IdManager.addNewId(newView, id)
                            map.putValue("android:id", "@+id/$id")
                            map.putValue("android:layout_width", "wrap_content")
                            map.putValue("android:layout_height", "wrap_content")
                            viewAttributeMap[newView] = map

                            addWidget(newView, parent, event)

                            if (parent is ConstraintLayout) {
                                val x_dp = (event.x / resources.displayMetrics.density).toInt()
                                val y_dp = (event.y / resources.displayMetrics.density).toInt()

                                val attrInitializer = initializer
                                val allAttrs = attrInitializer.getAllAttributesForView(newView)

                                val attributesToApply = mapOf(
                                    "app:layout_constraintStart_toStartOf" to "parent",
                                    "app:layout_constraintTop_toTopOf" to "parent",
                                    "android:layout_marginStart" to "${x_dp}dp",
                                    "android:layout_marginTop" to "${y_dp}dp"
                                )

                                for ((key, value) in attributesToApply) {
                                    map.putValue(key, value)
                                    val attr = attrInitializer.getAttributeFromKey(key, allAttrs)
                                    if (attr != null) {
                                        attrInitializer.applyAttribute(newView, value, attr)
                                    }
                                }
                            }

                            try {
                                val cls: Class<*> = newView.javaClass
                                val setStrokeEnabled =
                                    cls.getMethod("setStrokeEnabled", Boolean::class.javaPrimitiveType)
                                val setBlueprint = cls.getMethod("setBlueprint", Boolean::class.javaPrimitiveType)
                                setStrokeEnabled.invoke(newView, isShowStroke)
                                setBlueprint.invoke(newView, isBlueprint)
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }

                            if (data.containsKey(Constants.KEY_DEFAULT_ATTRS)) {
                                @Suppress("UNCHECKED_CAST")
                                initializer.applyDefaultAttributes(
                                    newView, data[Constants.KEY_DEFAULT_ATTRS] as MutableMap<String, String>
                                )
                            }
                        } else {
                            if (parent is ConstraintLayout) {
                                val x_dp = (event.x / resources.displayMetrics.density).toInt()
                                val y_dp = (event.y / resources.displayMetrics.density).toInt()
                                val attrMap = viewAttributeMap[draggedView]!!
                                val allAttrs = initializer.getAllAttributesForView(draggedView)

                                val attributesToApply = mapOf(
                                    "app:layout_constraintStart_toStartOf" to "parent",
                                    "app:layout_constraintTop_toTopOf" to "parent",
                                    "android:layout_marginStart" to "${x_dp}dp",
                                    "android:layout_marginTop" to "${y_dp}dp"
                                )

                                val oldPosAttrs = listOf("android:layout_marginStart", "android:layout_marginTop", "app:layout_constraintStart_toStartOf", "app:layout_constraintTop_toTopOf")
                                for(oldAttr in oldPosAttrs) {
                                    if(attrMap.contains(oldAttr)) {
                                        attrMap.removeValue(oldAttr)
                                    }
                                }

                                for ((key, value) in attributesToApply) {
                                    attrMap.putValue(key, value)
                                    val attr = initializer.getAttributeFromKey(key, allAttrs)
                                    if (attr != null) {
                                        initializer.applyAttribute(draggedView, value, attr)
                                    }
                                }
                            }
                            addWidget(draggedView, parent, event)
                        }
                        updateStructure()
                        updateUndoRedoHistory()
                    }
                }
                true
            })
    }

    private fun getIdForNewView(name: String): String {
        var id = name
        var n = 0
        var firstTime = true
        while (IdManager.containsId(id)) {
            n++
            id = if (firstTime) "$name$n" else id.replace(
                id.elementAt(id.lastIndex).toString().toRegex(), n.toString()
            )
            firstTime = false
        }
        return id
    }

    fun loadLayoutFromParser(xml: String) {
        clearAll()

        if (xml.isEmpty()) return

        val parser = XmlLayoutParser(context)
        parser.parseFromXml(xml, context)

        addView(parser.root)
        viewAttributeMap = parser.viewAttributeMap

        for (view in (viewAttributeMap as HashMap<View, *>?)!!.keys) {
            rearrangeListeners(view)

            if (view is ViewGroup) {
                setDragListener(view)
                setTransition(view)
            }
            view.minimumWidth = Utils.pxToDp(context, 20)
            view.minimumHeight = Utils.pxToDp(context, 20)
        }

        updateStructure()
        toggleStrokeWidgets()

        initializer =
            AttributeInitializer(context, viewAttributeMap, attributes, parentAttributes)
    }

    fun undo() {
        if (undoRedoManager == null) return
        if (undoRedoManager!!.isUndoEnabled) loadLayoutFromParser(undoRedoManager!!.undo())
    }

    fun redo() {
        if (undoRedoManager == null) return
        if (undoRedoManager!!.isRedoEnabled) loadLayoutFromParser(undoRedoManager!!.redo())
    }

    private fun clearAll() {
        removeAllViews()
        structureView!!.clear()
        viewAttributeMap.clear()
    }

    fun setStructureView(view: StructureView?) {
        structureView = view
    }

    fun bindUndoRedoManager(manager: UndoRedoManager?) {
        undoRedoManager = manager
    }

    private fun updateStructure() {
        if (childCount == 0) structureView!!.clear()
        else structureView!!.setView(getChildAt(0))
    }

    fun updateUndoRedoHistory() {
        if (undoRedoManager == null) return
        val result = XmlLayoutGenerator().generate(this, false)
        undoRedoManager!!.addToHistory(result)
    }

    private fun rearrangeListeners(view: View) {
        var activeDragHandle: ConstraintLayoutDesign.DraggingInfo? = null
        val handleTouchRadius = Utils.pxToDp(context, 12).toFloat()
        var longPressDrag = false
        var downX: Float = 0f
        var downY: Float = 0f
        var lastMoveX: Float = 0f
        var lastMoveY: Float = 0f

        val gestureDetector = GestureDetector(context, object : SimpleOnGestureListener() {
            override fun onLongPress(event: MotionEvent) {
                if (activeDragHandle == null) {
                    longPressDrag = true
                    lastMoveX = event.x
                    lastMoveY = event.y
                    if (isEnableVibration) VibrateUtils.vibrate(50)
                }
            }
        })

        view.setOnTouchListener { v, event ->
            val isConstraintParent = v.parent is ConstraintLayoutDesign
            val parentLayout = v.parent as? ConstraintLayoutDesign

            val xInParent = v.left + event.x
            val yInParent = v.top + event.y

            gestureDetector.onTouchEvent(event)

            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    longPressDrag = false
                    activeDragHandle = null
                    downX = event.x
                    downY = event.y
                    if (isConstraintParent) {
                        activeDragHandle = getPressedHandle(v, xInParent, yInParent, handleTouchRadius)
                        if (activeDragHandle != null) {
                            if (isHandleConnected(v, activeDragHandle!!.handle)) {
                                removeConstraint(v, activeDragHandle!!.handle)
                                activeDragHandle = null
                                return@setOnTouchListener true
                            }
                            parentLayout?.setDraggingInfo(activeDragHandle)
                            return@setOnTouchListener true
                        }
                    }
                }
                MotionEvent.ACTION_MOVE -> {
                    if (activeDragHandle != null && parentLayout != null) {
                        activeDragHandle!!.currentX = xInParent
                        activeDragHandle!!.currentY = yInParent
                        parentLayout.setDraggingInfo(activeDragHandle)
                    } else if (longPressDrag && isConstraintParent) {
                        handleViewMove(v, event.x, event.y, lastMoveX, lastMoveY)
                        lastMoveX = event.x
                        lastMoveY = event.y
                    }
                }
                MotionEvent.ACTION_UP -> {
                    if (activeDragHandle != null && parentLayout != null) {
                        handleConstraintDrop(activeDragHandle!!, parentLayout)
                    } else if (longPressDrag) {
                        updateUndoRedoHistory()
                    } else {
                        if(abs(event.x - downX) < 10 && abs(event.y - downY) < 10) {
                           showDefinedAttributes(v)
                        }
                    }
                    activeDragHandle = null
                    parentLayout?.clearDraggingInfo()
                    longPressDrag = false
                }
            }
            true
        }
    }

    private fun handleViewMove(view: View, currentX: Float, currentY: Float, lastX: Float, lastY: Float) {
        val dx = currentX - lastX
        val dy = currentY - lastY

        val newX = view.x + dx
        val newY = view.y + dy

        view.x = newX
        view.y = newY

        val density = context.resources.displayMetrics.density
        val allAttrs = initializer.getAllAttributesForView(view)
        
        val newMarginStart = (newX / density).toInt()
        val newMarginTop = (newY / density).toInt()

        val marginStartAttr = initializer.getAttributeFromKey("android:layout_marginStart", allAttrs)
        if (marginStartAttr != null) {
            initializer.applyAttribute(view, "${newMarginStart}dp", marginStartAttr)
        }
        
        val marginTopAttr = initializer.getAttributeFromKey("android:layout_marginTop", allAttrs)
        if (marginTopAttr != null) {
            initializer.applyAttribute(view, "${newMarginTop}dp", marginTopAttr)
        }
    }

    private fun getPressedHandle(view: View, touchX: Float, touchY: Float, radius: Float): ConstraintLayoutDesign.DraggingInfo? {
        for (handle in ConstraintLayoutDesign.Handle.values()) {
            if (handle == ConstraintLayoutDesign.Handle.NONE) continue
            val handlePos = getHandlePosition(view, handle)
            if (handlePos != null && distance(touchX, touchY, handlePos.first, handlePos.second) < radius) {
                return ConstraintLayoutDesign.DraggingInfo(view, handle, handlePos.first, handlePos.second)
            }
        }
        return null
    }

    private fun getHandlePosition(view: View, handle: ConstraintLayoutDesign.Handle): Pair<Float, Float>? {
        val x = view.left.toFloat()
        val y = view.top.toFloat()
        val w = view.width.toFloat()
        val h = view.height.toFloat()
        return when (handle) {
            ConstraintLayoutDesign.Handle.LEFT -> Pair(x, y + h / 2)
            ConstraintLayoutDesign.Handle.RIGHT -> Pair(x + w, y + h / 2)
            ConstraintLayoutDesign.Handle.TOP -> Pair(x + w / 2, y)
            ConstraintLayoutDesign.Handle.BOTTOM -> Pair(x + w / 2, y + h)
            ConstraintLayoutDesign.Handle.TOP_LEFT -> Pair(x, y)
            ConstraintLayoutDesign.Handle.TOP_RIGHT -> Pair(x + w, y)
            ConstraintLayoutDesign.Handle.BOTTOM_LEFT -> Pair(x, y + h)
            ConstraintLayoutDesign.Handle.BOTTOM_RIGHT -> Pair(x + w, y + h)
            ConstraintLayoutDesign.Handle.BASELINE -> {
                if(view.baseline > 0) Pair(x + w / 2, y + view.baseline) else null
            }
            else -> null
        }
    }

    private fun distance(x1: Float, y1: Float, x2: Float, y2: Float): Float =
        sqrt((x1 - x2).pow(2) + (y1 - y2).pow(2))

    private fun handleConstraintDrop(dragInfo: ConstraintLayoutDesign.DraggingInfo, parent: ConstraintLayout) {
        if (dragInfo.handle in listOf(ConstraintLayoutDesign.Handle.TOP_LEFT, ConstraintLayoutDesign.Handle.TOP_RIGHT, ConstraintLayoutDesign.Handle.BOTTOM_LEFT, ConstraintLayoutDesign.Handle.BOTTOM_RIGHT)) {
            handleResizeDrop(dragInfo)
            return
        }

        val view = dragInfo.view
        val snapThreshold = Utils.pxToDp(context, 24).toFloat()
        val density = context.resources.displayMetrics.density
        
        var bestTarget: Triple<String, Int, String>? = null // <Attribute, TargetID, TargetName>
        var bestMargin = 0
        var bestDistance = Float.MAX_VALUE

        val targets = parent.children.filter { it != view && it.id != View.NO_ID }.toMutableList<View?>()
        targets.add(0, null) 

        for(targetView in targets) {
             val targetId = targetView?.id ?: ConstraintSet.PARENT_ID
             
             when (dragInfo.handle) {
                 ConstraintLayoutDesign.Handle.TOP -> {
                    val targetTop = targetView?.top?.toFloat() ?: 0f
                    val targetBottom = targetView?.bottom?.toFloat() ?: 0f
                    
                    var dist = abs(dragInfo.currentY - targetBottom)
                    if (dist < snapThreshold && dist < bestDistance) {
                        bestDistance = dist
                        bestTarget = Triple("app:layout_constraintTop_toBottomOf", targetId, if(targetView != null) "@id/${IdManager.idMap[targetView]}" else "parent")
                        bestMargin = ((dragInfo.currentY - targetBottom) / density).toInt()
                    }
                    dist = abs(dragInfo.currentY - targetTop)
                     if (dist < snapThreshold && dist < bestDistance) {
                        bestDistance = dist
                        bestTarget = Triple("app:layout_constraintTop_toTopOf", targetId, if(targetView != null) "@id/${IdManager.idMap[targetView]}" else "parent")
                        bestMargin = ((dragInfo.currentY - targetTop) / density).toInt()
                    }
                 }
                 ConstraintLayoutDesign.Handle.BOTTOM -> {
                     val targetTop = targetView?.top?.toFloat() ?: 0f
                     val targetBottom = targetView?.bottom?.toFloat() ?: parent.height.toFloat()

                    var dist = abs(dragInfo.currentY - targetTop)
                    if (dist < snapThreshold && dist < bestDistance) {
                        bestDistance = dist
                        bestTarget = Triple("app:layout_constraintBottom_toTopOf", targetId, if(targetView != null) "@id/${IdManager.idMap[targetView]}" else "parent")
                        bestMargin = ((targetTop - dragInfo.currentY) / density).toInt()
                    }
                    dist = abs(dragInfo.currentY - targetBottom)
                     if (dist < snapThreshold && dist < bestDistance) {
                        bestDistance = dist
                        bestTarget = Triple("app:layout_constraintBottom_toBottomOf", targetId, if(targetView != null) "@id/${IdManager.idMap[targetView]}" else "parent")
                        bestMargin = ((targetBottom - dragInfo.currentY) / density).toInt()
                    }
                 }
                 ConstraintLayoutDesign.Handle.LEFT -> {
                     val targetLeft = targetView?.left?.toFloat() ?: 0f
                     val targetRight = targetView?.right?.toFloat() ?: 0f
                     
                    var dist = abs(dragInfo.currentX - targetRight)
                    if (dist < snapThreshold && dist < bestDistance) {
                        bestDistance = dist
                        bestTarget = Triple("app:layout_constraintStart_toEndOf", targetId, if(targetView != null) "@id/${IdManager.idMap[targetView]}" else "parent")
                        bestMargin = ((dragInfo.currentX - targetRight) / density).toInt()
                    }
                     dist = abs(dragInfo.currentX - targetLeft)
                     if (dist < snapThreshold && dist < bestDistance) {
                        bestDistance = dist
                        bestTarget = Triple("app:layout_constraintStart_toStartOf", targetId, if(targetView != null) "@id/${IdManager.idMap[targetView]}" else "parent")
                        bestMargin = ((dragInfo.currentX - targetLeft) / density).toInt()
                    }
                 }
                 ConstraintLayoutDesign.Handle.RIGHT -> {
                    val targetLeft = targetView?.left?.toFloat() ?: 0f
                    val targetRight = targetView?.right?.toFloat() ?: parent.width.toFloat()

                    var dist = abs(dragInfo.currentX - targetLeft)
                    if (dist < snapThreshold && dist < bestDistance) {
                        bestDistance = dist
                        bestTarget = Triple("app:layout_constraintEnd_toStartOf", targetId, if(targetView != null) "@id/${IdManager.idMap[targetView]}" else "parent")
                        bestMargin = ((targetLeft - dragInfo.currentX) / density).toInt()
                    }
                     dist = abs(dragInfo.currentX - targetRight)
                     if (dist < snapThreshold && dist < bestDistance) {
                        bestDistance = dist
                        bestTarget = Triple("app:layout_constraintEnd_toEndOf", targetId, if(targetView != null) "@id/${IdManager.idMap[targetView]}" else "parent")
                        bestMargin = ((targetRight - dragInfo.currentX) / density).toInt()
                    }
                 }
                 ConstraintLayoutDesign.Handle.BASELINE -> {
                    if (targetView is TextView && targetView.baseline > 0) {
                        val dist = abs(dragInfo.currentY - (targetView.y + targetView.baseline))
                        if (dist < snapThreshold && dist < bestDistance) {
                            bestDistance = dist
                            bestTarget = Triple("app:layout_constraintBaseline_toBaselineOf", targetId, "@id/${IdManager.idMap[targetView]}")
                            bestMargin = 0
                        }
                    }
                 }
                 else -> {}
             }
        }

        val viewRect = Rect()
        view.getHitRect(viewRect)
        if(bestTarget == null && viewRect.contains(dragInfo.currentX.toInt() - view.left, dragInfo.currentY.toInt() - view.top)) {
            removeConstraint(view, dragInfo.handle)
            return
        }

        if (bestTarget != null) {
            val (constraintAttrKey, _, targetName) = bestTarget
            clearRelevantConstraints(view, dragInfo.handle)
            val allAttrs = initializer.getAllAttributesForView(view)
            val constraintAttr = initializer.getAttributeFromKey(constraintAttrKey, allAttrs)

            if (constraintAttr != null) {
                initializer.applyAttribute(view, targetName, constraintAttr)
                
                val marginKey = getMarginKeyForHandle(dragInfo.handle)
                if (marginKey != null) {
                    val marginAttr = initializer.getAttributeFromKey(marginKey, allAttrs)
                    if (marginAttr != null) {
                        initializer.applyAttribute(view, "${bestMargin}dp", marginAttr)
                    }
                }
                updateUndoRedoHistory()
            }
        }
    }

    private fun handleResizeDrop(dragInfo: ConstraintLayoutDesign.DraggingInfo) {
        val view = dragInfo.view
        val density = context.resources.displayMetrics.density
        
        var newWidthPx = view.width.toFloat()
        var newHeightPx = view.height.toFloat()

        when(dragInfo.handle) {
             ConstraintLayoutDesign.Handle.TOP_RIGHT -> newWidthPx = dragInfo.currentX - view.left
             ConstraintLayoutDesign.Handle.BOTTOM_RIGHT -> {
                newWidthPx = dragInfo.currentX - view.left
                newHeightPx = dragInfo.currentY - view.top
             }
             ConstraintLayoutDesign.Handle.BOTTOM_LEFT -> newHeightPx = dragInfo.currentY - view.top
             ConstraintLayoutDesign.Handle.TOP_LEFT -> { /* More complex, involves moving the view */ }
             else -> return
        }
        
        val newWidthDp = (newWidthPx / density).toInt().coerceAtLeast(24)
        val newHeightDp = (newHeightPx / density).toInt().coerceAtLeast(24)
        
        val allAttrs = initializer.getAllAttributesForView(view)
        val widthAttr = initializer.getAttributeFromKey("android:layout_width", allAttrs)
        val heightAttr = initializer.getAttributeFromKey("android:layout_height", allAttrs)

        if (widthAttr != null) initializer.applyAttribute(view, "${newWidthDp}dp", widthAttr)
        if (heightAttr != null) initializer.applyAttribute(view, "${newHeightDp}dp", heightAttr)

        updateUndoRedoHistory()
    }

    private fun getMarginKeyForHandle(handle: ConstraintLayoutDesign.Handle): String? {
        return when (handle) {
            ConstraintLayoutDesign.Handle.TOP -> "android:layout_marginTop"
            ConstraintLayoutDesign.Handle.BOTTOM -> "android:layout_marginBottom"
            ConstraintLayoutDesign.Handle.LEFT -> "android:layout_marginStart"
            ConstraintLayoutDesign.Handle.RIGHT -> "android:layout_marginEnd"
            else -> null
        }
    }
    
    private fun isHandleConnected(view: View, handle: ConstraintLayoutDesign.Handle): Boolean {
        val params = view.layoutParams as? ConstraintLayout.LayoutParams ?: return false
        return when (handle) {
            ConstraintLayoutDesign.Handle.TOP -> params.topToTop != -1 || params.topToBottom != -1
            ConstraintLayoutDesign.Handle.BOTTOM -> params.bottomToTop != -1 || params.bottomToBottom != -1
            ConstraintLayoutDesign.Handle.LEFT -> params.leftToLeft != -1 || params.leftToRight != -1 || params.startToStart != -1 || params.startToEnd != -1
            ConstraintLayoutDesign.Handle.RIGHT -> params.rightToLeft != -1 || params.rightToRight != -1 || params.endToStart != -1 || params.endToEnd != -1
            ConstraintLayoutDesign.Handle.BASELINE -> params.baselineToBaseline != -1
            else -> false
        }
    }
    
    private fun removeConstraint(view: View, handle: ConstraintLayoutDesign.Handle) {
        clearRelevantConstraints(view, handle)
        Toast.makeText(context, "Constraint removed", Toast.LENGTH_SHORT).show()
        
        val tempParent = view.parent as ViewGroup
        val index = tempParent.indexOfChild(view)
        val lp = view.layoutParams
        tempParent.removeViewAt(index)
        tempParent.addView(view, index, lp)
        
        val attrMap = viewAttributeMap[view]!!
        val allAttrs = initializer.getAllAttributesForView(view)
        for(key in attrMap.keySet()){
            val attr = initializer.getAttributeFromKey(key, allAttrs)
            if(attr != null) {
                initializer.applyAttribute(view, attrMap.getValue(key), attr)
            }
        }
        updateUndoRedoHistory()
    }
    
    private fun clearRelevantConstraints(view: View, handle: ConstraintLayoutDesign.Handle) {
        val attrMap = viewAttributeMap[view] ?: return
        val keysToRemove = when(handle) {
            ConstraintLayoutDesign.Handle.TOP -> listOf("app:layout_constraintTop_toTopOf", "app:layout_constraintTop_toBottomOf", "android:layout_marginTop")
            ConstraintLayoutDesign.Handle.BOTTOM -> listOf("app:layout_constraintBottom_toBottomOf", "app:layout_constraintBottom_toTopOf", "android:layout_marginBottom")
            ConstraintLayoutDesign.Handle.LEFT -> listOf("app:layout_constraintStart_toStartOf", "app:layout_constraintStart_toEndOf", "android:layout_marginStart", "app:layout_constraintLeft_toLeftOf", "app:layout_constraintLeft_toRightOf", "android:layout_marginLeft")
            ConstraintLayoutDesign.Handle.RIGHT -> listOf("app:layout_constraintEnd_toEndOf", "app:layout_constraintEnd_toStartOf", "android:layout_marginEnd", "app:layout_constraintRight_toRightOf", "app:layout_constraintRight_toLeftOf", "android:layout_marginRight")
            ConstraintLayoutDesign.Handle.BASELINE -> listOf("app:layout_constraintBaseline_toBaselineOf")
            else -> listOf()
        }
        for(key in keysToRemove) {
            if(attrMap.contains(key)) {
                attrMap.removeValue(key)
            }
        }
    }

    private fun addWidget(view: View, newParent: ViewGroup, event: DragEvent) {
        removeWidget(view)
        if (newParent is LinearLayout) {
            val index = getIndexForNewChildOfLinear(newParent, event)
            newParent.addView(view, index)
        } else {
            try {
                newParent.addView(view, newParent.childCount)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun removeWidget(view: View) {
        (view.parent as ViewGroup?)?.removeView(view)
    }

    private fun getIndexForNewChildOfLinear(layout: LinearLayout, event: DragEvent): Int {
        val orientation = layout.orientation
        if (orientation == HORIZONTAL) {
            var index = 0
            for (i in 0 until layout.childCount) {
                val child = layout.getChildAt(i)
                if (child === shadow) continue
                if (child.right < event.x) index++
            }
            return index
        }
        if (orientation == VERTICAL) {
            var index = 0
            for (i in 0 until layout.childCount) {
                val child = layout.getChildAt(i)
                if (child === shadow) continue
                if (child.bottom < event.y) index++
            }
            return index
        }
        return -1
    }

    fun showDefinedAttributes(target: View) {
        val keys = viewAttributeMap[target]!!.keySet()
        val values = viewAttributeMap[target]!!.values()

        val attrs: MutableList<HashMap<String, Any>> = ArrayList()
        val allAttrs = initializer.getAllAttributesForView(target)

        val dialog = BottomSheetDialog(context)
        val binding =
            ShowAttributesDialogBinding.inflate(dialog.layoutInflater)

        dialog.setContentView(binding.root)
        TooltipCompat.setTooltipText(binding.btnAdd, "Add attribute")
        TooltipCompat.setTooltipText(binding.btnDelete, "Delete")

        for (key: String in keys) {
            for (map: HashMap<String, Any> in allAttrs) {
                if ((map[Constants.KEY_ATTRIBUTE_NAME].toString() == key)) {
                    attrs.add(map)
                    break
                }
            }
        }

        val appliedAttributesAdapter = AppliedAttributesAdapter(attrs, values)

        appliedAttributesAdapter.onClick = {
            showAttributeEdit(target, keys[it])
            dialog.dismiss()
        }

        appliedAttributesAdapter.onRemoveButtonClick = {
            dialog.dismiss()

            val view = removeAttribute(target, keys[it])
            showDefinedAttributes(view)
        }

        binding.attributesList.adapter = appliedAttributesAdapter
        binding.attributesList.layoutManager =
            LinearLayoutManager(context, RecyclerView.VERTICAL, false)
        binding.viewName.text = target.javaClass.superclass.simpleName
        binding.viewFullName.text = target.javaClass.superclass.name
        binding.btnAdd.setOnClickListener {
            showAvailableAttributes(target)
            dialog.dismiss()
        }

        binding.btnDelete.setOnClickListener {
            MaterialAlertDialogBuilder(context)
                .setTitle(R.string.delete_view)
                .setMessage(R.string.msg_delete_view)
                .setNegativeButton(
                    R.string.no
                ) { d, _ ->
                    d.dismiss()
                }
                .setPositiveButton(
                    R.string.yes
                ) { _, _ ->
                    removeId(target, target is ViewGroup)
                    removeViewAttributes(target)
                    removeWidget(target)
                    updateStructure()
                    updateUndoRedoHistory()
                    dialog.dismiss()
                }
                .show()
        }

        dialog.show()
    }

    private fun showAvailableAttributes(target: View) {
        val availableAttrs =
            initializer.getAvailableAttributesForView(target)
        val names: MutableList<String> = ArrayList()

        for (attr: HashMap<String, Any> in availableAttrs) {
            names.add(attr["name"].toString())
        }

        MaterialAlertDialogBuilder(context)
            .setTitle("Available attributes")
            .setAdapter(
                ArrayAdapter(context, android.R.layout.simple_list_item_1, names)
            ) { _, w ->
                showAttributeEdit(
                    target, availableAttrs[w][Constants.KEY_ATTRIBUTE_NAME].toString()
                )
            }
            .show()
    }

    private fun showAttributeEdit(target: View, attributeKey: String) {
        val allAttrs = initializer.getAllAttributesForView(target)
        val currentAttr =
            initializer.getAttributeFromKey(attributeKey, allAttrs)
        val attributeMap = viewAttributeMap[target]

        val argumentTypes =
            currentAttr?.get(Constants.KEY_ARGUMENT_TYPE)?.toString()?.split("\\|".toRegex())
                ?.dropLastWhile { it.isEmpty() }
                ?.toTypedArray()

        if (argumentTypes != null) {
            if (argumentTypes.size > 1) {
                if (attributeMap!!.contains(attributeKey)) {
                    val argumentType =
                        parseType(attributeMap.getValue(attributeKey), argumentTypes)
                    showAttributeEdit(target, attributeKey, argumentType)
                    return
                }
                MaterialAlertDialogBuilder(context)
                    .setTitle(R.string.select_arg_type)
                    .setAdapter(
                        ArrayAdapter(
                            context, android.R.layout.simple_list_item_1, argumentTypes
                        )
                    ) { _, w ->
                        showAttributeEdit(target, attributeKey, argumentTypes[w])
                    }
                    .show()

                return
            }
        }
        showAttributeEdit(target, attributeKey, argumentTypes?.get(0))
    }

    @Suppress("UNCHECKED_CAST")
    private fun showAttributeEdit(
        target: View, attributeKey: String, argumentType: String?
    ) {
        val allAttrs = initializer.getAllAttributesForView(target)
        val currentAttr =
            initializer.getAttributeFromKey(attributeKey, allAttrs)
        val attributeMap = viewAttributeMap[target]

        var savedValue =
            if (attributeMap!!.contains(attributeKey)) attributeMap.getValue(attributeKey) else ""
        val defaultValue =
            if (currentAttr?.containsKey(Constants.KEY_DEFAULT_VALUE) == true)
                currentAttr[Constants.KEY_DEFAULT_VALUE].toString()
            else null
        val constant =
            if (currentAttr?.containsKey(Constants.KEY_CONSTANT) == true
            ) currentAttr[Constants.KEY_CONSTANT].toString()
            else null

        val context = context

        var dialog: AttributeDialog? = null

        when (argumentType) {
            Constants.ARGUMENT_TYPE_SIZE -> dialog = SizeDialog(context, savedValue)
            Constants.ARGUMENT_TYPE_DIMENSION -> dialog =
                DimensionDialog(context, savedValue, currentAttr?.get("dimensionUnit")?.toString())

            Constants.ARGUMENT_TYPE_ID -> dialog = IdDialog(context, savedValue)
            Constants.ARGUMENT_TYPE_VIEW -> dialog = ViewDialog(context, savedValue, constant)
            Constants.ARGUMENT_TYPE_BOOLEAN -> dialog = BooleanDialog(context, savedValue)
            Constants.ARGUMENT_TYPE_DRAWABLE -> {
                if (savedValue.startsWith("@drawable/")) {
                    savedValue = savedValue.replace("@drawable/", "")
                }
                dialog = StringDialog(context, savedValue, Constants.ARGUMENT_TYPE_DRAWABLE)
            }

            Constants.ARGUMENT_TYPE_STRING -> {
                if (savedValue.startsWith("@string/")) {
                    savedValue = savedValue.replace("@string/", "")
                }
                dialog = StringDialog(context, savedValue, Constants.ARGUMENT_TYPE_STRING)
            }

            Constants.ARGUMENT_TYPE_TEXT -> dialog =
                StringDialog(context, savedValue, Constants.ARGUMENT_TYPE_TEXT)

            Constants.ARGUMENT_TYPE_INT -> dialog =
                NumberDialog(context, savedValue, Constants.ARGUMENT_TYPE_INT)

            Constants.ARGUMENT_TYPE_FLOAT -> dialog =
                NumberDialog(context, savedValue, Constants.ARGUMENT_TYPE_FLOAT)

            Constants.ARGUMENT_TYPE_FLAG -> dialog =
                FlagDialog(context, savedValue, currentAttr?.get("arguments") as ArrayList<String>?)

            Constants.ARGUMENT_TYPE_ENUM -> dialog =
                EnumDialog(context, savedValue, currentAttr?.get("arguments") as ArrayList<String>?)

            Constants.ARGUMENT_TYPE_COLOR -> dialog = ColorDialog(context, savedValue)
        }
        if (dialog == null) return

        dialog.setTitle(currentAttr?.get("name")?.toString())
        dialog.setOnSaveValueListener {
            if (defaultValue != null && (defaultValue == it)) {
                if (attributeMap.contains(attributeKey)) removeAttribute(target, attributeKey)
            } else {
                if (currentAttr != null) {
                    initializer.applyAttribute(target, it!!, currentAttr)
                }
                showDefinedAttributes(target)
                updateUndoRedoHistory()
                updateStructure()
            }
        }

        dialog.show()
    }

    private fun removeViewAttributes(view: View) {
        viewAttributeMap.remove(view)
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                removeViewAttributes(view.getChildAt(i))
            }
        }
    }

    private fun removeAttribute(target: View, attributeKey: String): View {
        @Suppress("NAME_SHADOWING")
        var target = target
        val allAttrs = initializer.getAllAttributesForView(target)
        val currentAttr =
            initializer.getAttributeFromKey(attributeKey, allAttrs)

        val attributeMap = viewAttributeMap[target]

        if (currentAttr != null) {
            if (currentAttr.containsKey(Constants.KEY_CAN_DELETE)) return target
        }

        val name =
            if (attributeMap!!.contains("android:id")) attributeMap.getValue("android:id") else null
        val id = if (name != null) getViewId(name.replace("@+id/", "")) else -1
        attributeMap.removeValue(attributeKey)

        if ((attributeKey == "android:id")) {
            removeId(target, false)
            target.id = -1
            target.requestLayout()

            // delete all id attributes for views
            for (view: View in viewAttributeMap.keys) {
                val map = viewAttributeMap[view]

                for (key: String in map!!.keySet()) {
                    val value = map.getValue(key)

                    if (value.startsWith("@id/") && (value == name!!.replace("+", ""))) map.removeValue(key)
                }
            }
            updateStructure()
            return target
        }

        viewAttributeMap.remove(target)

        val parent = target.parent as ViewGroup
        val indexOfView = parent.indexOfChild(target)

        parent.removeView(target)

        val childs: MutableList<View> = ArrayList()

        if (target is ViewGroup) {
            val group = target

            if (group.childCount > 0) {
                for (i in 0 until group.childCount) {
                    childs.add(group.getChildAt(i))
                }
            }

            group.removeAllViews()
        }

        if (name != null) removeId(target, false)

        target = InvokeUtil.createView(target.javaClass.name, context) as View
        rearrangeListeners(target)

        if (target is ViewGroup) {
            target.setMinimumWidth(Utils.pxToDp(context, 20))
            target.setMinimumHeight(Utils.pxToDp(context, 20))
            val group = target
            if (childs.size > 0) {
                for (i in childs.indices) {
                    group.addView(childs[i])
                }
            }
            setTransition(group)
        }

        parent.addView(target, indexOfView)
        viewAttributeMap[target] = attributeMap

        if (name != null) {
            addId(target, name, id)
            target.requestLayout()
        }

        val keys = attributeMap.keySet()
        val values = attributeMap.values()
        val attrs: MutableList<HashMap<String, Any>> = ArrayList()

        for (key: String in keys) {
            for (map: HashMap<String, Any> in allAttrs) {
                if ((map[Constants.KEY_ATTRIBUTE_NAME].toString() == key)) {
                    attrs.add(map)
                    break
                }
            }
        }

        for (i in keys.indices) {
            val key = keys[i]
            if ((key == "android:id")) continue
            initializer.applyAttribute(target, values[i], attrs[i])
        }

        try {
            val cls: Class<*> = target.javaClass
            val method = cls.getMethod("setStrokeEnabled", Boolean::class.javaPrimitiveType)
            method.invoke(target, isShowStroke)
        } catch (e: Exception) {
            e.printStackTrace()
        }
        updateStructure()
        updateUndoRedoHistory()
        return target
    }

    private fun initAttributes() {
        attributes = convertJsonToJavaObject(Constants.ATTRIBUTES_FILE)
        parentAttributes = convertJsonToJavaObject(Constants.PARENT_ATTRIBUTES_FILE)
        viewAttributeMap = HashMap()
        initializer =
            AttributeInitializer(context, viewAttributeMap, attributes, parentAttributes)
    }

    private fun convertJsonToJavaObject(filePath: String): HashMap<String, List<HashMap<String, Any>>> {
        return Gson()
            .fromJson(
                FileUtil.readFromAsset(filePath, context),
                object : TypeToken<HashMap<String?, ArrayList<HashMap<String?, Any?>?>?>?>() {}.type
            )
    }

    enum class ViewType {
        DESIGN,
        BLUEPRINT
    }
}