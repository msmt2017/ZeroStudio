package android.zero.studio.uidesigner.tools

import android.content.Context
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.widget.LinearLayoutCompat
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import android.zero.studio.uidesigner.editor.initializer.AttributeInitializer
import android.zero.studio.uidesigner.editor.initializer.AttributeMap
import android.zero.studio.uidesigner.managers.IdManager.addNewId
import android.zero.studio.uidesigner.managers.IdManager.clear
import android.zero.studio.uidesigner.utils.Constants
import android.zero.studio.uidesigner.utils.FileUtil
import android.zero.studio.uidesigner.utils.InvokeUtil.createView
import android.zero.studio.uidesigner.utils.InvokeUtil.invokeMethod
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserException
import org.xmlpull.v1.XmlPullParserFactory
import java.io.IOException
import java.io.StringReader

class XmlLayoutParser(context: Context) {

    val viewAttributeMap: HashMap<View, AttributeMap> = HashMap()

    private val initializer: AttributeInitializer
    private val container: LinearLayoutCompat

    init {
        val attributes = Gson()
            .fromJson<HashMap<String, List<HashMap<String, Any>>>>(
                FileUtil.readFromAsset(Constants.ATTRIBUTES_FILE, context),
                object : TypeToken<HashMap<String, List<HashMap<String, Any>>>>() {
                }.type
            )
        val parentAttributes = Gson()
            .fromJson<HashMap<String, List<HashMap<String, Any>>>>(
                FileUtil.readFromAsset(Constants.PARENT_ATTRIBUTES_FILE, context),
                object : TypeToken<HashMap<String, List<HashMap<String, Any>>>>() {
                }.type
            )

        initializer = AttributeInitializer(context, attributes, parentAttributes)

        container = LinearLayoutCompat(context)
        container.layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
        )
    }

    val root: View
        get() {
            val view = container.getChildAt(0)
            container.removeView(view)
            return view
        }

    fun parseFromXml(xml: String, context: Context) {
        val listViews: MutableList<View> = ArrayList()
        listViews.add(container)

        try {
            val factory = XmlPullParserFactory.newInstance()
            val parser = factory.newPullParser()
            parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
            parser.setInput(StringReader(xml))

            while (parser.eventType != XmlPullParser.END_DOCUMENT) {
                when (parser.eventType) {
                    XmlPullParser.START_TAG -> {
                        val view = createView(parser.name, context) as View
                        listViews.add(view)

                        val map = AttributeMap()

                        var i = 0
                        while (i < parser.attributeCount) {
                            if (!parser.getAttributeName(i).startsWith("xmlns")) {
                                map.putValue(parser.getAttributeName(i), parser.getAttributeValue(i))
                            }
                            i++
                        }

                        viewAttributeMap[view] = map
                    }

                    XmlPullParser.END_TAG -> {
                        val index = parser.depth
                        (listViews[index - 1] as ViewGroup).addView(listViews[index])
                        listViews.removeAt(index)
                    }
                }
                parser.next()
            }
        } catch (e: XmlPullParserException) {
            e.printStackTrace()
        } catch (e: IOException) {
            e.printStackTrace()
        }

        clear()

        for (view in viewAttributeMap.keys) {
            val map = viewAttributeMap[view]!!

            for (key in map.keySet()) {
                if (key == "android:id") {
                    addNewId(view, map.getValue("android:id"))
                }
            }
        }

        for (view in viewAttributeMap.keys) {
            val map = viewAttributeMap[view]!!
            applyAttributes(view, map)
        }
    }

    private fun applyAttributes(target: View, attributeMap: AttributeMap) {
        val allAttrs = initializer.getAllAttributesForView(target)

        val keys = attributeMap.keySet()

        for (i in keys.indices.reversed()) {
            val key = keys[i]

            val attr = initializer.getAttributeFromKey(key, allAttrs) ?: return
            val methodName = attr[Constants.KEY_METHOD_NAME].toString()
            val className = attr[Constants.KEY_CLASS_NAME].toString()
            val value = attributeMap.getValue(key)

            if (key == "android:id") {
                continue
            }

            invokeMethod(methodName, className, target, value, target.context)
        }
    }
}
