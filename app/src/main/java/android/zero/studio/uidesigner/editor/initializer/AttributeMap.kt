package android.zero.studio.uidesigner.editor.initializer

class AttributeMap {
    private val attrs: MutableList<Attribute> = ArrayList()

    /**
     * Puts a key-value pair into the AttributeMap.
     * If the key already exists, it updates the value.
     */
    fun putValue(key: String, value: String) {
        val index = getAttributeIndexFromKey(key)
        if (index != -1) {
            attrs[index].value = value
        } else {
            attrs.add(Attribute(key, value))
        }
    }

    /**
     * Removes a key-value pair from the AttributeMap if it exists.
     */
    fun removeValue(key: String) {
        val index = getAttributeIndexFromKey(key)

        if (index != -1) {
            attrs.removeAt(index)
        }
    }

    /**
     * Safely gets the value associated with the given key.
     *
     * @return the value of the attribute, or null if the key doesn't exist.
     */
    fun getValueOrNull(key: String): String? {
        val index = getAttributeIndexFromKey(key)
        return if (index != -1) attrs[index].value else null
    }
    /**
     * Gets the value associated with the given key in the AttributeMap
     *
     * @param key the key of the attribute
     * @return the value of the attribute
     */
    fun getValue(key: String): String {
        val index = getAttributeIndexFromKey(key)
        val attr = attrs[index]
        return attr.value
    }

    /**
     * Gets a list of all the keys in the AttributeMap.
     */
    fun keySet(): List<String> {
        return attrs.map { it.key }
    }

    /**
     * Gets a list of all the values in the AttributeMap.
     */
    fun values(): List<String> {
        return attrs.map { it.value }
    }

    /**
     * Checks if the AttributeMap contains a key.
     */
    fun contains(key: String): Boolean {
        return getAttributeIndexFromKey(key) != -1
    }

    /**
     * Gets the index of the Attribute with the given key.
     * @return the index of the Attribute, or -1 if not found.
     */
    private fun getAttributeIndexFromKey(key: String): Int {
        return attrs.indexOfFirst { it.key == key }
    }

    private data class Attribute(val key: String, var value: String)
}