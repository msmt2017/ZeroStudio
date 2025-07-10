// 请用此代码完整替换你现有的 AttributeInitializer.kt 文件
package android.zero.studio.uidesigner.editor.initializer

import android.content.Context
import android.view.View
import android.zero.studio.uidesigner.editor.DesignEditor
import android.zero.studio.uidesigner.utils.Constants
import android.zero.studio.uidesigner.utils.InvokeUtil

class AttributeInitializer(
    private val context: Context,
    private val viewAttributeMap: HashMap<View, AttributeMap>,
    private val attributes: HashMap<String, List<HashMap<String, Any>>>,
    private val parentAttributes: HashMap<String, List<HashMap<String, Any>>>
) {

    /**
     * Applies a map of default attributes to a given view.
     * This is typically used when a new widget is dropped onto the canvas.
     */
    fun applyDefaultAttributes(target: View, defaultAttrs: Map<String, String?>) {
        val allAttrs = getAllAttributesForView(target)

        for ((key, value) in defaultAttrs) {
            val attributeDefinition = getAttributeFromKey(key, allAttrs)
            if (attributeDefinition != null && value != null) {
                applyAttribute(target, value, attributeDefinition)
            }
        }
    }

    /**
     * Applies a single attribute to a view.
     *
     * @param target The view to apply the attribute to.
     * @param value The value of the attribute (e.g., "match_parent", "16dp", "#FF0000").
     * @param attribute The definition map of the attribute from the JSON configuration.
     * @param applyToView If true, the attribute change is immediately reflected on the view.
     *                    If false, only the internal data model (AttributeMap) is updated.
     *                    This is useful for batch updates during interactions like dragging.
     */
    fun applyAttribute(
        target: View,
        value: String,
        attribute: HashMap<String, Any>,
        applyToView: Boolean = true
    ) {
        val methodName = attribute[Constants.KEY_METHOD_NAME]?.toString()
        val className = attribute[Constants.KEY_CLASS_NAME]?.toString()
        val attributeName = attribute[Constants.KEY_ATTRIBUTE_NAME]?.toString()

        // Always update the data model (AttributeMap).
        if (attributeName != null) {
            viewAttributeMap[target]?.putValue(attributeName, value)
        }

        // Only apply the change to the actual view if requested.
        if (applyToView) {
            if (methodName != null && className != null) {
                InvokeUtil.invokeMethod(methodName, className, target, value, context)
            }
        }
    }

    /**
     * Gets a list of attributes that are available for a view but have not yet been applied.
     */
    fun getAvailableAttributesForView(target: View): List<HashMap<String, Any>> {
        val appliedKeys = viewAttributeMap[target]?.keySet() ?: emptyList()
        val allAttrs = getAllAttributesForView(target)

        // Filter out the attributes that are already applied.
        return allAttrs.filterNot { it[Constants.KEY_ATTRIBUTE_NAME] in appliedKeys }
    }

    /**
     * Gathers all possible attributes for a given view by traversing its class hierarchy
     * and its parent's hierarchy.
     */
    @Suppress("UNCHECKED_CAST")
    fun getAllAttributesForView(target: View): MutableList<HashMap<String, Any>> {
        val allAttrs: MutableList<HashMap<String, Any>> = ArrayList()
        val viewParentCls: Class<*>? = View::class.java.superclass

        // 1. Get attributes for the view itself
        var currentCls: Class<*>? = target.javaClass
        while (currentCls != null && currentCls != viewParentCls) {
            attributes[currentCls.name]?.let {
                allAttrs.addAll(0, it)
            }
            currentCls = currentCls.superclass
        }

        // 2. Get parent-specific attributes (e.g., layout_... attributes)
        val parent = target.parent
        if (parent != null && parent !is DesignEditor) {
            var parentCls: Class<*>? = parent.javaClass
            while (parentCls != null && parentCls != viewParentCls) {
                parentAttributes[parentCls.name]?.let {
                    allAttrs.addAll(it)
                }
                parentCls = parentCls.superclass
            }
        }

        return allAttrs
    }

    /**
     * Finds a specific attribute definition map from a list of attributes using its key.
     */
    fun getAttributeFromKey(
        key: String,
        attributeList: List<HashMap<String, Any>>
    ): HashMap<String, Any>? {
        return attributeList.firstOrNull { it[Constants.KEY_ATTRIBUTE_NAME] == key }
    }
}