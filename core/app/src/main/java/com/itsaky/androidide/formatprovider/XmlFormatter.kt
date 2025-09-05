package com.itsaky.androidide.formatprovider

import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.StringReader
import java.io.StringWriter
import java.util.Collections

/**
 * A universal, highly configurable, and professional-grade code formatter for all XML dialects.
 * This final version uses a stateful, event-driven parsing loop to correctly handle complex nested
 * and mixed-content tags, ensuring robust and accurate formatting.
 */
class XmlFormatter(
    private val indentSize: Int = 4,
    private val attributesPerLineThreshold: Int = 2,
    private val keepBlankLinesBetweenElements: Boolean = true
) : CodeFormatter {

    private class FormatState(indentSize: Int) {
        val writer = StringWriter()
        val indentUnit = " ".repeat(indentSize)
        var currentIndentLevel = 0
        var justStartedTag = false
        var lastTagWasBlock = false

        fun indent(): String = indentUnit.repeat(currentIndentLevel)
    }
    
    private data class IndentRule(
        val indentChildren: Boolean = true,
        val keepSpaceWithin: Boolean = false,
        val isBlockElement: Boolean = true
    )

    private val defaultRule = IndentRule()
    private val indentRuleEngine = mapOf(
        "LinearLayout" to defaultRule, "RelativeLayout" to defaultRule, "FrameLayout" to defaultRule,
        "ConstraintLayout" to defaultRule, "CoordinatorLayout" to defaultRule, "MotionLayout" to defaultRule,
        "ScrollView" to defaultRule, "HorizontalScrollView" to defaultRule, "manifest" to defaultRule,
        "application" to defaultRule, "activity" to defaultRule, "service" to defaultRule,
        "receiver" to defaultRule, "provider" to defaultRule, "intent-filter" to defaultRule,
        "action" to IndentRule(isBlockElement = false), 
        "category" to IndentRule(isBlockElement = false),
        "androidx.constraintlayout.widget.ConstraintLayout" to defaultRule,
        "com.google.android.material.appbar.AppBarLayout" to defaultRule,
        "androidx.appcompat.widget.Toolbar" to defaultRule, 
        "androidx.recyclerview.widget.RecyclerView" to defaultRule, 
        "androidx.viewpager.widget.ViewPager" to defaultRule,
        "TextView" to defaultRule, "ImageView" to defaultRule, "Button" to defaultRule,
        "EditText" to defaultRule, "View" to defaultRule, "ProgressBar" to defaultRule,
        "com.google.android.material.textfield.TextInputLayout" to defaultRule,
        "com.google.android.material.button.MaterialButton" to defaultRule,
        "resources" to IndentRule(isBlockElement = false),
        "style" to defaultRule,
        "declare-styleable" to IndentRule(isBlockElement = false),
        "selector" to IndentRule(isBlockElement = false),
        "shape" to IndentRule(isBlockElement = false),
        "menu" to IndentRule(isBlockElement = false),
        "plurals" to IndentRule(isBlockElement = false),
        "string-array" to IndentRule(isBlockElement = false),
        "string" to IndentRule(keepSpaceWithin = true, isBlockElement = false),
        "item" to IndentRule(keepSpaceWithin = true, isBlockElement = false),
        "dimen" to IndentRule(indentChildren = false, keepSpaceWithin = true, isBlockElement = false),
        "color" to IndentRule(indentChildren = false, keepSpaceWithin = true, isBlockElement = false),
        "integer" to IndentRule(indentChildren = false, keepSpaceWithin = true, isBlockElement = false),
        "bool" to IndentRule(indentChildren = false, keepSpaceWithin = true, isBlockElement = false),
        "attr" to IndentRule(isBlockElement = false),
        "requestFocus" to IndentRule(isBlockElement = false),
        "merge" to defaultRule,
        "include" to defaultRule
    )

    override fun format(source: String): String {
        if (source.isBlank()) return source
        val isAndroidXml = source.contains("http://schemas.android.com/apk/res/android") ||
                           source.contains("<resources>") ||
                           source.contains("<manifest")
                           
        val mode = if (isAndroidXml) FormatMode.ANDROID else FormatMode.GENERIC
        
        return try {
            when (mode) {
                FormatMode.ANDROID -> formatAdvanced(source)
                FormatMode.GENERIC -> formatGeneric(source)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            source
        }
    }

    private fun formatAdvanced(source: String): String {
        val factory = XmlPullParserFactory.newInstance().apply { isNamespaceAware = true }
        val parser = factory.newPullParser()
        val trimmedSource = source.trim()
        parser.setInput(StringReader(trimmedSource))
        val state = FormatState(indentSize)
        
        if (trimmedSource.startsWith("<?xml")) {
            state.writer.append("<?xml version=\"1.0\" encoding=\"utf-8\"?>")
        }

        var eventType = parser.eventType
        if (eventType == XmlPullParser.START_DOCUMENT) {
            eventType = parser.next()
        }

        var justClosedTag = false
        
        while(eventType != XmlPullParser.END_DOCUMENT) {
            when (eventType) {
                XmlPullParser.START_TAG -> {
                    val tagName = parser.name
                    val rule = indentRuleEngine[tagName] ?: defaultRule

                    if (state.justStartedTag) {
                        state.writer.append(">")
                    }

                    if (state.writer.buffer.isNotEmpty()) {
                        state.writer.append("\n")
                        if (keepBlankLinesBetweenElements && rule.isBlockElement && state.lastTagWasBlock) {
                            state.writer.append("\n")
                        }
                    }
                    state.writer.append(state.indent()).append("<").append(tagName)
                    writeAttributes(state.writer, getSortedAttributes(parser), state.indent())

                    if (rule.indentChildren) {
                        state.currentIndentLevel++
                    }
                    
                    state.justStartedTag = true
                    justClosedTag = false
                    state.lastTagWasBlock = rule.isBlockElement
                }
                XmlPullParser.END_TAG -> {
                    val tagName = parser.name
                    val rule = indentRuleEngine[tagName] ?: defaultRule

                    if (rule.indentChildren) {
                        state.currentIndentLevel--
                    }

                    if (state.justStartedTag) {
                        state.writer.append(" />")
                    } else {
                        if (!rule.keepSpaceWithin) {
                           state.writer.append("\n").append(state.indent())
                        }
                        state.writer.append("</").append(tagName).append(">")
                    }
                    state.justStartedTag = false
                    justClosedTag = true
                }
                XmlPullParser.TEXT -> {
                    val text = parser.text
                    if (!text.isNullOrBlank()) {
                         if (state.justStartedTag) {
                            state.writer.append(">")
                            state.justStartedTag = false
                        }
                        state.writer.append(escapeXml(text))
                    }
                }
                XmlPullParser.COMMENT -> {
                    if (state.justStartedTag) {
                        state.writer.append(">\n")
                    } else if (state.writer.buffer.isNotEmpty() && !justClosedTag) {
                        state.writer.append("\n")
                    }
                    state.writer.append(state.indent()).append("<!--").append(parser.text).append("-->")
                    state.justStartedTag = false
                    justClosedTag = false
                    state.lastTagWasBlock = false
                }
                XmlPullParser.CDSECT -> {
                    if (state.justStartedTag) {
                        state.writer.append(">")
                        state.justStartedTag = false
                    }
                    state.writer.append("<![CDATA[").append(parser.text).append("]]>")
                }
            }
            eventType = parser.next()
        }
        return state.writer.toString().trim() + "\n"
    }
    
    private fun formatGeneric(source: String): String { 
        val factory = XmlPullParserFactory.newInstance().apply { isNamespaceAware = true }
        val parser = factory.newPullParser()
        parser.setInput(StringReader(source))
        val stringWriter = StringWriter()
        val serializer = Xml.newSerializer()
        serializer.setOutput(stringWriter)
        serializer.setFeature("http://xmlpull.org/v1/doc/features.html#indent-output", true)
        
        if (source.trim().startsWith("<?xml")) {
             serializer.startDocument("UTF-8", null)
        }

        var eventType = parser.eventType
        while (eventType != XmlPullParser.END_DOCUMENT) {
            when (eventType) {
                XmlPullParser.START_DOCUMENT -> {}
                XmlPullParser.START_TAG -> {
                    serializer.startTag(parser.namespace, parser.name)
                    for (i in 0 until parser.attributeCount) {
                        serializer.attribute(
                            parser.getAttributeNamespace(i),
                            parser.getAttributeName(i),
                            parser.getAttributeValue(i)
                        )
                    }
                }
                XmlPullParser.END_TAG -> serializer.endTag(parser.namespace, parser.name)
                XmlPullParser.TEXT -> serializer.text(parser.text)
                XmlPullParser.COMMENT -> serializer.comment(parser.text)
                XmlPullParser.CDSECT -> serializer.cdsect(parser.text)
                XmlPullParser.PROCESSING_INSTRUCTION -> serializer.processingInstruction(parser.text)
                XmlPullParser.DOCDECL -> serializer.docdecl(parser.text)
                XmlPullParser.IGNORABLE_WHITESPACE -> serializer.ignorableWhitespace(parser.text)
            }
            eventType = parser.next()
        }
        serializer.endDocument()
        return stringWriter.toString()
    }

    private enum class FormatMode { ANDROID, GENERIC }
    private data class Attribute(val namespace: String, val name: String, val value: String)
    private val attributeComparator = Comparator<Attribute> { a1, a2 ->
        val order1 = getAttributeOrder(a1); val order2 = getAttributeOrder(a2)
        if (order1 != order2) return@Comparator order1.compareTo(order2)
        "${a1.namespace}:${a1.name}".compareTo("${a2.namespace}:${a2.name}")
    }
    private fun getAttributeOrder(attr: Attribute): Int { 
        return when {
            attr.name.startsWith("xmlns:") -> 0
            attr.name == "id" -> 1
            attr.name == "style" -> 2
            attr.name == "layout_width" -> 3
            attr.name == "layout_height" -> 4
            attr.name.startsWith("layout_") -> 5
            attr.namespace == "android" -> 6
            attr.namespace.isNotEmpty() -> 7
            else -> 8
        }
    }
    private fun getSortedAttributes(parser: XmlPullParser): List<Attribute> { 
        return (0 until parser.attributeCount).map { i ->
            Attribute(
                parser.getAttributePrefix(i) ?: "",
                parser.getAttributeName(i),
                parser.getAttributeValue(i)
            )
        }.sortedWith(attributeComparator)
    }
    private fun writeAttribute(writer: StringWriter, attr: Attribute) {
        if (attr.namespace.isNotEmpty()) {
            writer.append(attr.namespace).append(":")
        }
        writer.append(attr.name).append("=\"").append(escapeXml(attr.value)).append("\"")
    }
    private fun writeAttributes(writer: StringWriter, attributes: List<Attribute>, indent: String) {
        if (attributes.isEmpty()) return
        if (attributes.size > attributesPerLineThreshold) {
            val attrIndent = indent + " ".repeat(indentSize)
            for (attr in attributes) {
                writer.append("\n").append(attrIndent)
                writeAttribute(writer, attr)
            }
            writer.append("\n").append(indent)
        } else {
            for (attr in attributes) {
                writer.append(" ")
                writeAttribute(writer, attr)
            }
        }
    }
    private fun escapeXml(text: String): String {
        return text.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")
    }
}