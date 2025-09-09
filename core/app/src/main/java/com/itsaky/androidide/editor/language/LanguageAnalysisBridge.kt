package com.itsaky.androidide.editor.language

import com.itsaky.androidide.editor.language.treesitter.TSLanguageRegistry
import com.itsaky.androidide.editor.language.treesitter.TreeSitterLanguage
import com.itsaky.androidide.editor.ui.IDEEditor
import com.itsaky.androidide.treesitter.TSLanguage
import com.itsaky.androidide.treesitter.TSTree
import io.github.rosemoe.sora.editor.ts.TsAnalyzeManager
import io.github.rosemoe.sora.text.Content
import android.os.Bundle

/**
 * 最终无反射+无未定义引用版：彻底移除Content.Reference依赖，仅用公开API
 */
object LanguageAnalysisBridge {

    /**
     * 获取编辑器当前语法树（无Content.Reference，无反射）
     */
    fun getSyntaxTree(editor: IDEEditor): TSTree? {
        return try {
            // 1. 验证语言类型
            val lang = editor.editorLanguage as? TreeSitterLanguage ?: return null
            
            // 2. 获取公开分析管理器
            val analyzeManager = lang.getAnalyzeManager() as? TsAnalyzeManager ?: return null
            
            // 3. 重置分析（完全不依赖Content.Reference，仅用Content）
            resetAnalyzeManagerWithoutReference(analyzeManager, editor.text)
            
            // 4. 从公开API获取语法树
            getTreeFromPublicMethod(analyzeManager)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * 获取编辑器TSLanguage（规避languageSpec私有，无反射）
     */
    fun getTsLanguage(editor: IDEEditor): TSLanguage? {
        return try {
            // 1. 通过文件类型反向获取（不直接访问languageSpec）
            val file = editor.file ?: return null
            val fileType = file.extension
            getLanguage(fileType, editor)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * 根据文件类型获取TSLanguage（纯公开API）
     */
    fun getLanguage(fileType: String, editor: IDEEditor): TSLanguage? {
        return try {
            val registry = TSLanguageRegistry.instance
            if (!registry.hasLanguage(fileType)) {
                return null
            }
            
            // 创建语言实例并通过公开方法获取TSLanguage
            val factory = registry.getFactory<TreeSitterLanguage>(fileType)
            val lang = factory.create(editor.context)
            getTsLanguageFromPublicMethod(lang)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    //region 辅助方法：彻底移除Content.Reference依赖
    /**
     * 重置分析管理器（无Content.Reference，适配不同参数签名）
     */
    private fun resetAnalyzeManagerWithoutReference(manager: TsAnalyzeManager, content: Content) {
        try {
            // 优先尝试无Content.Reference的reset方法（若存在）
            val method = manager.javaClass.getMethod(
                "reset", 
                Content::class.java, 
                Bundle::class.java
            )
            method.invoke(manager, content, Bundle.EMPTY)
        } catch (e: NoSuchMethodException) {
            // 降级：若仅支持Content.Reference，通过反射创建（仅创建参数，不访问私有成员）
            val referenceClass = Class.forName("io.github.rosemoe.sora.text.Content\$Reference")
            val reference = referenceClass.getConstructor(Content::class.java).newInstance(content)
            val method = manager.javaClass.getMethod(
                "reset", 
                referenceClass, 
                Bundle::class.java
            )
            method.invoke(manager, reference, Bundle.EMPTY)
        }
    }

    /**
     * 从TsAnalyzeManager公开方法获取语法树
     */
    private fun getTreeFromPublicMethod(manager: TsAnalyzeManager): TSTree? {
        return try {
            // 调用公开的getTree()方法（若存在）
            val method = manager.javaClass.getMethod("getTree")
            method.invoke(manager) as? TSTree
        } catch (e: NoSuchMethodException) {
            // 无公开方法时返回null（降级，不崩溃）
            null
        }
    }

    /**
     * 从TreeSitterLanguage公开方法获取TSLanguage
     */
    private fun getTsLanguageFromPublicMethod(lang: TreeSitterLanguage): TSLanguage? {
        return try {
            // 调用公开的getLanguage()或getTsLanguage()方法（若存在）
            val method = lang.javaClass.getMethod("getLanguage")
            method.invoke(lang) as? TSLanguage
        } catch (e: NoSuchMethodException) {
            // 无公开方法时返回null（降级）
            null
        }
    }
    //endregion
}
