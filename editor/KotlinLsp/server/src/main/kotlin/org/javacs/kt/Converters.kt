package org.javacs.kt

// Import aliases to resolve name collisions
import com.itsaky.androidide.lsp.models.CompletionParams as AndroidIdeCompletionParams
import com.itsaky.androidide.lsp.models.CompletionResult as AndroidIdeCompletionResult
import com.itsaky.androidide.lsp.models.DefinitionParams as AndroidIdeDefinitionParams
import com.itsaky.androidide.lsp.models.DefinitionResult as AndroidIdeDefinitionResult
import com.itsaky.androidide.lsp.models.DiagnosticResult as AndroidIdeDiagnosticResult
import com.itsaky.androidide.lsp.models.ReferenceParams as AndroidIdeReferenceParams
import com.itsaky.androidide.lsp.models.SignatureHelpParams as AndroidIdeSignatureHelpParams
import com.itsaky.androidide.lsp.models.FormatCodeParams as AndroidIdeFormatCodeParams
import com.itsaky.androidide.lsp.models.CodeFormatResult as AndroidIdeCodeFormatResult
import com.itsaky.androidide.lsp.models.SignatureHelp as AndroidIdeSignatureHelp
import com.itsaky.androidide.lsp.models.SignatureInformation as AndroidIdeSignatureInformation
import com.itsaky.androidide.lsp.models.ParameterInformation as AndroidIdeParameterInformation
import com.itsaky.androidide.lsp.models.DiagnosticItem as AndroidIdeDiagnosticItem
import com.itsaky.androidide.lsp.models.CompletionItem as AndroidIdeCompletionItem
import com.itsaky.androidide.lsp.models.CompletionItemKind as AndroidIdeCompletionItemKind
import com.itsaky.androidide.lsp.models.DiagnosticSeverity as AndroidIdeDiagnosticSeverity
import com.itsaky.androidide.lsp.models.MarkupContent as AndroidIdeMarkupContent
import com.itsaky.androidide.lsp.models.MarkupKind as AndroidIdeMarkupKind
import com.itsaky.androidide.lsp.models.IndexedTextEdit as AndroidIdeIndexedTextEdit
import com.itsaky.androidide.models.Position as AndroidIdePosition
import com.itsaky.androidide.models.Range as AndroidIdeRange
import com.itsaky.androidide.models.Location as AndroidIdeLocation
import com.itsaky.androidide.progress.ICancelChecker

import org.eclipse.lsp4j.*
import org.eclipse.lsp4j.jsonrpc.messages.Either
import org.javacs.kt.util.parseURI
import java.nio.file.Paths

// Range and Position Converters
fun Range.toIdeRange(): AndroidIdeRange =
    AndroidIdeRange(this.start.toIdePosition(), this.end.toIdePosition())

fun Position.toIdePosition(): AndroidIdePosition =
    AndroidIdePosition(this.line, this.character)

fun AndroidIdeRange.toLsp4j(): Range =
    Range(this.start.toLsp4j(), this.end.toLsp4j())

fun AndroidIdePosition.toLsp4j(): Position =
    Position(this.line, this.column)

// Location Converters
fun Location.toIdeLocation(): AndroidIdeLocation =
    AndroidIdeLocation(Paths.get(parseURI(this.uri)), this.range.toIdeRange())

// Completion Converters
fun CompletionParams.toIdeCompletionParams(sp: SourcePath): AndroidIdeCompletionParams {
    val path = Paths.get(parseURI(this.textDocument.uri))
    val content = sp.content(path.toUri())
    val offset = org.javacs.kt.position.offset(content, this.position)

    val lineStart = if (offset > 0) content.lastIndexOf('\n', offset - 1) + 1 else 0
    val currentTextBeforeCursor = content.substring(lineStart, offset)
    val prefix = currentTextBeforeCursor.takeLastWhile { it.isLetterOrDigit() || it == '_' || it == '.' }

    return AndroidIdeCompletionParams(
        position = this.position.toIdePosition(),
        file = path,
        cancelChecker = ICancelChecker.CANCELLED 
    ).apply {
        this.prefix = prefix
        this.content = content
    }
}

fun AndroidIdeCompletionParams.toLsp4j(): CompletionParams =
    CompletionParams(TextDocumentIdentifier(this.file.toUri().toString()), this.position.toLsp4j())

fun Either<List<CompletionItem>, CompletionList>.toIdeCompletionResult(): AndroidIdeCompletionResult {
    val items = this.left ?: this.right?.items ?: emptyList()
    val ideItems = items.map { it.toIdeCompletionItem() }
    val isIncomplete = this.right?.isIncomplete ?: (items.size >= 75)
    return AndroidIdeCompletionResult(ideItems).apply {
        this.isIncomplete = isIncomplete
    }
}

