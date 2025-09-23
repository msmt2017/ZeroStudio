package com.itsaky.androidide.lsp.models

import com.itsaky.androidide.lsp.CancellableRequestParams
import com.itsaky.androidide.models.Position
import com.itsaky.androidide.models.Range
import com.itsaky.androidide.progress.ICancelChecker
import java.nio.file.Path

/**
 * Parameters for a hover request.
 */
data class HoverParams(
    val file: Path,
    val position: Position,
    override val cancelChecker: ICancelChecker
) : CancellableRequestParams

/**
 * Result of a hover request.
 */
data class HoverResult(
    val contents: List<MarkupContent>,
    val range: Range? = null
)