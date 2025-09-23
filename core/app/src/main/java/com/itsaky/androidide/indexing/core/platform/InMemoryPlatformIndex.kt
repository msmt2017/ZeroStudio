package com.itsaky.androidide.indexing.core.platform

import com.itsaky.androidide.indexing.IIndex
import com.itsaky.androidide.indexing.IIndexable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap

/**
 * A simple, generic, in-memory implementation of an index.
 * This implementation is thread-safe.
 */
class InMemoryPlatformIndex<T : IPlatformIndexable>(
    override val name: String,
    override val path: String
) : IPlatformIndex<T> {

    private val log = LoggerFactory.getLogger(javaClass)
    private val indexStore = ConcurrentHashMap<String, T>()
    private val scope = CoroutineScope(Dispatchers.IO)

    // A unique key is needed for the map. We'll derive it from the indexable object itself.
    private fun getKey(symbol: T): String {
        // A more robust implementation would use a stable identifier from the symbol.
        return symbol.hashCode().toString()
    }

    override fun index(symbol: T) {
        indexStore[getKey(symbol)] = symbol
    }

    override fun indexAsync(symbol: T) {
        scope.launch {
            index(symbol)
        }
    }

    override fun indexAll(symbols: Collection<T>) {
        symbols.forEach { index(it) }
    }

    override fun indexAllAsync(symbols: Collection<T>) {
        scope.launch {
            indexAll(symbols)
        }
    }

    override fun delete() {
        indexStore.clear()
        log.info("In-memory index '{}' cleared.", name)
    }
}