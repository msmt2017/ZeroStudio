package me.rerere.rikkahub.ui.components.richtext

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.fastForEach
import androidx.core.net.toUri
import me.rerere.highlight.HighlightText
import me.rerere.rikkahub.ui.components.table.ColumnDefinition
import me.rerere.rikkahub.ui.components.table.ColumnWidth
import me.rerere.rikkahub.ui.components.table.DataTable
import me.rerere.rikkahub.ui.theme.JetbrainsMono
import me.rerere.rikkahub.utils.unescapeHtml
import org.intellij.markdown.IElementType
import org.intellij.markdown.MarkdownElementTypes
import org.intellij.markdown.MarkdownTokenTypes
import org.intellij.markdown.ast.ASTNode
import org.intellij.markdown.ast.LeafASTNode
import org.intellij.markdown.ast.findChildOfType
import org.intellij.markdown.flavours.gfm.GFMElementTypes
import org.intellij.markdown.flavours.gfm.GFMFlavourDescriptor
import org.intellij.markdown.flavours.gfm.GFMTokenTypes
import org.intellij.markdown.parser.MarkdownParser

private val flavour by lazy {
    GFMFlavourDescriptor()
}

private val parser by lazy {
    MarkdownParser(flavour)
}

private val INLINE_LATEX_REGEX = Regex("\\\\\\((.+?)\\\\\\)")
private val BLOCK_LATEX_REGEX = Regex("\\\\\\[(.+?)\\\\\\]", RegexOption.DOT_MATCHES_ALL)
val THINKING_REGEX = Regex("<think>([\\s\\S]*?)(?:</think>|$)", RegexOption.DOT_MATCHES_ALL)

// 预处理markdown内容
private fun preProcess(content: String): String {
    // 替换行内公式 \( ... \) 到 $ ... $
    var result = content.replace(INLINE_LATEX_REGEX) { matchResult ->
        "$" + matchResult.groupValues[1] + "$"
    }

    // 替换块级公式 \[ ... \] 到 $$ ... $$
    result =
        result.replace(BLOCK_LATEX_REGEX) { matchResult ->
            "$$" + matchResult.groupValues[1] + "$$"
        }

    // 替换思考
    result = result.replace(THINKING_REGEX) { matchResult ->
        matchResult.groupValues[1].lines().filter { it.isNotBlank() }.joinToString("\n") { ">$it" }
    }

    return result
}

@Preview(showBackground = true)
@Composable
private fun MarkdownPreview() {
    MaterialTheme {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            MarkdownBlock(
                content = "Hi there!",
                modifier = Modifier.background(Color.Red)
            )
            MarkdownBlock(
                content = """
                    ### 🌍 This is Markdown Test This Markdown Test
                    1. How many roads must a man walk down
                        * the slings and arrows of outrageous fortune, Or to take arms against a sea of troubles,
                        * by opposing end them.
                            * How many times must a man look up, Before he can see the sky?
                            * How many times $ f(x) = \sum_{n=0}^{\infty} \frac{f^{(n)}(a)}{n!}(x-a)^n$
                    2. How many times must a man look up, Before he can see the sky?
                    * Before they're allowed to be free? Yes, 'n' how many times can a man turn his head

                    4. For in that sleep of death what dreams may come [citation](1)

                    This is Markdown Test, This is Markdown Test.

                    ***
                    This is Markdown Test, This is Markdown Test.

                    | Name | Age | Address | Email | Job | Homepage |
                    | ---- | --- | ------- | ----- | --- | -------- |
                    | John | 25  | New York | john@example.com | Software Engineer | john.com |
                    | Jane | 26  | London   | jane@example.com | Data Scientist | jane.com |

                    ## HTML Escaping
                    This is a &gt;  test

                """.trimIndent()
            )
        }
    }
}

