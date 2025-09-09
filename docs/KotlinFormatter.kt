package com.itsaky.androidide.formatprovider

import com.pinterest.ktlint.rule.engine.api.Code
import com.pinterest.ktlint.rule.engine.api.EditorConfigOverride
import com.pinterest.ktlint.rule.engine.api.KtLintRuleEngine
import com.pinterest.ktlint.rule.engine.core.api.editorconfig.INDENT_SIZE_PROPERTY
import com.pinterest.ktlint.rule.engine.core.api.editorconfig.INDENT_STYLE_PROPERTY
import com.pinterest.ktlint.rule.engine.core.api.editorconfig.MAX_LINE_LENGTH_PROPERTY
import com.pinterest.ktlint.ruleset.standard.StandardRuleSetProvider
import org.ec4j.core.model.PropertyType

/**
 * 一个强大且健壮的 Kotlin (.kt) 和 Kotlin Script (.kts) 文件代码格式化器。
 *
 * 此格式化器基于现代的 `ktlint-rule-engine` 核心（版本 1.x API），
 * 利用 KtLint 的标准规则集，提供符合官方 Kotlin 风格指南的格式化功能。
 * 它通过类型安全的 EditorConfig 覆盖机制进行配置，确保了稳定性和精确性。
 *
 * @param options 格式化选项，用于动态配置 KtLint 引擎的行为。
 */
class KotlinFormatter(
    private val options: KotlinFormatOptions = KotlinFormatOptions()
) : CodeFormatter {

    /**
     * KtLint 规则引擎实例。
     * 这是一个相对重量级的对象，因此使用 lazy 初始化，仅在首次需要时创建。
     * 它被配置为使用标准规则集和用户提供的格式化选项。
     */
    private val ktLintRuleEngine: KtLintRuleEngine by lazy {
        // 使用 EditorConfigOverride 来以类型安全的方式传递格式化选项。
        // 这取代了旧 API 中的 userData Map<String, String>。
        val editorConfigOverride = EditorConfigOverride.from(
            INDENT_STYLE_PROPERTY to if (options.useTabs) PropertyType.IndentStyleValue.tab else PropertyType.IndentStyleValue.space,
            INDENT_SIZE_PROPERTY to options.indentSize,
            MAX_LINE_LENGTH_PROPERTY to options.maxLineLength
        )

        KtLintRuleEngine(
            // 从 StandardRuleSetProvider 获取所有标准规则的 RuleProvider。
            // 这是现代 API 的标准做法。
            ruleProviders = StandardRuleSetProvider().getRuleProviders(),
            editorConfigOverride = editorConfigOverride
        )
    }

    /**
     * 格式化给定的 Kotlin 源代码字符串。
     *
     * @param source 要格式化的源代码。
     * @return 格式化后的代码字符串。如果格式化过程中发生严重错误，则返回原始源代码。
     */
    override fun format(source: String): String {
        if (source.isBlank()) {
            return source
        }

        return try {
            // 使用 Code.fromSnippet 创建一个代码对象。
            // 它可以区分普通 Kotlin 文件和脚本文件 (.kts)。
            val code = Code.fromSnippet(
                content = source,
                script = options.isKtsFile
            )

            // 调用 KtLintRuleEngine 的 format 方法。
            // 这个方法会应用所有规则并自动修正可修复的违规。
            ktLintRuleEngine.format(code)
        } catch (e: Exception) {
            // 捕获 KtLint 在解析或格式化过程中可能抛出的任何异常，
            // 例如 KtLintParseException（语法错误）或 KtLintRuleException（规则执行错误）。
            System.err.println("ktlint formatting failed, returning original source. Reason: ${e.javaClass.simpleName} - ${e.message}")
            e.printStackTrace()
            source
        }
    }
}

/**
 * 用于 Kotlin 格式化的数据类选项。
 *
 * 这些选项与 ktlint 的核心 EditorConfig 属性相对应，提供了类型安全的配置方式。
 *
 * @param indentSize 缩进大小。
 * @param useTabs 是否使用制表符（Tab）进行缩进，如果为 false，则使用空格。
 * @param maxLineLength 每行的最大长度。
 * @param isKtsFile 文件是否为 Kotlin 脚本 (.kts)。
 */
data class KotlinFormatOptions(
    val indentSize: Int = 4,
    val useTabs: Boolean = false,
    val maxLineLength: Int = 120,
    val isKtsFile: Boolean = false
)
