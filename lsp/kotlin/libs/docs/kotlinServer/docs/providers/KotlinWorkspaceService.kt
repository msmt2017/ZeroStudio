package android.zero.studio.lsp.kotlin.providers

import com.itsaky.androidide.lsp.models.WorkspaceSymbolParams
import com.itsaky.androidide.lsp.models.WorkspaceSymbolResult

/**
 * Kotlin 工作区服务 Provider。
 * 负责工作区符号、文件变更等，对接底层 shared/server 的工作区能力。
 */
class KotlinWorkspaceService {
    /**
     * 查询工作区符号。
     * @param params 工作区符号参数
     * @return 工作区符号结果
     */
    suspend fun workspaceSymbols(params: WorkspaceSymbolParams): WorkspaceSymbolResult {
        // TODO: 调用 shared/server 的工作区符号 API
        return WorkspaceSymbolResult(emptyList())
    }
} 