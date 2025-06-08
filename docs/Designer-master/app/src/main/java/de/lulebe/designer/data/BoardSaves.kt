package de.lulebe.designer.data


object BoardSaves {
    private val listeners = mutableListOf<(id: Long) -> Unit>()

    fun addListener (l: (id: Long) -> Unit) = listeners.add(l)

    fun removeListener (l: (id: Long) -> Unit) = listeners.remove(l)

    fun boardSaved (id: String) {
        try {
            val lid = id.toLong()
            listeners.forEach { it(lid) }
        } catch (e: NumberFormatException) {}
    }
    fun boardSaved (id: Long) = listeners.forEach { it(id) }
}