fun CompletionItem.toIdeCompletionItem(): AndroidIdeCompletionItem {
    return AndroidIdeCompletionItem(
        ideLabel = this.label,
        detail = this.detail ?: "",
        insertText = this.insertText ?: this.label,
        insertTextFormat = null,
        sortText = this.sortText,
        command = null,
        completionKind = this.kind?.toIdeCompletionItemKind() ?: AndroidIdeCompletionItemKind.NONE,
        matchLevel = com.itsaky.androidide.lsp.models.MatchLevel.NO_MATCH,
        additionalTextEdits = null,
        data = null
    )
}

fun CompletionItemKind.toIdeCompletionItemKind(): AndroidIdeCompletionItemKind =
    try {
        AndroidIdeCompletionItemKind.valueOf(this.name)
    } catch (e: IllegalArgumentException) {
        AndroidIdeCompletionItemKind.NONE
    }

// Definition Converters
fun AndroidIdeDefinitionParams.toLsp4j(): DefinitionParams =
    DefinitionParams(TextDocumentIdentifier(this.file.toUri().toString()), this.position.toLsp4j())

// Reference Converters
fun AndroidIdeReferenceParams.toLsp4j(): ReferenceParams =
    ReferenceParams(TextDocumentIdentifier(this.file.toUri().toString()), this.position.toLsp4j(), ReferenceContext(this.includeDeclaration))

// SignatureHelp Converters
fun AndroidIdeSignatureHelpParams.toLsp4j(): SignatureHelpParams =
    SignatureHelpParams(TextDocumentIdentifier(this.file.toUri().toString()), this.position.toLsp4j())

fun SignatureHelp.toIdeSignatureHelp(): AndroidIdeSignatureHelp {
    val ideSignatures = this.signatures.map { it.toIdeSignatureInformation() }
    return AndroidIdeSignatureHelp(
        ideSignatures,
        this.activeSignature,
        this.activeParameter
    )
}

fun SignatureInformation.toIdeSignatureInformation(): AndroidIdeSignatureInformation {
    val ideParams = this.parameters.map { it.toIdeParameterInformation() }
    val doc = this.documentation?.right?.value ?: "" // Assuming MarkupContent
    return AndroidIdeSignatureInformation(
        this.label,
        AndroidIdeMarkupContent(doc, AndroidIdeMarkupKind.MARKDOWN),
        ideParams
    )
}

fun ParameterInformation.toIdeParameterInformation(): AndroidIdeParameterInformation {
    val doc = this.documentation?.right?.value ?: "" 
    val labelString = this.label.left ?: "" 
    return AndroidIdeParameterInformation(
        labelString,
        AndroidIdeMarkupContent(doc, AndroidIdeMarkupKind.MARKDOWN)
    )
}

// Formatting Converters
fun AndroidIdeFormatCodeParams.toLsp4j(): DocumentFormattingParams {
    return DocumentFormattingParams(
        TextDocumentIdentifier("file:///format.kt"),
        FormattingOptions()
    ).apply {
        try {
            val contentField = this.javaClass.getDeclaredField("content")
            contentField.isAccessible = true
            contentField.set(this, this@toLsp4j.content.toString())
        } catch (e: Exception) {
            LOG.warn("Could not set content for formatting request via reflection", e)
        }
    }
}

fun List<TextEdit>.toIdeCodeFormatResult(originalContent: CharSequence): AndroidIdeCodeFormatResult {
    val fullDocumentEdit = this.firstOrNull()
    return if (fullDocumentEdit != null) {
        val indexedEdit = AndroidIdeIndexedTextEdit(0, originalContent.length, fullDocumentEdit.newText)
        AndroidIdeCodeFormatResult(isIndexed = true, indexedTextEdits = mutableListOf(indexedEdit))
    } else {
        AndroidIdeCodeFormatResult.NONE
    }
}

// Diagnostics Converters
fun PublishDiagnosticsParams.toIdeDiagnosticResult(): AndroidIdeDiagnosticResult {
    val path = Paths.get(parseURI(this.uri))
    val ideDiagnostics = this.diagnostics.map { it.toIdeDiagnosticItem() }
    return AndroidIdeDiagnosticResult(path, ideDiagnostics)
}

fun Diagnostic.toIdeDiagnosticItem(): AndroidIdeDiagnosticItem {
    return AndroidIdeDiagnosticItem(
        message = this.message,
        code = this.code?.left ?: "",
        range = this.range.toIdeRange(),
        source = this.source ?: "kotlin",
        severity = this.severity.toIdeSeverity()
    )
}

fun DiagnosticSeverity.toIdeSeverity(): AndroidIdeDiagnosticSeverity =
    when (this) {
        DiagnosticSeverity.Error -> AndroidIdeDiagnosticSeverity.ERROR
        DiagnosticSeverity.Warning -> AndroidIdeDiagnosticSeverity.WARNING
        DiagnosticSeverity.Information -> AndroidIdeDiagnosticSeverity.INFO
        DiagnosticSeverity.Hint -> AndroidIdeDiagnosticSeverity.HINT
    }