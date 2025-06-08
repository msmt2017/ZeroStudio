package me.gulya.gradle.mcp

import io.modelcontextprotocol.kotlin.sdk.Tool
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

// Builds the overall input schema
class InputSchemaBuilder {
    private val properties = mutableMapOf<String, JsonObject>()
    private val required = mutableListOf<String>()

    /** Defines an optional property with a name and its attributes */
    fun optionalProperty(name: String, block: PropertyBuilder.() -> Unit) {
        val propertyBuilder = PropertyBuilder()
        propertyBuilder.block()
        properties[name] = propertyBuilder.build()
    }

    /** Defines a required property with a name and its attributes */
    fun requiredProperty(name: String, block: PropertyBuilder.() -> Unit) {
        optionalProperty(name, block)
        required.add(name)
    }

    /** Constructs the final Tool.Input object */
    fun build(): Tool.Input {
        return Tool.Input(
            properties = JsonObject(properties),
            required = required.toList()
        )
    }
}

// Builds individual property attributes
class PropertyBuilder {
    private val attributes = mutableMapOf<String, JsonElement>()

    /** Sets the type of the property (e.g., "string", "integer") */
    fun type(value: String) {
        attributes["type"] = JsonPrimitive(value)
    }

    /** Sets the description of the property */
    fun description(value: String) {
        attributes["description"] = JsonPrimitive(value)
    }

    /** Sets an arbitrary JSON Schema attribute */
    fun attribute(key: String, value: JsonElement) {
        attributes[key] = value
    }

    /** Defines a nested object schema for the property */
    fun objectSchema(block: InputSchemaBuilder.() -> Unit) {
        val nestedBuilder = InputSchemaBuilder()
        nestedBuilder.block()
        val nestedSchema = nestedBuilder.build()
        attributes["type"] = JsonPrimitive("object")
        attributes["properties"] = nestedSchema.properties
        nestedSchema.required?.let {
            attributes["required"] = JsonArray(it.map { JsonPrimitive(it) })
        }
    }

    /** Defines an array schema with a single item type */
    fun arraySchema(block: PropertyBuilder.() -> Unit) {
        val itemBuilder = PropertyBuilder()
        itemBuilder.block()
        val itemSchema = itemBuilder.build()
        attributes["type"] = JsonPrimitive("array")
        attributes["items"] = itemSchema
    }

    /** Constructs the JsonObject for a single property */
    fun build(): JsonObject {
        return JsonObject(attributes)
    }
}

/** Top-level DSL function to create a Tool.Input schema */
fun inputSchema(block: InputSchemaBuilder.() -> Unit): Tool.Input {
    val builder = InputSchemaBuilder()
    builder.block()
    return builder.build()
}