@Composable
fun MarkdownBlock(
    content: String,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current,
    onClickCitation: (String) -> Unit = {}
) {
    val preprocessed = remember(content) { preProcess(content) }
    val astTree = remember(preprocessed) {
        parser.buildMarkdownTreeFromString(preprocessed)
//            .also {
//                dumpAst(it, preprocessed) // for debugging ast tree
//            }
    }

    ProvideTextStyle(style) {
        Column(
            modifier = modifier.padding(start = 4.dp)
        ) {
            astTree.children.fastForEach { child ->
                MarkdownNode(
                    node = child,
                    content = preprocessed,
                    onClickCitation = onClickCitation
                )
            }
        }
    }
}

// for debug
private fun dumpAst(node: ASTNode, text: String, indent: String = "") {
    println("$indent${node.type} ${if (node.children.isEmpty()) node.getTextInNode(text) else ""} | ${node.javaClass.simpleName}")
    node.children.fastForEach {
        dumpAst(it, text, "$indent  ")
    }
}

object HeaderStyle {
    val H1 = TextStyle(
        fontStyle = FontStyle.Normal,
        fontWeight = FontWeight.Bold,
        fontSize = 24.sp
    )

    val H2 = TextStyle(
        fontStyle = FontStyle.Normal,
        fontWeight = FontWeight.Bold,
        fontSize = 20.sp
    )

    val H3 = TextStyle(
        fontStyle = FontStyle.Normal,
        fontWeight = FontWeight.Bold,
        fontSize = 18.sp
    )

    val H4 = TextStyle(
        fontStyle = FontStyle.Normal,
        fontWeight = FontWeight.Bold,
        fontSize = 16.sp
    )

    val H5 = TextStyle(
        fontStyle = FontStyle.Normal,
        fontWeight = FontWeight.Bold,
        fontSize = 14.sp
    )

    val H6 = TextStyle(
        fontStyle = FontStyle.Normal,
        fontWeight = FontWeight.Bold,
        fontSize = 12.sp
    )
}

