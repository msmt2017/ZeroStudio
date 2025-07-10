
package android.zero.studio.uidesigner.tools

import android.content.Context
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.widget.LinearLayoutCompat
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import android.zero.studio.uidesigner.editor.initializer.AttributeInitializer
import android.zero.studio.uidesigner.editor.initializer.AttributeMap
import android.zero.studio.uidesigner.managers.IdManager
import android.zero.studio.uidesigner.utils.Constants
import android.zero.studio.uidesigner.utils.FileUtil
import android.zero.studio.uidesigner.utils.InvokeUtil
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserException
import org.xmlpull.v1.XmlPullParserFactory
import java.io.IOException
import java.io.StringReader

class XmlLayoutParser(context: Context) {

    val viewAttributeMap: HashMap<View, AttributeMap> = HashMap()

    private val initializer: AttributeInitializer
    private var container: LinearLayoutCompat? = null
    var root: View? = null
        private set

    init {
        val attributes = Gson()
            .fromJson<HashMap<String, List<HashMap<String, Any>>>>(
                FileUtil.readFromAsset(Constants.ATTRIBUTES_FILE, context),
                object : TypeToken<HashMap<String, List<HashMap<String, Any>>>>() {}.type
            )
        val parentAttributes = Gson()
            .fromJson<HashMap<String, List<HashMap<String, Any>>>>(
                FileUtil.readFromAsset(Constants.PARENT_ATTRIBUTES_FILE, context),
                object : TypeToken<HashMap<String, List<HashMap<String, Any>>>>() {}.type
            )

        initializer = AttributeInitializer(context, viewAttributeMap, attributes, parentAttributes)

        container = LinearLayoutCompat(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }
    }

    fun parseFromXml(xml: String, context: Context) {
        if (xml.isBlank()) return
        val listViews: MutableList<View> = ArrayList()
        container?.let { listViews.add(it) }

        try {
            val factory = XmlPullParserFactory.newInstance()
            val parser = factory.newPullParser()
            parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
            parser.setInput(StringReader(xml))

            while (parser.eventType != XmlPullParser.END_DOCUMENT) {
                when (parser.eventType) {
                    XmlPullParser.START_TAG -> {
                        // 增加健壮性：处理无法创建的View标签
                        val view = InvokeUtil.createView(parser.name, context) as? View

                        if (view != null) {
                            listViews.add(view)
                            val map = AttributeMap()
                            for (i in 0 until parser.attributeCount) {
                                if (!parser.getAttributeName(i).startsWith("xmlns")) {
                                    map.putValue(parser.getAttributeName(i), parser.getAttributeValue(i))
                                }
                            }
                            viewAttributeMap[view] = map
                        }
                    }

                    XmlPullParser.END_TAG -> {
                        // 增加健壮性：确保 listViews 中有足够的元素
                        if (listViews.size > 1 && parser.depth < listViews.size) {
                            val child = listViews.removeAt(listViews.size - 1)
                            val parent = listViews.last() as? ViewGroup
                            parent?.addView(child)
                        }
                    }
                }
                parser.next()
            }
        } catch (e: XmlPullParserException) {
            e.printStackTrace()
        } catch (e: IOException) {
            e.printStackTrace()
        } catch (e: Exception) {
            // 捕获其他潜在的解析错误，如类型转换失败
            e.printStackTrace()
        }

        IdManager.clear()

        container?.getChildAt(0)?.let {
            root = it
            (it.parent as? ViewGroup)?.removeView(it)
        }
        
        container = null // 释放临时的容器

        // 第一遍遍历：注册所有ID
        for (view in viewAttributeMap.keys) {
            val map = viewAttributeMap[view]!!
            if (map.contains("android:id")) {
                IdManager.addNewId(view, map.getValue("android:id"))
            }
        }

        // 第二遍遍历：应用所有属性
        for (view in viewAttributeMap.keys) {
            val map = viewAttributeMap[view]!!
            applyAttributes(view, map)
        }
    }

    private fun applyAttributes(target: View, attributeMap: AttributeMap) {
        val allAttrs = initializer.getAllAttributesForView(target)
        val keys = attributeMap.keySet()

        for (key in keys) {
            if (key == "android:id") {
                continue
            }
            
            val attr = initializer.getAttributeFromKey(key, allAttrs)
            if (attr != null) {
                val methodName = attr[Constants.KEY_METHOD_NAME]?.toString()
                val className = attr[Constants.KEY_CLASS_NAME]?.toString()
                val value = attributeMap.getValue(key)

                if (methodName != null && className != null) {
                    InvokeUtil.invokeMethod(methodName, className, target, value, target.context)
                }
            }
        }
    }
}