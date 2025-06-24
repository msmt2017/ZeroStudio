package android.zero.studio.lsp.kotlin.adapters

import com.itsaky.androidide.lsp.models.DefinitionResult
import org.eclipse.lsp4j.Location
import org.eclipse.lsp4j.LocationLink

class DefinitionAdapter {
    fun toAndroidIdeDefinitionResult(lspResult: Any): DefinitionResult {
        val locations = when (lspResult) {
            is List<*> -> lspResult.filterIsInstance<Location>()
            is Location -> listOf(lspResult)
            is org.eclipse.lsp4j.Either<*, *> -> {
                if (lspResult.isLeft) lspResult.left as? List<Location> ?: emptyList()
                else lspResult.right as? List<LocationLink> ?: emptyList()
            }
            else -> emptyList()
        }
        // TODO: 适配为 IDE 的 Location 类型
        return DefinitionResult(emptyList())
    }
} 