@Composable
fun MarkdownNode(
    node: ASTNode,
    content: String,
    modifier: Modifier = Modifier,
    onClickCitation: (String) -> Unit = {},
    listLevel: Int = 0
) {
    when (node.type) {
        // 文件根节点
        MarkdownElementTypes.MARKDOWN_FILE -> {
            node.children.fastForEach { child ->
                MarkdownNode(
                    node = child,
                    content = content,
                    modifier = modifier,
                    onClickCitation = onClickCitation
                )
            }
        }

        // 段落
        MarkdownElementTypes.PARAGRAPH -> {
            Paragraph(
                node = node,
                content = content,
                modifier = modifier,
                onClickCitation = onClickCitation
            )
        }

        // 标题
        MarkdownElementTypes.ATX_1,
        MarkdownElementTypes.ATX_2,
        MarkdownElementTypes.ATX_3,
        MarkdownElementTypes.ATX_4,
        MarkdownElementTypes.ATX_5,
        MarkdownElementTypes.ATX_6 -> {
            val style = when (node.type) {
                MarkdownElementTypes.ATX_1 -> HeaderStyle.H1
                MarkdownElementTypes.ATX_2 -> HeaderStyle.H2
                MarkdownElementTypes.ATX_3 -> HeaderStyle.H3
                MarkdownElementTypes.ATX_4 -> HeaderStyle.H4
                MarkdownElementTypes.ATX_5 -> HeaderStyle.H5
                MarkdownElementTypes.ATX_6 -> HeaderStyle.H6
                else -> throw IllegalArgumentException("Unknown header type")
            }
            ProvideTextStyle(value = style) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    node.children.fastForEach { node ->
                        if (node.type == MarkdownTokenTypes.ATX_CONTENT) {
                            Paragraph(
                                node = node,
                                content = content,
                                onClickCitation = onClickCitation,
                                modifier = modifier.padding(vertical = 16.dp),
                                trim = true,
                            )
                        }
                    }
                }
            }
        }

        // 列表
        MarkdownElementTypes.UNORDERED_LIST -> {
            UnorderedListNode(
                node = node,
                content = content,
                modifier = modifier.padding(vertical = 4.dp),
                onClickCitation = onClickCitation,
                level = listLevel
            )
        }

        MarkdownElementTypes.ORDERED_LIST -> {
            OrderedListNode(
                node = node,
                content = content,
                modifier = modifier.padding(vertical = 4.dp),
                onClickCitation = onClickCitation,
                level = listLevel
            )
        }

        // 引用块
        MarkdownElementTypes.BLOCK_QUOTE -> {
            ProvideTextStyle(LocalTextStyle.current.copy(fontStyle = FontStyle.Italic)) {
                val borderColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                val bgColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)
                Column(
                    modifier = Modifier
                        .drawWithContent {
                            drawContent()
                            drawRect(
                                color = bgColor,
                                size = size
                            )
                            drawRect(
                                color = borderColor,
                                size = Size(10f, size.height)
                            )
                        }
                        .padding(8.dp)
                ) {
                    node.children.fastForEach { child ->
                        MarkdownNode(
                            node = child,
                            content = content,
                            onClickCitation = onClickCitation
                        )
                    }
                }
            }
        }

        // 链接
        MarkdownElementTypes.INLINE_LINK -> {
            val linkText =
                node.findChildOfType(MarkdownElementTypes.LINK_TEXT)
                    ?.findChildOfType(GFMTokenTypes.GFM_AUTOLINK)
                    ?.getTextInNode(content) ?: ""
            val linkDest =
                node.findChildOfType(MarkdownElementTypes.LINK_DESTINATION)?.getTextInNode(content)
                    ?: ""
            val context = LocalContext.current
            Text(
                text = linkText,
                color = MaterialTheme.colorScheme.primary,
                textDecoration = TextDecoration.Underline,
                modifier = modifier.clickable {
                    val intent = Intent(Intent.ACTION_VIEW, linkDest.toUri())
                    context.startActivity(intent)
                }
            )
        }

        // 加粗和斜体
        MarkdownElementTypes.EMPH -> {
            ProvideTextStyle(TextStyle(fontStyle = FontStyle.Italic)) {
                node.children.fastForEach { child ->
                    MarkdownNode(
                        node = child,
                        content = content,
                        modifier = modifier,
                        onClickCitation = onClickCitation
                    )
                }
            }
        }

        MarkdownElementTypes.STRONG -> {
            ProvideTextStyle(TextStyle(fontWeight = FontWeight.SemiBold)) {
                node.children.fastForEach { child ->
                    MarkdownNode(
                        node = child,
                        content = content,
                        modifier = modifier,
                        onClickCitation = onClickCitation
                    )
                }
            }
        }

        // GFM 特殊元素
        GFMElementTypes.STRIKETHROUGH -> {
            Text(
                text = node.getTextInNode(content),
                textDecoration = TextDecoration.LineThrough,
                modifier = modifier
            )
        }

        GFMElementTypes.TABLE -> {
            TableNode(node = node, content = content, modifier = modifier)
        }

        MarkdownTokenTypes.HORIZONTAL_RULE -> {
            HorizontalDivider(
                modifier = Modifier.padding(vertical = 16.dp),
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                thickness = 0.5.dp
            )
        }

        // 图片
        MarkdownElementTypes.IMAGE -> {
            val altText =
                node.findChildOfType(MarkdownElementTypes.LINK_TEXT)?.getTextInNode(content) ?: ""
            val imageUrl =
                node.findChildOfType(MarkdownElementTypes.LINK_DESTINATION)?.getTextInNode(content)
                    ?: ""
            Column(
                modifier = modifier,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // 这里可以使用Coil等图片加载库加载图片
                ZoomableAsyncImage(model = imageUrl, contentDescription = altText)
            }
        }

        GFMElementTypes.INLINE_MATH -> {
            val formula = node.getTextInNode(content)
            MathInline(
                formula,
                modifier = modifier
                    .padding(horizontal = 1.dp)
            )
        }

        GFMElementTypes.BLOCK_MATH -> {
            val formula = node.getTextInNode(content)
            MathBlock(
                formula, modifier = modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
            )
        }

        MarkdownElementTypes.CODE_SPAN -> {
            val code = node.getTextInNode(content).trim('`')
            Text(
                text = code,
                fontFamily = FontFamily.Monospace,
                modifier = modifier
            )
        }

        MarkdownElementTypes.CODE_BLOCK -> {
            val code = node.getTextInNode(content)
            HighlightCodeBlock(
                code = code,
                language = "plaintext",
                modifier = Modifier
                    .padding(bottom = 4.dp)
                    .fillMaxWidth(),
                completeCodeBlock = true
            )
        }

        // 代码块
        MarkdownElementTypes.CODE_FENCE -> {
            // 这里不能直接取CODE_FENCE_CONTENT的内容，因为首行indent没有包含在内
            // 因此，需要往上找到最后一个EOL元素，用它来作为代码块的起始offset
            val contentStartIndex =
                node.children.indexOfFirst { it.type == MarkdownTokenTypes.CODE_FENCE_CONTENT }
            if (contentStartIndex == -1) return
            val eolElement = node.children.subList(0, contentStartIndex)
                .findLast { it.type == MarkdownTokenTypes.EOL } ?: return
            val codeContentStartOffset = eolElement.endOffset
            val codeContentEndOffset = node.children
                .findLast { it.type == MarkdownTokenTypes.CODE_FENCE_CONTENT }?.endOffset ?: return
            val code = content.substring(
                codeContentStartOffset,
                codeContentEndOffset
            ).trimIndent()

            val language = node.findChildOfType(MarkdownTokenTypes.FENCE_LANG)
                ?.getTextInNode(content)
                ?: "plaintext"
            val hasEnd = node.findChildOfType(MarkdownTokenTypes.CODE_FENCE_END) != null

            HighlightCodeBlock(
                code = code,
                language = language,
                modifier = Modifier
                    .padding(bottom = 4.dp)
                    .fillMaxWidth(),
                completeCodeBlock = hasEnd
            )
        }

        MarkdownTokenTypes.TEXT -> {
            val text = node.getTextInNode(content)
            Text(
                text = text,
                modifier = modifier,
            )
        }

        MarkdownElementTypes.HTML_BLOCK -> {
            val text = node.getTextInNode(content)
            HighlightText(
                code = text,
                modifier = modifier,
                language = "html",
                fontFamily = JetbrainsMono,
            )
        }

        // 其他类型的节点，递归处理子节点
        else -> {
            // 递归处理其他节点的子节点
            node.children.fastForEach { child ->
                MarkdownNode(
                    node = child,
                    content = content,
                    modifier = modifier,
                    onClickCitation = onClickCitation
                )
            }
        }
    }
}

