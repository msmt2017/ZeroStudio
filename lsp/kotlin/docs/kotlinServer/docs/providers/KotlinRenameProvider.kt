package android.zero.studio.lsp.kotlin.providers

import com.itsaky.androidide.lsp.models.RenameParams
import com.itsaky.androidide.lsp.models.RenameResult

/**
 * Kotlin 重命名服务 Provider。
 * 负责符号重命名，对接底层 shared/server 的重命名能力。
 */
class KotlinRenameProvider {
    /**
     * 执行重命名操作。
     * @param params 重命名参数
     * @return 重命名结果
     */
    suspend fun rename(params: RenameParams): RenameResult {
        // TODO: 调用 shared/server 的重命名 API
        return RenameResult.EMPTY
    }
} 