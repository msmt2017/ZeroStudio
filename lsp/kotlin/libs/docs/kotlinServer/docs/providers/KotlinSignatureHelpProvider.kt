package android.zero.studio.lsp.kotlin.providers

import com.itsaky.androidide.lsp.models.SignatureHelpParams
import com.itsaky.androidide.lsp.models.SignatureHelp

/**
 * Kotlin 签名帮助服务 Provider。
 * 负责签名帮助，对接底层 shared/server 的签名帮助能力。
 */
class KotlinSignatureHelpProvider {
    /**
     * 查询签名帮助。
     * @param params 签名帮助参数
     * @return 签名帮助结果
     */
    suspend fun signatureHelp(params: SignatureHelpParams): SignatureHelp {
        // TODO: 调用 shared/server 的签名帮助 API
        return SignatureHelp(emptyList(), -1, -1)
    }
} 