@Composable
private fun UnorderedListNode(
    node: ASTNode,
    content: String,
    modifier: Modifier = Modifier,
    onClickCitation: (String) -> Unit = {},
    level: Int = 0
) {
    val bulletStyle = when (level % 3) {
        0 -> "• "
        1 -> "◦ "
        else -> "▪ "
    }

    Column(
        modifier = modifier.padding(start = (level * 8).dp)
    ) {
        node.children.fastForEach { child ->
            if (child.type == MarkdownElementTypes.LIST_ITEM) {
                ListItemNode(
                    node = child,
                    content = content,
                    bulletText = bulletStyle,
                    onClickCitation = onClickCitation,
                    level = level
                )
            }
        }
    }
}

@Composable
private fun OrderedListNode(
    node: ASTNode,
    content: String,
    modifier: Modifier = Modifier,
    onClickCitation: (String) -> Unit = {},
    level: Int = 0
) {
    Column(modifier.padding(start = (level * 8).dp)) {
        var index = 1
        node.children.fastForEach { child ->
            if (child.type == MarkdownElementTypes.LIST_ITEM) {
                val numberText = child.findChildOfType(MarkdownTokenTypes.LIST_NUMBER)
                    ?.getTextInNode(content) ?: "$index. "
                ListItemNode(
                    node = child,
                    content = content,
                    bulletText = numberText,
                    onClickCitation = onClickCitation,
                    level = level
                )
                index++
            }
        }
    }
}

