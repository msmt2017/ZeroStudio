// 文件路径：app/src/main/java/android/zero/mcp/ContextManager.kt
package android.zero.mcp

import java.util.concurrent.ConcurrentHashMap

/**
 * 会话上下文管理：保存并检索每个 contextId 对应的键值对。
 */
class ContextManager {

    private val store = ConcurrentHashMap<String, MutableMap<String, Any>>()

    /** 创建新上下文，返回 contextId */
    fun createContext(): String {
        val id = java.util.UUID.randomUUID().toString()
        store[id] = mutableMapOf()
        return id
    }

    /** 获取上下文 Map，对应 contextId */
    fun getContext(contextId: String?): MutableMap<String, Any>? {
        if (contextId == null) return null
        return store[contextId]
    }

    /** 更新上下文键值 */
    fun put(contextId: String, key: String, value: Any) {
        store[contextId]?.put(key, value)
    }

    /** 删除上下文 */
    fun removeContext(contextId: String) {
        store.remove(contextId)
    }
}
