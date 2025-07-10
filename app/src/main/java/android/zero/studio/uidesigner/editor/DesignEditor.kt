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
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.PopupMenu
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
import android.zero.studio.uidesigner.editor.callers.layouts.ConstraintUpdater
import android.zero.studio.uidesigner.editor.dialogs.*
import android.zero.studio.uidesigner.editor.initializer.AttributeInitializer
import android.zero.studio.uidesigner.editor.initializer.AttributeMap
import android.zero.studio.uidesigner.editor.palette.layouts.ConstraintLayoutDesign
import android.zero.studio.uidesigner.editor.palette.layouts.GuidelineDesign
import android.zero.studio.uidesigner.managers.IdManager
import android.zero.studio.uidesigner.managers.IdManager.addId
import android.zero.studio.uidesigner.managers.IdManager.removeId
import android.zero.studio.uidesigner.managers.PreferencesManager.isEnableVibration
import android.zero.studio.uidesigner.managers.PreferencesManager.isShowStroke
import android.zero.studio.uidesigner.managers.UndoRedoManager
import android.zero.studio.uidesigner.tools.XmlLayoutGenerator
import android.zero.studio.uidesigner.tools.XmlLayoutParser
import android.zero.studio.uidesigner.utils.ArgumentUtil.parseType
import android.zero.studio.uidesigner.utils.Constants
import android.zero.studio.uidesigner.utils.DimensionUtil
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
            isBlueprint = value == ViewType.BLUEPRINT
            if(::viewAttributeMap.isInitialized) {
                for (child in viewAttributeMap.keys) {
                    (child.parent as? ConstraintLayoutDesign)?.setBlueprint(isBlueprint)
                }
            }
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
    lateinit var initializer: AttributeInitializer
        private set

    private var isBlueprint = false
    private var structureView: StructureView? = null
    private var undoRedoManager: UndoRedoManager? = null

    // --- Interaction State Machine ---
    private var interactionState: InteractionState = InteractionState.IDLE
    private var activeDragInfo: ConstraintLayoutDesign.DraggingInfo? = null
    private var draggedView: View? = null
    private var dragStartX = 0f
    private var dragStartY = 0f
    private var initialViewX = 0f
    private var initialViewY = 0f
    private var downEventTime = 0L

    private enum class InteractionState { IDLE, PENDING, DRAGGING_HANDLE, DRAGGING_VIEW }
    private val gestureDetector: GestureDetector


    constructor(context: Context) : super(context) { gestureDetector = GestureDetector(context, GestureListener()); init(context) }
    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs) { gestureDetector = GestureDetector(context, GestureListener()); init(context) }
    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr) { gestureDetector = GestureDetector(context, GestureListener()); init(context) }

    private inner class GestureListener : GestureDetector.SimpleOnGestureListener() {
        override fun onLongPress(e: MotionEvent) {
            if (interactionState == InteractionState.PENDING) {
                if (activeDragInfo != null) {
                    interactionState = InteractionState.DRAGGING_HANDLE
                    (activeDragInfo!!.view.parent as? ConstraintLayoutDesign)?.setDraggingInfo(activeDragInfo)
                } else if (draggedView != null) {
                    interactionState = InteractionState.DRAGGING_VIEW
                }
                if(isEnableVibration) VibrateUtils.vibrate(50)
            }
        }
    }
    
    // --- Initialization and Setup ---
    private fun init(context: Context) {
        initAttributes()
        viewType = ViewType.DESIGN
        isBlueprint = false
        deviceConfiguration = DeviceConfiguration(DeviceSize.LARGE)
        shadow = View(context)
        paint = Paint()
        shadow.setBackgroundColor(MaterialColors.getColor(this, com.google.android.material.R.attr.colorOutline))
        shadow.layoutParams = ViewGroup.LayoutParams(Utils.pxToDp(context, 50), Utils.pxToDp(context, 35))
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
        when (deviceConfiguration?.size) {
            DeviceSize.SMALL -> { scaleX = 0.75f; scaleY = 0.75f }
            DeviceSize.MEDIUM -> { scaleX = 0.85f; scaleY = 0.85f }
            DeviceSize.LARGE, DeviceSize.SCREEN, DeviceSize.CUSTOM -> { scaleX = 1.0f; scaleY = 1.0f }
            null -> { scaleX = 1.0f; scaleY = 1.0f }
        }
    }

    private fun drawBlueprint(canvas: Canvas) { paint.color = Constants.BLUEPRINT_DASH_COLOR; setBackgroundColor(Constants.BLUEPRINT_BACKGROUND_COLOR) }
    private fun drawDesign(canvas: Canvas) { paint.color = Constants.DESIGN_DASH_COLOR; setBackgroundColor(Utils.getSurfaceColor(context)) }
    fun previewLayout(deviceConfiguration: DeviceConfiguration?, apiLevel: APILevel?) { this.deviceConfiguration = deviceConfiguration; this.apiLevel = apiLevel }
    fun resizeLayout(deviceConfiguration: DeviceConfiguration?) { this.deviceConfiguration = deviceConfiguration; invalidate() }

    private fun setTransition(group: ViewGroup) {
       
         if (group is RecyclerView || group is androidx.fragment.app.FragmentContainerView) return
        LayoutTransition().apply {
            disableTransitionType(LayoutTransition.CHANGE_DISAPPEARING)
            enableTransitionType(LayoutTransition.CHANGING)
            setDuration(150)
        }.also { group.layoutTransition = it }
    }

    private fun toggleStrokeWidgets() { if (!::viewAttributeMap.isInitialized) return; try { for (view in viewAttributeMap.keys) { view.javaClass.getMethod("setStrokeEnabled", Boolean::class.javaPrimitiveType).invoke(view, isShowStroke) } } catch (e: Exception) { /* Ignore */ } }
    private fun setBlueprintOnChildren() { if (!::viewAttributeMap.isInitialized) return; try { for (view in viewAttributeMap.keys) { (view.parent as? ConstraintLayoutDesign)?.setBlueprint(isBlueprint); view.javaClass.getMethod("setBlueprint", Boolean::class.javaPrimitiveType).invoke(view, isBlueprint) } } catch (e: Exception) { /* Ignore */ } }
    

    private fun setDragListener(group: ViewGroup) {
        group.setOnDragListener { host, event ->
            var parent = host as ViewGroup
            val draggedView = if (event.localState is View) event.localState as View else null
            when (event.action) {
                DragEvent.ACTION_DRAG_STARTED -> { if (isEnableVibration) VibrateUtils.vibrate(50); if (draggedView != null) parent.removeView(draggedView) }
                DragEvent.ACTION_DRAG_EXITED -> removeWidget(shadow)
                DragEvent.ACTION_DRAG_ENDED -> { if (!event.result && draggedView != null) { removeId(draggedView, draggedView is ViewGroup); removeViewAttributes(draggedView); viewAttributeMap.remove(draggedView); updateStructure() }; updateUndoRedoHistory(); checkForLayoutErrors() }
                DragEvent.ACTION_DRAG_LOCATION, DragEvent.ACTION_DRAG_ENTERED -> { if (shadow.parent == null) addWidget(shadow, parent, event) else if (parent is LinearLayout) { val index = parent.indexOfChild(shadow); val newIndex = getIndexForNewChildOfLinear(parent, event); if (index != newIndex) { parent.removeView(shadow); parent.addView(shadow, newIndex) } } else if (shadow.parent !== parent) { addWidget(shadow, parent, event) } }
                DragEvent.ACTION_DROP -> { removeWidget(shadow); if (childCount == 0) { parent = addRootConstraintLayout() } else { (getChildAt(0) as? ViewGroup)?.let { parent = it } }; if (draggedView == null) { handleNewViewDrop(event, parent) } else { handleExistingViewDrop(draggedView, parent, event) }; updateStructure() }
            }
            true
        }
    }
    
    private fun addRootConstraintLayout(): ViewGroup {
        val rootLayout = InvokeUtil.createView("android.zero.studio.uidesigner.editor.palette.layouts.ConstraintLayoutDesign", context) as ViewGroup
        rootLayout.layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
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
        return rootLayout
    }
    
    private fun handleNewViewDrop(event: DragEvent, parent: ViewGroup) {
        @Suppress("UNCHECKED_CAST") val data: HashMap<String, Any> = event.localState as HashMap<String, Any>
        val newView = InvokeUtil.createView(data[Constants.KEY_CLASS_NAME].toString(), context) as View
        newView.layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        rearrangeListeners(newView)
        if (newView is ViewGroup) { setDragListener(newView); setTransition(newView) }
        newView.minimumWidth = Utils.pxToDp(context, 20); newView.minimumHeight = Utils.pxToDp(context, 20)
        if (newView is EditText) newView.isFocusable = false
        val map = AttributeMap()
        val id = getIdForNewView(newView.javaClass.simpleName.replace("Design", "").replace(" ".toRegex(), "_").lowercase())
        IdManager.addNewId(newView, id)
        map.putValue("android:id", "@+id/$id")
        map.putValue("android:layout_width", "wrap_content")
        map.putValue("android:layout_height", "wrap_content")
        viewAttributeMap[newView] = map
        addWidget(newView, parent, event)
        if (parent is ConstraintLayout) {
            val xDp = (event.x / resources.displayMetrics.density).toInt(); val yDp = (event.y / resources.displayMetrics.density).toInt()
            val attributesToApply = mutableMapOf("app:layout_constraintStart_toStartOf" to "parent", "app:layout_constraintTop_toTopOf" to "parent", "android:layout_marginStart" to "${xDp}dp", "android:layout_marginTop" to "${yDp}dp")
            batchApplyAttributes(newView, attributesToApply)
        }
        try { newView.javaClass.getMethod("setStrokeEnabled", Boolean::class.javaPrimitiveType).invoke(newView, isShowStroke); newView.javaClass.getMethod("setBlueprint", Boolean::class.javaPrimitiveType).invoke(newView, isBlueprint) } catch (e: Exception) { /* Ignore */ }
        if (data.containsKey(Constants.KEY_DEFAULT_ATTRS)) { @Suppress("UNCHECKED_CAST") initializer.applyDefaultAttributes(newView, data[Constants.KEY_DEFAULT_ATTRS] as MutableMap<String, String>) }
    }
    
    private fun handleExistingViewDrop(draggedView: View, parent: ViewGroup, event: DragEvent) {
         if (parent is ConstraintLayout) {
            val xDp = (event.x / resources.displayMetrics.density).toInt(); val yDp = (event.y / resources.displayMetrics.density).toInt()
            val attrMap = viewAttributeMap[draggedView] ?: return
            val attrsToClear = listOf("android:layout_marginStart", "android:layout_marginTop", "android:layout_marginLeft", "app:layout_constraintTop_toTopOf", "app:layout_constraintStart_toStartOf")
            attrsToClear.forEach { attrMap.removeValue(it) }
            val attributesToApply = mapOf("app:layout_constraintStart_toStartOf" to "parent", "app:layout_constraintTop_toTopOf" to "parent", "android:layout_marginStart" to "${xDp}dp", "android:layout_marginTop" to "${yDp}dp")
            batchApplyAttributes(draggedView, attributesToApply)
        }
        addWidget(draggedView, parent, event)
    }

    private fun getIdForNewView(name: String): String { var id = name; var n = 0; var firstTime = true; while (IdManager.containsId(id)) { n++; id = if (firstTime) "$name$n" else id.replace(id.elementAt(id.lastIndex).toString().toRegex(), n.toString()); firstTime = false }; return id }
    fun loadLayoutFromParser(xml: String) { clearAll(); if (xml.isEmpty()) return; val parser = XmlLayoutParser(context); parser.parseFromXml(xml, context); addView(parser.root); viewAttributeMap = parser.viewAttributeMap; for (view in viewAttributeMap.keys) { rearrangeListeners(view); if (view is ViewGroup) { setDragListener(view); setTransition(view) }; view.minimumWidth = Utils.pxToDp(context, 20); view.minimumHeight = Utils.pxToDp(context, 20) }; updateStructure(); toggleStrokeWidgets(); setBlueprintOnChildren(); initializer = AttributeInitializer(context, viewAttributeMap, attributes, parentAttributes); checkForLayoutErrors() }
    fun undo() { if (undoRedoManager?.isUndoEnabled == true) loadLayoutFromParser(undoRedoManager!!.undo()) }
    fun redo() { if (undoRedoManager?.isRedoEnabled == true) loadLayoutFromParser(undoRedoManager!!.redo()) }
    private fun clearAll() { removeAllViews(); structureView?.clear(); if(::viewAttributeMap.isInitialized) viewAttributeMap.clear() }
    fun setStructureView(view: StructureView?) { structureView = view }
    fun bindUndoRedoManager(manager: UndoRedoManager?) { undoRedoManager = manager }
    private fun updateStructure() { if (childCount == 0) structureView?.clear() else getChildAt(0)?.let { structureView?.setView(it) } }
    fun updateUndoRedoHistory() { if (undoRedoManager == null) return; val result = XmlLayoutGenerator().generate(this, false); undoRedoManager!!.addToHistory(result) }

    // --- Touch and Interaction Handlers ---
    @SuppressLint("ClickableViewAccessibility")
    private fun rearrangeListeners(view: View) {
        view.setOnTouchListener { v, event ->
            gestureDetector.onTouchEvent(event)
            val parentLayout = v.parent as? ConstraintLayoutDesign ?: return@setOnTouchListener false
            val xInParent = v.left + event.x; val yInParent = v.top + event.y
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    interactionState = InteractionState.PENDING; activeDragInfo = null; draggedView = null
                    downEventTime = System.currentTimeMillis(); dragStartX = event.rawX; dragStartY = event.rawY
                    initialViewX = v.x; initialViewY = v.y
                    val handleTouchRadius = Utils.pxToDp(context, 24).toFloat()
                    val pressedHandleInfo = getPressedHandle(v, xInParent, yInParent, handleTouchRadius)
                    if (pressedHandleInfo != null) { activeDragInfo = pressedHandleInfo } else { draggedView = v }
                    v.parent.requestDisallowInterceptTouchEvent(true)
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    if (interactionState == InteractionState.DRAGGING_HANDLE && activeDragInfo != null) {
                        activeDragInfo!!.currentX = xInParent; activeDragInfo!!.currentY = yInParent; checkForAlignment(activeDragInfo!!.view, parentLayout)
                    } else if (interactionState == InteractionState.DRAGGING_VIEW && draggedView != null) {
                        val dx = event.rawX - dragStartX; val dy = event.rawY - dragStartY
                        draggedView!!.x = initialViewX + dx; draggedView!!.y = initialViewY + dy; checkForAlignment(draggedView!!, parentLayout)
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    parentLayout.clearAlignmentLines(); v.parent.requestDisallowInterceptTouchEvent(false)
                    val wasDragging = interactionState == InteractionState.DRAGGING_HANDLE || interactionState == InteractionState.DRAGGING_VIEW
                    if (wasDragging) {
                         if (activeDragInfo != null) {
                            if (isResizeHandle(activeDragInfo!!.handle)) { handleResizeDrop(activeDragInfo!!) } else { handleConstraintDrop(activeDragInfo!!, parentLayout) }
                        } else if (draggedView != null) { handleViewDrop(draggedView!!) }
                    } else {
                        val isClick = System.currentTimeMillis() - downEventTime < 200 && distance(event.rawX, event.rawY, dragStartX, dragStartY) < Utils.pxToDp(context, 10)
                        if (isClick) { handleViewClick(v) }
                    }
                    interactionState = InteractionState.IDLE; activeDragInfo = null; draggedView = null; parentLayout.clearDraggingInfo(); checkForLayoutErrors(); updateUndoRedoHistory()
                    true
                }
                else -> false
            }
        }
    }
    
    private fun handleViewClick(view: View) {
showDefinedAttributes(view)
    }
    
    private fun showChainStylePopupMenu(view: View) { val popup = PopupMenu(context, view); if (isViewInHorizontalChain(view)) popup.menu.add("Set Horizontal Chain Style"); if (isViewInVerticalChain(view)) popup.menu.add("Set Vertical Chain Style"); popup.setOnMenuItemClickListener { menuItem -> when (menuItem.title) { "Set Horizontal Chain Style" -> showChainStyleDialog(view, true); "Set Vertical Chain Style" -> showChainStyleDialog(view, false) }; true }; popup.show() }
    private fun checkForAlignment(draggedView: View, parent: ConstraintLayoutDesign) { val alignmentLines = mutableListOf<ConstraintLayoutDesign.AlignmentLine>(); val snapThreshold = Utils.pxToDp(context, 2).toFloat(); val draggedRect = Rect(draggedView.left, draggedView.top, draggedView.right, draggedView.bottom); parent.children.filter { it != draggedView }.forEach { targetView -> val targetRect = Rect(targetView.left, targetView.top, targetView.right, targetView.bottom); if (abs(draggedRect.left - targetRect.left) < snapThreshold) alignmentLines.add(ConstraintLayoutDesign.AlignmentLine(targetRect.left.toFloat(), draggedRect.top.toFloat(), targetRect.left.toFloat(), targetRect.bottom.toFloat())); if (abs(draggedRect.left - targetRect.right) < snapThreshold) alignmentLines.add(ConstraintLayoutDesign.AlignmentLine(targetRect.right.toFloat(), draggedRect.top.toFloat(), targetRect.right.toFloat(), targetRect.bottom.toFloat())); if (abs(draggedRect.right - targetRect.left) < snapThreshold) alignmentLines.add(ConstraintLayoutDesign.AlignmentLine(targetRect.left.toFloat(), draggedRect.top.toFloat(), targetRect.left.toFloat(), targetRect.bottom.toFloat())); if (abs(draggedRect.right - targetRect.right) < snapThreshold) alignmentLines.add(ConstraintLayoutDesign.AlignmentLine(targetRect.right.toFloat(), draggedRect.top.toFloat(), targetRect.right.toFloat(), targetRect.bottom.toFloat())); if (abs(draggedRect.top - targetRect.top) < snapThreshold) alignmentLines.add(ConstraintLayoutDesign.AlignmentLine(draggedRect.left.toFloat(), targetRect.top.toFloat(), targetRect.right.toFloat(), targetRect.top.toFloat())); if (abs(draggedRect.top - targetRect.bottom) < snapThreshold) alignmentLines.add(ConstraintLayoutDesign.AlignmentLine(draggedRect.left.toFloat(), targetRect.bottom.toFloat(), targetRect.right.toFloat(), targetRect.bottom.toFloat())); if (abs(draggedRect.bottom - targetRect.top) < snapThreshold) alignmentLines.add(ConstraintLayoutDesign.AlignmentLine(draggedRect.left.toFloat(), targetRect.top.toFloat(), targetRect.right.toFloat(), targetRect.top.toFloat())); if (abs(draggedRect.bottom - targetRect.bottom) < snapThreshold) alignmentLines.add(ConstraintLayoutDesign.AlignmentLine(draggedRect.left.toFloat(), targetRect.bottom.toFloat(), targetRect.right.toFloat(), targetRect.bottom.toFloat())); if (abs(draggedRect.centerX() - targetRect.centerX()) < snapThreshold) alignmentLines.add(ConstraintLayoutDesign.AlignmentLine(targetRect.centerX().toFloat(), draggedRect.top.toFloat(), targetRect.centerX().toFloat(), targetRect.bottom.toFloat())); if (abs(draggedRect.centerY() - targetRect.centerY()) < snapThreshold) alignmentLines.add(ConstraintLayoutDesign.AlignmentLine(draggedRect.left.toFloat(), targetRect.centerY().toFloat(), targetRect.right.toFloat(), targetRect.centerY().toFloat())) }; parent.setAlignmentLines(alignmentLines) }
    private fun handleViewDrop(view: View) { val density = context.resources.displayMetrics.density; val changes = mutableMapOf<String, String>(); if (!isHorizontallyConstrained(view)) { changes["app:layout_constraintStart_toStartOf"] = "parent" }; changes["android:layout_marginStart"] = "${(view.x / density).toInt()}dp"; if (!isVerticallyConstrained(view)) { changes["app:layout_constraintTop_toTopOf"] = "parent" }; changes["android:layout_marginTop"] = "${(view.y / density).toInt()}dp"; if (changes.isNotEmpty()) { batchApplyAttributes(view, changes) } }
    

    private fun batchApplyAttributes(view: View, attributes: Map<String, String>) {
        val parent = view.parent as? ConstraintLayout ?: return
        val updater = ConstraintUpdater(parent)
        val viewAttrMap = viewAttributeMap[view]!!

        for ((key, value) in attributes) {
            viewAttrMap.putValue(key, value)
        }

        val lp = view.layoutParams as ConstraintLayout.LayoutParams
        for (key in viewAttrMap.keySet()) {
            val value = viewAttrMap.getValueOrNull(key) ?: continue
            applyAttributeToUpdater(updater, view, lp, key, value)
        }
        view.layoutParams = lp
        updater.apply()
    }
    
    private fun applyAttributeToUpdater(updater: ConstraintUpdater, view: View, lp: ConstraintLayout.LayoutParams, key: String, value: String) {
        val viewId = view.id
        when (key) {
            "android:layout_width" -> lp.width = DimensionUtil.parse(value, context).toInt()
            "android:layout_height" -> lp.height = DimensionUtil.parse(value, context).toInt()
            "app:layout_constraintDimensionRatio" -> updater.setDimensionRatio(viewId, value)
            
            "app:layout_constraintStart_toStartOf" -> updater.connect(viewId, ConstraintSet.START, IdManager.getViewId(value) ?: ConstraintSet.PARENT_ID, ConstraintSet.START)
            "app:layout_constraintStart_toEndOf" -> updater.connect(viewId, ConstraintSet.START, IdManager.getViewId(value) ?: ConstraintSet.PARENT_ID, ConstraintSet.END)
            "app:layout_constraintEnd_toStartOf" -> updater.connect(viewId, ConstraintSet.END, IdManager.getViewId(value) ?: ConstraintSet.PARENT_ID, ConstraintSet.START)
            "app:layout_constraintEnd_toEndOf" -> updater.connect(viewId, ConstraintSet.END, IdManager.getViewId(value) ?: ConstraintSet.PARENT_ID, ConstraintSet.END)
            "app:layout_constraintTop_toTopOf" -> updater.connect(viewId, ConstraintSet.TOP, IdManager.getViewId(value) ?: ConstraintSet.PARENT_ID, ConstraintSet.TOP)
            "app:layout_constraintTop_toBottomOf" -> updater.connect(viewId, ConstraintSet.TOP, IdManager.getViewId(value) ?: ConstraintSet.PARENT_ID, ConstraintSet.BOTTOM)
            "app:layout_constraintBottom_toTopOf" -> updater.connect(viewId, ConstraintSet.BOTTOM, IdManager.getViewId(value) ?: ConstraintSet.PARENT_ID, ConstraintSet.TOP)
            "app:layout_constraintBottom_toBottomOf" -> updater.connect(viewId, ConstraintSet.BOTTOM, IdManager.getViewId(value) ?: ConstraintSet.PARENT_ID, ConstraintSet.BOTTOM)
            "app:layout_constraintBaseline_toBaselineOf" -> updater.connect(viewId, ConstraintSet.BASELINE, IdManager.getViewId(value) ?: ConstraintSet.PARENT_ID, ConstraintSet.BASELINE)
            
            "android:layout_marginStart" -> updater.setMargin(viewId, ConstraintSet.START, value)
            "android:layout_marginEnd" -> updater.setMargin(viewId, ConstraintSet.END, value)
            "android:layout_marginTop" -> updater.setMargin(viewId, ConstraintSet.TOP, value)
            "android:layout_marginBottom" -> updater.setMargin(viewId, ConstraintSet.BOTTOM, value)
            
            "app:layout_constraintHorizontal_bias" -> updater.setHorizontalBias(viewId, value.toFloatOrNull() ?: 0.5f)
            "app:layout_constraintVertical_bias" -> updater.setVerticalBias(viewId, value.toFloatOrNull() ?: 0.5f)

            "app:layout_constraintHorizontal_chainStyle" -> {
                val style = when(value) { "spread_inside" -> ConstraintSet.CHAIN_SPREAD_INSIDE; "packed" -> ConstraintSet.CHAIN_PACKED; else -> ConstraintSet.CHAIN_SPREAD }
                updater.setHorizontalChainStyle(viewId, style)
            }
            "app:layout_constraintVertical_chainStyle" -> {
                val style = when(value) { "spread_inside" -> ConstraintSet.CHAIN_SPREAD_INSIDE; "packed" -> ConstraintSet.CHAIN_PACKED; else -> ConstraintSet.CHAIN_SPREAD }
                updater.setVerticalChainStyle(viewId, style)
            }

            else -> {
                val allAttrs = initializer.getAllAttributesForView(view)
                val attr = initializer.getAttributeFromKey(key, allAttrs) ?: return
                initializer.applyAttribute(view, value, attr)
            }
        }
    }
    

    private fun getPressedHandle(view: View, touchX: Float, touchY: Float, radius: Float): ConstraintLayoutDesign.DraggingInfo? { for (handle in ConstraintLayoutDesign.Handle.values()) { if (handle == ConstraintLayoutDesign.Handle.NONE) continue; val handlePos = getHandlePosition(view, handle); if (handlePos != null && distance(touchX, touchY, handlePos.first, handlePos.second) < radius) { return ConstraintLayoutDesign.DraggingInfo(view, handle, handlePos.first, handlePos.second) } }; return null }
    private fun getHandlePosition(view: View, handle: ConstraintLayoutDesign.Handle): Pair<Float, Float>? { val x = view.left.toFloat(); val y = view.top.toFloat(); val w = view.width.toFloat(); val h = view.height.toFloat(); return when (handle) { ConstraintLayoutDesign.Handle.LEFT -> Pair(x, y + h / 2); ConstraintLayoutDesign.Handle.RIGHT -> Pair(x + w, y + h / 2); ConstraintLayoutDesign.Handle.TOP -> Pair(x + w / 2, y); ConstraintLayoutDesign.Handle.BOTTOM -> Pair(x + w / 2, y + h); ConstraintLayoutDesign.Handle.TOP_LEFT -> Pair(x, y); ConstraintLayoutDesign.Handle.TOP_RIGHT -> Pair(x + w, y); ConstraintLayoutDesign.Handle.BOTTOM_LEFT -> Pair(x, y + h); ConstraintLayoutDesign.Handle.BOTTOM_RIGHT -> Pair(x + w, y + h); ConstraintLayoutDesign.Handle.BASELINE -> if(view is TextView && view.lineCount > 0 && view.baseline > 0) Pair(x + w / 2, y + view.baseline) else null; else -> null } }
    private fun distance(x1: Float, y1: Float, x2: Float, y2: Float): Float = sqrt((x1 - x2).pow(2) + (y1 - y2).pow(2))
    private fun handleConstraintDrop(dragInfo: ConstraintLayoutDesign.DraggingInfo, parent: ConstraintLayout) { val view = dragInfo.view; val snapThreshold = Utils.pxToDp(context, 24).toFloat(); var bestTargetInfo: Quad<String, String, Float, Int>? = null; var bestDistance = Float.MAX_VALUE; val allTargets = parent.children.filter { it != view }.toMutableList<View?>(); allTargets.add(0, null); for (targetView in allTargets) { val connections = getPossibleConnections(dragInfo, targetView, parent); for (connection in connections) { if (connection.third < snapThreshold && connection.third < bestDistance) { bestDistance = connection.third; bestTargetInfo = connection } } }; val viewRect = Rect(); view.getHitRect(viewRect); if (bestTargetInfo == null && viewRect.contains((dragInfo.currentX - view.left).toInt(), (dragInfo.currentY - view.top).toInt())) { removeConstraint(view, dragInfo.handle); return }; if (bestTargetInfo != null) { val (attrKey, targetName, _, margin) = bestTargetInfo; val changes = mutableMapOf<String, String>(); clearRelevantConstraints(view, dragInfo.handle); changes[attrKey] = targetName; getMarginKeyForHandle(dragInfo.handle)?.let { changes[it] = "${margin}dp" }; batchApplyAttributes(view, changes) } }
    private fun getPossibleConnections(dragInfo: ConstraintLayoutDesign.DraggingInfo, targetView: View?, parent: ConstraintLayout): List<Quad<String, String, Float, Int>> { val density = context.resources.displayMetrics.density; val list = mutableListOf<Quad<String, String, Float, Int>>(); val currentX = dragInfo.currentX; val currentY = dragInfo.currentY; val targetName = if (targetView == null) "parent" else { IdManager.idMap[targetView]?.let { "@id/$it" } ?: "parent" }; when(dragInfo.handle) { ConstraintLayoutDesign.Handle.TOP -> { val targetTop = targetView?.top?.toFloat() ?: 0f; val targetBottom = targetView?.bottom?.toFloat() ?: 0f; list.add(Quad("app:layout_constraintTop_toBottomOf", targetName, abs(currentY - targetBottom), ((currentY - targetBottom) / density).toInt())); list.add(Quad("app:layout_constraintTop_toTopOf", targetName, abs(currentY - targetTop), ((currentY - targetTop) / density).toInt())) }; ConstraintLayoutDesign.Handle.BOTTOM -> { val targetTop = targetView?.top?.toFloat() ?: 0f; val targetBottom = targetView?.bottom?.toFloat() ?: parent.height.toFloat(); list.add(Quad("app:layout_constraintBottom_toTopOf", targetName, abs(currentY - targetTop), ((targetTop - currentY) / density).toInt())); list.add(Quad("app:layout_constraintBottom_toBottomOf", targetName, abs(currentY - targetBottom), ((targetBottom - currentY) / density).toInt())) }; ConstraintLayoutDesign.Handle.LEFT -> { val targetLeft = targetView?.left?.toFloat() ?: 0f; val targetRight = targetView?.right?.toFloat() ?: 0f; list.add(Quad("app:layout_constraintStart_toEndOf", targetName, abs(currentX - targetRight), ((currentX - targetRight) / density).toInt())); list.add(Quad("app:layout_constraintStart_toStartOf", targetName, abs(currentX - targetLeft), ((currentX - targetLeft) / density).toInt())) }; ConstraintLayoutDesign.Handle.RIGHT -> { val targetLeft = targetView?.left?.toFloat() ?: 0f; val targetRight = targetView?.right?.toFloat() ?: parent.width.toFloat(); list.add(Quad("app:layout_constraintEnd_toStartOf", targetName, abs(currentX - targetLeft), ((targetLeft - currentX) / density).toInt())); list.add(Quad("app:layout_constraintEnd_toEndOf", targetName, abs(currentX - targetRight), ((targetRight - currentX) / density).toInt())) }; ConstraintLayoutDesign.Handle.BASELINE -> { if (targetView is TextView && targetView.lineCount > 0 && targetView.baseline > 0) { val targetBaselineY = targetView.y + targetView.baseline; list.add(Quad("app:layout_constraintBaseline_toBaselineOf", targetName, abs(currentY - targetBaselineY), 0)) } }; else -> {} }; return list }
    data class Quad<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)
    private fun handleResizeDrop(dragInfo: ConstraintLayoutDesign.DraggingInfo) { val view = dragInfo.view; val density = context.resources.displayMetrics.density; var newWidthPx = view.width.toFloat(); var newHeightPx = view.height.toFloat(); var newX = view.x; var newY = view.y; when(dragInfo.handle) { ConstraintLayoutDesign.Handle.TOP_LEFT -> { newWidthPx = view.right - dragInfo.currentX; newHeightPx = view.bottom - dragInfo.currentY; newX = dragInfo.currentX; newY = dragInfo.currentY }; ConstraintLayoutDesign.Handle.TOP_RIGHT -> { newWidthPx = dragInfo.currentX - view.left; newHeightPx = view.bottom - dragInfo.currentY; newY = dragInfo.currentY }; ConstraintLayoutDesign.Handle.BOTTOM_LEFT -> { newWidthPx = view.right - dragInfo.currentX; newHeightPx = dragInfo.currentY - view.top; newX = dragInfo.currentX }; ConstraintLayoutDesign.Handle.BOTTOM_RIGHT -> { newWidthPx = dragInfo.currentX - view.left; newHeightPx = dragInfo.currentY - view.top }; else -> return }; val changes = mutableMapOf<String, String>(); changes["android:layout_width"] = "${(newWidthPx / density).toInt().coerceAtLeast(24)}dp"; changes["android:layout_height"] = "${(newHeightPx / density).toInt().coerceAtLeast(24)}dp"; if (newX != view.x) changes["android:layout_marginStart"] = "${(newX / density).toInt()}dp"; if (newY != view.y) changes["android:layout_marginTop"] = "${(newY / density).toInt()}dp"; batchApplyAttributes(view, changes) }
    private fun clearRelevantConstraints(view: View, handle: ConstraintLayoutDesign.Handle) { val attrMap = viewAttributeMap[view] ?: return; getConstraintKeysForHandle(handle).forEach { attrMap.removeValue(it) } }
    private fun isHorizontallyConstrained(view: View): Boolean { val p = view.layoutParams as? ConstraintLayout.LayoutParams ?: return false; return p.startToStart!=-1||p.startToEnd!=-1||p.endToStart!=-1||p.endToEnd!=-1||p.leftToLeft!=-1||p.leftToRight!=-1||p.rightToLeft!=-1||p.rightToRight!=-1 }
    private fun isVerticallyConstrained(view: View): Boolean { val p = view.layoutParams as? ConstraintLayout.LayoutParams ?: return false; return p.topToTop!=-1||p.topToBottom!=-1||p.bottomToTop!=-1||p.bottomToBottom!=-1 }
    private fun isResizeHandle(handle: ConstraintLayoutDesign.Handle): Boolean = handle in listOf(ConstraintLayoutDesign.Handle.TOP_LEFT, ConstraintLayoutDesign.Handle.TOP_RIGHT, ConstraintLayoutDesign.Handle.BOTTOM_LEFT, ConstraintLayoutDesign.Handle.BOTTOM_RIGHT)
    private fun isHandleConnected(view: View, handle: ConstraintLayoutDesign.Handle): Boolean { val p = view.layoutParams as? ConstraintLayout.LayoutParams ?: return false; return when(handle) { ConstraintLayoutDesign.Handle.TOP -> p.topToTop!=-1||p.topToBottom!=-1; ConstraintLayoutDesign.Handle.BOTTOM -> p.bottomToTop!=-1||p.bottomToBottom!=-1; ConstraintLayoutDesign.Handle.LEFT -> p.startToStart!=-1||p.startToEnd!=-1||p.leftToLeft!=-1||p.leftToRight!=-1; ConstraintLayoutDesign.Handle.RIGHT -> p.endToStart!=-1||p.endToEnd!=-1||p.rightToLeft!=-1||p.rightToRight!=-1; ConstraintLayoutDesign.Handle.BASELINE -> p.baselineToBaseline!=-1; else -> false } }
    private fun isViewInChain(view: View): Boolean = isViewInHorizontalChain(view) || isViewInVerticalChain(view)
    private fun isViewInHorizontalChain(view: View): Boolean { val p = view.layoutParams as? ConstraintLayout.LayoutParams ?: return false; val startConnected = p.startToStart != -1 || p.startToEnd != -1 || p.leftToLeft != -1 || p.leftToRight != -1; val endConnected = p.endToStart != -1 || p.endToEnd != -1 || p.rightToLeft != -1 || p.rightToRight != -1; return startConnected && endConnected }
    private fun isViewInVerticalChain(view: View): Boolean { val p = view.layoutParams as? ConstraintLayout.LayoutParams ?: return false; val topConnected = p.topToTop != -1 || p.topToBottom != -1; val bottomConnected = p.bottomToTop != -1 || p.bottomToBottom != -1; return topConnected && bottomConnected }
    private fun showChainStyleDialog(view: View, isHorizontal: Boolean) { val items = arrayOf("Spread", "Spread Inside", "Packed"); val keys = arrayOf("spread", "spread_inside", "packed"); MaterialAlertDialogBuilder(context).setTitle("Set ${if (isHorizontal) "Horizontal" else "Vertical"} Chain Style").setItems(items) { d, w -> val style = keys[w]; val attrKey = if (isHorizontal) "app:layout_constraintHorizontal_chainStyle" else "app:layout_constraintVertical_chainStyle"; batchApplyAttributes(view, mapOf(attrKey to style)); d.dismiss() }.show() }
    private fun getConstraintKeysForHandle(handle: ConstraintLayoutDesign.Handle): List<String> = when (handle) { ConstraintLayoutDesign.Handle.TOP -> listOf("app:layout_constraintTop_toTopOf", "app:layout_constraintTop_toBottomOf", "android:layout_marginTop"); ConstraintLayoutDesign.Handle.BOTTOM -> listOf("app:layout_constraintBottom_toBottomOf", "app:layout_constraintBottom_toTopOf", "android:layout_marginBottom"); ConstraintLayoutDesign.Handle.LEFT -> listOf("app:layout_constraintStart_toStartOf", "app:layout_constraintStart_toEndOf", "android:layout_marginStart", "app:layout_constraintLeft_toLeftOf", "app:layout_constraintLeft_toRightOf", "android:layout_marginLeft"); ConstraintLayoutDesign.Handle.RIGHT -> listOf("app:layout_constraintEnd_toEndOf", "app:layout_constraintEnd_toStartOf", "android:layout_marginEnd", "app:layout_constraintRight_toRightOf", "app:layout_constraintRight_toLeftOf", "android:layout_marginRight"); ConstraintLayoutDesign.Handle.BASELINE -> listOf("app:layout_constraintBaseline_toBaselineOf"); else -> emptyList() }
    private fun getMarginKeyForHandle(handle: ConstraintLayoutDesign.Handle): String? = when (handle) { ConstraintLayoutDesign.Handle.TOP -> "android:layout_marginTop"; ConstraintLayoutDesign.Handle.BOTTOM -> "android:layout_marginBottom"; ConstraintLayoutDesign.Handle.LEFT -> "android:layout_marginStart"; ConstraintLayoutDesign.Handle.RIGHT -> "android:layout_marginEnd"; else -> null }
    private fun removeConstraint(view: View, handle: ConstraintLayoutDesign.Handle) { val attrMap = viewAttributeMap[view] ?: return; val keysToRemove = getConstraintKeysForHandle(handle); for (key in keysToRemove) { attrMap.removeValue(key) }; val changes = mutableMapOf<String, String>(); attrMap.keySet().forEach { key -> attrMap.getValueOrNull(key)?.let { value -> changes[key] = value } }; batchApplyAttributes(view, changes); Toast.makeText(context, "Constraint removed", Toast.LENGTH_SHORT).show() }
    private fun checkForLayoutErrors() { val errorViews = mutableListOf<View>(); val parentLayout = getChildAt(0) as? ConstraintLayoutDesign ?: return; for (view in viewAttributeMap.keys) { if (view.parent != parentLayout || view is Guideline) continue; if (!isHorizontallyConstrained(view) || !isVerticallyConstrained(view)) { errorViews.add(view) } }; parentLayout.setErrorViews(errorViews) }
    private fun addWidget(view: View, newParent: ViewGroup, event: DragEvent) { removeWidget(view); if (newParent is LinearLayout) { val index = getIndexForNewChildOfLinear(newParent, event); newParent.addView(view, index) } else { try { newParent.addView(view, newParent.childCount) } catch (e: Exception) { e.printStackTrace() } } }
    private fun removeWidget(view: View) { (view.parent as ViewGroup?)?.removeView(view) }
    private fun getIndexForNewChildOfLinear(layout: LinearLayout, event: DragEvent): Int { val orientation = layout.orientation; if (orientation == HORIZONTAL) { var index = 0; for (i in 0 until layout.childCount) { val child = layout.getChildAt(i); if (child === shadow) continue; if (child.right < event.x) index++ }; return index }; if (orientation == VERTICAL) { var index = 0; for (i in 0 until layout.childCount) { val child = layout.getChildAt(i); if (child === shadow) continue; if (child.bottom < event.y) index++ }; return index }; return -1 }
    fun showDefinedAttributes(target: View) { val attributeMap = viewAttributeMap[target] ?: return; val keys = attributeMap.keySet(); val values = attributeMap.values(); val attrs: MutableList<HashMap<String, Any>> = ArrayList(); val allAttrs = initializer.getAllAttributesForView(target); val dialog = BottomSheetDialog(context); val binding = ShowAttributesDialogBinding.inflate(dialog.layoutInflater); dialog.setContentView(binding.root); TooltipCompat.setTooltipText(binding.btnAdd, "Add attribute"); TooltipCompat.setTooltipText(binding.btnDelete, "Delete"); for (key: String in keys) { val attributeDefinition = initializer.getAttributeFromKey(key, allAttrs); if (attributeDefinition != null) { attrs.add(attributeDefinition) } }; if (attrs.size != values.size) { return }; val appliedAttributesAdapter = AppliedAttributesAdapter(attrs, values); appliedAttributesAdapter.onClick = { position -> if (position < keys.size) { showAttributeEdit(target, keys[position]); dialog.dismiss() } }; appliedAttributesAdapter.onRemoveButtonClick = { position -> dialog.dismiss(); if (position < keys.size) { val view = removeAttribute(target, keys[position]); showDefinedAttributes(view) } }; binding.attributesList.adapter = appliedAttributesAdapter; binding.attributesList.layoutManager = LinearLayoutManager(context, RecyclerView.VERTICAL, false); binding.viewName.text = target.javaClass.superclass?.simpleName ?: target.javaClass.simpleName; binding.viewFullName.text = target.javaClass.superclass?.name ?: target.javaClass.name; binding.btnAdd.setOnClickListener { showAvailableAttributes(target); dialog.dismiss() }; binding.btnDelete.setOnClickListener { MaterialAlertDialogBuilder(context).setTitle(R.string.delete_view).setMessage(R.string.msg_delete_view).setNegativeButton(R.string.no) { d, _ -> d.dismiss() }.setPositiveButton(R.string.yes) { _, _ -> removeId(target, target is ViewGroup); removeViewAttributes(target); removeWidget(target); updateStructure(); updateUndoRedoHistory(); dialog.dismiss() }.show() }; dialog.show() }
    private fun showAvailableAttributes(target: View) { val availableAttrs = initializer.getAvailableAttributesForView(target); val names: MutableList<String> = ArrayList(); for (attr: HashMap<String, Any> in availableAttrs) { names.add(attr["name"].toString()) }; MaterialAlertDialogBuilder(context).setTitle("Available attributes").setAdapter(ArrayAdapter(context, android.R.layout.simple_list_item_1, names)) { _, w -> showAttributeEdit(target, availableAttrs[w][Constants.KEY_ATTRIBUTE_NAME].toString()) }.show() }
    private fun showAttributeEdit(target: View, attributeKey: String) { val allAttrs = initializer.getAllAttributesForView(target); val currentAttr = initializer.getAttributeFromKey(attributeKey, allAttrs); val attributeMap = viewAttributeMap[target]; val argumentTypes = currentAttr?.get(Constants.KEY_ARGUMENT_TYPE)?.toString()?.split("\\|".toRegex())?.dropLastWhile { it.isEmpty() }?.toTypedArray(); if (argumentTypes != null) { if (argumentTypes.size > 1) { if (attributeMap?.contains(attributeKey) == true) { val argumentType = parseType(attributeMap.getValueOrNull(attributeKey) ?: "", argumentTypes); showAttributeEdit(target, attributeKey, argumentType); return }; MaterialAlertDialogBuilder(context).setTitle(R.string.select_arg_type).setAdapter(ArrayAdapter(context, android.R.layout.simple_list_item_1, argumentTypes)) { _, w -> showAttributeEdit(target, attributeKey, argumentTypes[w]) }.show(); return } }; showAttributeEdit(target, attributeKey, argumentTypes?.get(0)) }
    @Suppress("UNCHECKED_CAST") private fun showAttributeEdit(target: View, attributeKey: String, argumentType: String?) { val allAttrs = initializer.getAllAttributesForView(target); val currentAttr = initializer.getAttributeFromKey(attributeKey, allAttrs); val attributeMap = viewAttributeMap[target]; var savedValue = attributeMap?.getValueOrNull(attributeKey) ?: ""; val defaultValue = if (currentAttr?.containsKey(Constants.KEY_DEFAULT_VALUE) == true) currentAttr[Constants.KEY_DEFAULT_VALUE].toString() else null; val constant = if (currentAttr?.containsKey(Constants.KEY_CONSTANT) == true) currentAttr[Constants.KEY_CONSTANT].toString() else null; val context = context; var dialog: AttributeDialog? = null; when (argumentType) { Constants.ARGUMENT_TYPE_SIZE -> dialog = SizeDialog(context, savedValue); Constants.ARGUMENT_TYPE_DIMENSION -> dialog = DimensionDialog(context, savedValue, currentAttr?.get("dimensionUnit")?.toString()); Constants.ARGUMENT_TYPE_ID -> dialog = IdDialog(context, savedValue); Constants.ARGUMENT_TYPE_VIEW -> dialog = ViewDialog(context, savedValue, constant); Constants.ARGUMENT_TYPE_BOOLEAN -> dialog = BooleanDialog(context, savedValue); Constants.ARGUMENT_TYPE_DRAWABLE -> { if (savedValue.startsWith("@drawable/")) { savedValue = savedValue.replace("@drawable/", "") }; dialog = StringDialog(context, savedValue, Constants.ARGUMENT_TYPE_DRAWABLE) }; Constants.ARGUMENT_TYPE_STRING -> { if (savedValue.startsWith("@string/")) { savedValue = savedValue.replace("@string/", "") }; dialog = StringDialog(context, savedValue, Constants.ARGUMENT_TYPE_STRING) }; Constants.ARGUMENT_TYPE_TEXT -> dialog = StringDialog(context, savedValue, Constants.ARGUMENT_TYPE_TEXT); Constants.ARGUMENT_TYPE_INT -> dialog = NumberDialog(context, savedValue, Constants.ARGUMENT_TYPE_INT); Constants.ARGUMENT_TYPE_FLOAT -> dialog = NumberDialog(context, savedValue, Constants.ARGUMENT_TYPE_FLOAT); Constants.ARGUMENT_TYPE_FLAG -> dialog = FlagDialog(context, savedValue, currentAttr?.get("arguments") as ArrayList<String>?); Constants.ARGUMENT_TYPE_ENUM -> dialog = EnumDialog(context, savedValue, currentAttr?.get("arguments") as ArrayList<String>?); Constants.ARGUMENT_TYPE_COLOR -> dialog = ColorDialog(context, savedValue) }; if (dialog == null) return; dialog.setTitle(currentAttr?.get("name")?.toString()); dialog.setOnSaveValueListener { if (it != null) { if (defaultValue != null && (defaultValue == it)) { if (attributeMap?.contains(attributeKey) == true) removeAttribute(target, attributeKey) } else { if (currentAttr != null) { batchApplyAttributes(target, mapOf(attributeKey to it)) }; showDefinedAttributes(target); updateUndoRedoHistory(); updateStructure() } } }; dialog.show() }
    private fun removeViewAttributes(view: View) { viewAttributeMap.remove(view); if (view is ViewGroup) { for (i in 0 until view.childCount) { removeViewAttributes(view.getChildAt(i)) } } }
    private fun removeAttribute(target: View, attributeKey: String): View { val attributeMap = viewAttributeMap[target] ?: return target; val allAttrs = initializer.getAllAttributesForView(target); val currentAttr = initializer.getAttributeFromKey(attributeKey, allAttrs); val cantDelete = currentAttr?.get(Constants.KEY_CAN_DELETE) as? String == "false"; if (cantDelete) { Toast.makeText(context, "This attribute cannot be deleted.", Toast.LENGTH_SHORT).show(); return target }; val oldIdName = if (attributeKey == "android:id") attributeMap.getValueOrNull(attributeKey) else null; attributeMap.removeValue(attributeKey); if (oldIdName != null) { removeId(target, false); viewAttributeMap.keys.forEach { v -> if (v == target) return@forEach; val map = viewAttributeMap[v]; map?.let { nonNullMap -> val keysToRemove = mutableListOf<String>(); nonNullMap.keySet().forEach { key -> if (nonNullMap.getValueOrNull(key)?.replace("@+id/", "@id/") == oldIdName.replace("@+id/", "@id/")) { keysToRemove.add(key) } }; if (keysToRemove.isNotEmpty()) { keysToRemove.forEach { nonNullMap.removeValue(it) }; val changes = mutableMapOf<String, String>(); nonNullMap.keySet().forEach{k -> nonNullMap.getValueOrNull(k)?.let { v -> changes[k] = v }}; if(changes.isNotEmpty()) batchApplyAttributes(v, changes) } } } }; val changes = mutableMapOf<String, String>(); attributeMap.keySet().forEach { k -> attributeMap.getValueOrNull(k)?.let { v -> changes[k] = v } }; batchApplyAttributes(target, changes); updateStructure(); updateUndoRedoHistory(); return target }
    private fun initAttributes() { attributes = convertJsonToJavaObject(Constants.ATTRIBUTES_FILE); parentAttributes = convertJsonToJavaObject(Constants.PARENT_ATTRIBUTES_FILE); viewAttributeMap = HashMap(); initializer = AttributeInitializer(context, viewAttributeMap, attributes, parentAttributes) }
    private fun convertJsonToJavaObject(filePath: String): HashMap<String, List<HashMap<String, Any>>> = Gson().fromJson(FileUtil.readFromAsset(filePath, context), object : TypeToken<HashMap<String?, ArrayList<HashMap<String?, Any?>?>?>?>() {}.type)
    enum class ViewType { DESIGN, BLUEPRINT }
}