@Composable
private fun ListItemNode(
    node: ASTNode,
    content: String,
    bulletText: String,
    onClickCitation: (String) -> Unit = {},
    level: Int
) {
    Column {
        // 分离列表项的直接内容和嵌套列表
        val (directContent, nestedLists) = separateContentAndLists(node)
        // directContent 渲染处理
        if (directContent.isNotEmpty()) {
            Row {
                Text(
                    text = bulletText,
                    modifier = Modifier.alignByBaseline()
                )
                FlowRow {
                    directContent.fastForEach { contentChild ->
                        MarkdownNode(
                            node = contentChild,
                            content = content,
                            onClickCitation = onClickCitation,
                            listLevel = level
                        )
                    }
                }
            }
        }
        // nestedLists 渲染处理
        nestedLists.fastForEach { nestedList ->
            MarkdownNode(
                node = nestedList,
                content = content,
                onClickCitation = onClickCitation,
                listLevel = level + 1 // 增加层级
            )
        }
    }
}

// 分离列表项的直接内容和嵌套列表
private fun separateContentAndLists(listItemNode: ASTNode): Pair<List<ASTNode>, List<ASTNode>> {
    val directContent = mutableListOf<ASTNode>()
    val nestedLists = mutableListOf<ASTNode>()
    listItemNode.children.fastForEach { child ->
        when (child.type) {
            MarkdownElementTypes.UNORDERED_LIST,
            MarkdownElementTypes.ORDERED_LIST -> {
                nestedLists.add(child)
            }

            else -> {
                directContent.add(child)
            }
        }
    }
    return directContent to nestedLists
}

@Composable
private fun Paragraph(
    node: ASTNode,
    content: String,
    trim: Boolean = false,
    onClickCitation: (String) -> Unit = {},
    modifier: Modifier,
) {
    // dumpAst(node, content)
    if (node.findChildOfType(MarkdownElementTypes.IMAGE, GFMElementTypes.BLOCK_MATH) != null) {
        FlowRow(modifier = modifier) {
            node.children.fastForEach { child ->
                MarkdownNode(
                    node = child,
                    content = content,
                    onClickCitation = onClickCitation
                )
            }
        }
        return
    }

    val colorScheme = MaterialTheme.colorScheme
    val inlineContents = remember {
        mutableStateMapOf<String, InlineTextContent>()
    }

    val textStyle = LocalTextStyle.current
    val density = LocalDensity.current
    FlowRow(
        modifier = modifier
            .then(
                if (node.nextSibling() != null)
                    Modifier
                        .padding(bottom = 4.dp)
                else Modifier
            )
    ) {
        val annotatedString = remember(content) {
            buildAnnotatedString {
                node.children.fastForEach { child ->
                    appendMarkdownNodeContent(
                        node = child,
                        content = content,
                        inlineContents = inlineContents,
                        colorScheme = colorScheme,
                        onClickCitation = onClickCitation,
                        style = textStyle,
                        density = density,
                        trim = trim,
                    )
                }
            }
        }
        Text(
            text = annotatedString,
            modifier = Modifier,
            inlineContent = inlineContents,
            softWrap = true,
            overflow = TextOverflow.Visible
        )
    }
}

@Composable
private fun TableNode(node: ASTNode, content: String, modifier: Modifier = Modifier) {
    // 提取表格的标题行和数据行
    val headerNode = node.children.find { it.type == GFMElementTypes.HEADER }
    val rowNodes = node.children.filter { it.type == GFMElementTypes.ROW }

    // 计算列数（从标题行获取）
    val columnCount = headerNode?.children?.count { it.type == GFMTokenTypes.CELL } ?: 0

    // 检查是否有足够的列来显示表格
    if (columnCount == 0) return

    // 提取表头单元格文本
    val headerCells = headerNode?.children
        ?.filter { it.type == GFMTokenTypes.CELL }
        ?.map { it.getTextInNode(content).trim() }
        ?: emptyList()

    // 提取所有行的数据
    val rows = rowNodes.map { rowNode ->
        rowNode.children
            .filter { it.type == GFMTokenTypes.CELL }
            .map { it.getTextInNode(content).trim() }
    }

    // 创建列定义
    val columns = List(columnCount) { columnIndex ->
        ColumnDefinition<List<String>>(
            header = {
                MarkdownBlock(
                    content = if (columnIndex < headerCells.size) headerCells[columnIndex] else "",
                )
            },
            cell = { rowData ->
                MarkdownBlock(
                    content = if (columnIndex < rowData.size) rowData[columnIndex] else "",
                )
            },
            width = ColumnWidth.Adaptive(min = 80.dp)
        )
    }

    // 渲染表格
    DataTable(
        columns = columns,
        data = rows,
        modifier = modifier
            .padding(vertical = 8.dp)
            .fillMaxWidth()

    )
}

private fun AnnotatedString.Builder.appendMarkdownNodeContent(
    node: ASTNode,
    content: String,
    trim: Boolean = false,
    inlineContents: MutableMap<String, InlineTextContent>,
    colorScheme: ColorScheme,
    density: Density,
    style: TextStyle,
    onClickCitation: (String) -> Unit = {},
) {
    when {
        node.type == MarkdownTokenTypes.BLOCK_QUOTE -> {}

        node is LeafASTNode -> {
            append(
                node
                    .getTextInNode(content)
                    .unescapeHtml()
                    .let { if (trim) it.trim() else it }
            )
        }

        node.type == MarkdownElementTypes.EMPH -> {
            withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                node.children
                    .trim(MarkdownTokenTypes.EMPH, 1)
                    .fastForEach {
                        appendMarkdownNodeContent(
                            node = it,
                            content = content,
                            inlineContents = inlineContents,
                            colorScheme = colorScheme,
                            density = density,
                            style = style,
                            onClickCitation = onClickCitation
                        )
                    }
            }
        }

        node.type == MarkdownElementTypes.STRONG -> {
            withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) {
                node.children
                    .trim(MarkdownTokenTypes.EMPH, 2)
                    .fastForEach {
                        appendMarkdownNodeContent(
                            node = it,
                            content = content,
                            inlineContents = inlineContents,
                            colorScheme = colorScheme,
                            density = density,
                            style = style,
                            onClickCitation = onClickCitation
                        )
                    }
            }
        }

        node.type == GFMElementTypes.STRIKETHROUGH -> {
            withStyle(SpanStyle(textDecoration = TextDecoration.LineThrough)) {
                node.children
                    .trim(GFMTokenTypes.TILDE, 2)
                    .fastForEach {
                        appendMarkdownNodeContent(
                            node = it,
                            content = content,
                            inlineContents = inlineContents,
                            colorScheme = colorScheme,
                            density = density,
                            style = style,
                            onClickCitation = onClickCitation
                        )
                    }
            }
        }

        node.type == MarkdownElementTypes.INLINE_LINK -> {
            val linkDest =
                node.findChildOfType(MarkdownElementTypes.LINK_DESTINATION)?.getTextInNode(content)
                    ?: ""
            val linkText =
                node.findChildOfType(MarkdownElementTypes.LINK_TEXT)
                    ?.findChildOfType(GFMTokenTypes.GFM_AUTOLINK)
                    ?.getTextInNode(content)
                    ?: linkDest
            if (linkText == "citation") {
                // 如果是引用，则特殊处理
                val splits = linkDest.split(":")
                if (splits.size == 2) {
                    val (index, id) = splits
                    inlineContents.putIfAbsent(
                        "citation:$linkDest", InlineTextContent(
                            placeholder = Placeholder(
                                width = 1.em,
                                height = 1.em,
                                placeholderVerticalAlign = PlaceholderVerticalAlign.TextCenter,
                            ),
                            children = {
                                Box(
                                    modifier = Modifier
                                        .clickable {
                                            onClickCitation(id.trim())
                                        }
                                        .fillMaxSize()
                                        .clip(RoundedCornerShape(20))
                                        .background(colorScheme.primary.copy(0.2f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = index.toString(),
                                        modifier = Modifier.wrapContentSize(),
                                        style = TextStyle(
                                            fontSize = 12.sp,
                                            lineHeight = 12.sp,
                                            fontWeight = FontWeight.SemiBold
                                        ),
                                    )
                                }
                            }
                        ))
                    appendInlineContent("citation:$linkDest")
                }
            } else {
                withLink(LinkAnnotation.Url(linkDest)) {
                    withStyle(
                        SpanStyle(
                            color = colorScheme.primary,
                            textDecoration = TextDecoration.Underline
                        )
                    ) {
                        append(linkText)
                    }
                }
            }
        }

        node.type == MarkdownElementTypes.CODE_SPAN -> {
            val code = node.getTextInNode(content).trim('`')
            withStyle(
                SpanStyle(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 0.95.em,
                    background = colorScheme.secondaryContainer.copy(alpha = 0.2f),
                )
            ) {
                append(code)
            }
        }

        node.type == GFMElementTypes.INLINE_MATH -> {
            // formula as id
            val formula = node.getTextInNode(content)
            appendInlineContent(formula, "[Latex]")
            val (width, height) = with(density) {
                assumeLatexSize(
                    latex = formula,
                    fontSize = style.fontSize.toPx()
                ).let {
                    it.width().toSp() to it.height().toSp()
                }
            }
            inlineContents.putIfAbsent(
                /* key = */ formula,
                /* value = */ InlineTextContent(
                    placeholder = Placeholder(
                        width = width,
                        height = height,
                        placeholderVerticalAlign = PlaceholderVerticalAlign.TextCenter
                    ),
                    children = {
                        MathInline(
                            latex = formula,
                            modifier = Modifier
                        )
                    }
                )
            )
        }

        // 其他类型继续递归处理
        else -> {
            node.children.fastForEach {
                appendMarkdownNodeContent(
                    node = it,
                    content = content,
                    inlineContents = inlineContents,
                    colorScheme = colorScheme,
                    density = density,
                    style = style,
                    onClickCitation = onClickCitation
                )
            }
        }
    }
}

private fun ASTNode.getTextInNode(text: String): String {
    return text.substring(startOffset, endOffset)
}

private fun ASTNode.getTextInNode(text: String, type: IElementType): String {
    var startOffset = -1
    var endOffset = -1
    children.fastForEach {
        if (it.type == type) {
            if (startOffset == -1) {
                startOffset = it.startOffset
            }
            endOffset = it.endOffset
        }
    }
    if (startOffset == -1 || endOffset == -1) {
        return ""
    }
    return text.substring(startOffset, endOffset)
}

private fun ASTNode.nextSibling(): ASTNode? {
    val brother = this.parent?.children ?: return null
    for (i in brother.indices) {
        if (brother[i] == this) {
            if (i + 1 < brother.size) {
                return brother[i + 1]
            }
        }
    }
    return null
}

private fun ASTNode.findChildOfType(vararg types: IElementType): ASTNode? {
    if (this.type in types) return this
    for (child in children) {
        val result = child.findChildOfType(*types)
        if (result != null) return result
    }
    return null
}

private fun ASTNode.traverseChildren(
    action: (ASTNode) -> Unit
) {
    children.fastForEach { child ->
        action(child)
        child.traverseChildren(action)
    }
}

private fun List<ASTNode>.trim(type: IElementType, size: Int): List<ASTNode> {
    if (this.isEmpty() || size <= 0) return this
    var start = 0
    var end = this.size
    // 从头裁剪
    var trimmed = 0
    while (start < end && trimmed < size && this[start].type == type) {
        start++
        trimmed++
    }
    // 从尾裁剪
    trimmed = 0
    while (end > start && trimmed < size && this[end - 1].type == type) {
        end--
        trimmed++
    }
    return this.subList(start, end)
}
