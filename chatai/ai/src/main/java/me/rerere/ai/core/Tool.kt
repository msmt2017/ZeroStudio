package me.rerere.ai.core

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.longOrNull

@Serializable
data class Tool(
    val name: String,
    val description: String,
    val parameters: Schema,
    val execute: suspend (JsonElement) -> JsonElement
)

@Serializable
sealed class Schema {
    /**
     * 验证给定的 JsonElement 是否符合当前 Schema
     * @param json 要验证的 JSON 元素
     * @return 验证结果，包含是否有效以及可能的错误信息
     */
    abstract fun validate(json: JsonElement): ValidationResult

    /**
     * 对象类型 Schema
     */
    @Serializable
    @SerialName("object")
    data class ObjectSchema(
        val properties: Map<String, Schema>,
        val required: List<String> = emptyList(),
    ) : Schema() {
        override fun validate(json: JsonElement): ValidationResult {
            if (json !is JsonObject) {
                return ValidationResult.failure("Expected object, but got ${json::class.simpleName}")
            }
            // 验证必填字段
            for (requiredProp in required) {
                if (!json.containsKey(requiredProp)) {
                    return ValidationResult.failure("Missing required property: $requiredProp")
                }
            }
            // 验证每个属性
            for ((propName, propValue) in json) {
                val propSchema = properties[propName] ?: continue
                val propResult = propSchema.validate(propValue)
                if (!propResult.isValid) {
                    return ValidationResult.failure("Property '$propName': ${propResult.error}")
                }
            }
            return ValidationResult.success()
        }
    }

    /**
     * 数组类型 Schema
     */
    @Serializable
    @SerialName("array")
    data class ArraySchema(
        val items: Schema,
        val minItems: Int = 0,
        val maxItems: Int? = null,
        val uniqueItems: Boolean = false
    ) : Schema() {
        override fun validate(json: JsonElement): ValidationResult {
            if (json !is JsonArray) {
                return ValidationResult.failure("Expected array, but got ${json::class.simpleName}")
            }
            // 验证数组长度
            if (json.size < minItems) {
                return ValidationResult.failure("Array size ${json.size} is less than minimum $minItems")
            }

            if (maxItems != null && json.size > maxItems) {
                return ValidationResult.failure("Array size ${json.size} is greater than maximum $maxItems")
            }
            // 验证唯一性
            if (uniqueItems && json.size != json.distinct().count()) {
                return ValidationResult.failure("Array items must be unique")
            }
            // 验证每个元素
            for ((index, item) in json.withIndex()) {
                val itemResult = items.validate(item)
                if (!itemResult.isValid) {
                    return ValidationResult.failure("Item at index $index: ${itemResult.error}")
                }
            }
            return ValidationResult.success()
        }
    }

    /**
     * 字符串类型 Schema
     */
    @Serializable
    @SerialName("string")
    data class StringSchema(
        val minLength: Int = 0,
        val maxLength: Int? = null,
        val pattern: String? = null
    ) : Schema() {
        private val regex: Regex? get() = pattern?.let { Regex(it) }

        override fun validate(json: JsonElement): ValidationResult {
            if (json !is JsonPrimitive || !json.isString) {
                return ValidationResult.failure("Expected string, but got ${json::class.simpleName}")
            }
            val value = json.content

            if (value.length < minLength) {
                return ValidationResult.failure("String length ${value.length} is less than minimum $minLength")
            }

            if (maxLength != null && value.length > maxLength) {
                return ValidationResult.failure("String length ${value.length} is greater than maximum $maxLength")
            }

            if (regex != null && !regex!!.matches(value)) {
                return ValidationResult.failure("String does not match pattern: $pattern")
            }

            return ValidationResult.success()
        }
    }

    /**
     * 数值类型 Schema
     */
    @Serializable
    @SerialName("number")
    data class NumberSchema(
        val minimum: Double? = null,
        val maximum: Double? = null,
        val exclusiveMinimum: Boolean = false,
        val exclusiveMaximum: Boolean = false,
        val multipleOf: Double? = null
    ) : Schema() {
        override fun validate(json: JsonElement): ValidationResult {
            if (json !is JsonPrimitive || !json.isString && json.booleanOrNull != null) {
                return ValidationResult.failure("Expected number, but got ${json::class.simpleName}")
            }
            val value = json.doubleOrNull
                ?: return ValidationResult.failure("Cannot parse as number: $json")

            minimum?.let {
                if (exclusiveMinimum && value <= it) {
                    return ValidationResult.failure("Value $value must be greater than $it")
                } else if (!exclusiveMinimum && value < it) {
                    return ValidationResult.failure("Value $value must be greater than or equal to $it")
                }
            }

            maximum?.let {
                if (exclusiveMaximum && value >= it) {
                    return ValidationResult.failure("Value $value must be less than $it")
                } else if (!exclusiveMaximum && value > it) {
                    return ValidationResult.failure("Value $value must be less than or equal to $it")
                }
            }

            multipleOf?.let {
                if (value % it != 0.0) {
                    return ValidationResult.failure("Value $value is not a multiple of $it")
                }
            }

            return ValidationResult.success()
        }
    }

    /**
     * 整数类型 Schema
     */
    @Serializable
    @SerialName("integer")
    data class IntegerSchema(
        val minimum: Long? = null,
        val maximum: Long? = null,
    ) : Schema() {
        override fun validate(json: JsonElement): ValidationResult {
            if (json !is JsonPrimitive || !json.isString && json.booleanOrNull != null) {
                return ValidationResult.failure("Expected integer, but got ${json::class.simpleName}")
            }
            val doubleValue = json.doubleOrNull
                ?: return ValidationResult.failure("Cannot parse as number: $json")

            val value = json.longOrNull
                ?: return ValidationResult.failure("Value $doubleValue is not an integer")

            minimum?.let {
                if (value <= it) {
                    return ValidationResult.failure("Value $value must be greater than $it")
                }
            }

            maximum?.let {
                if (value >= it) {
                    return ValidationResult.failure("Value $value must be less than $it")
                }
            }

            return ValidationResult.success()
        }
    }

    /**
     * 布尔类型 Schema
     */
    @Serializable
    @SerialName("boolean")
    object BooleanSchema : Schema() {
        override fun validate(json: JsonElement): ValidationResult {
            if (json !is JsonPrimitive || json.booleanOrNull == null) {
                return ValidationResult.failure("Expected boolean, but got ${json::class.simpleName}")
            }
            return ValidationResult.success()
        }
    }

    /**
     * null 类型 Schema
     */
    @Serializable
    @SerialName("null")
    object NullSchema : Schema() {
        override fun validate(json: JsonElement): ValidationResult {
            if (json !is JsonNull) {
                return ValidationResult.failure("Expected null, but got ${json::class.simpleName}")
            }
            return ValidationResult.success()
        }
    }

    /**
     * 枚举类型 Schema
     */
    @Serializable
    @SerialName("enum")
    data class EnumSchema(
        val values: List<JsonElement>
    ) : Schema() {
        override fun validate(json: JsonElement): ValidationResult {
            if (json !in values) {
                return ValidationResult.failure("Value $json is not in enum: $values")
            }
            return ValidationResult.success()
        }
    }

    /**
     * 组合类型 Schema - anyOf (满足任一 Schema)
     */
    @Serializable
    @SerialName("anyOf")
    data class AnyOfSchema(
        val schemas: List<Schema>
    ) : Schema() {
        override fun validate(json: JsonElement): ValidationResult {
            val errors = mutableListOf<String>()

            for (schema in schemas) {
                val result = schema.validate(json)
                if (result.isValid) {
                    return ValidationResult.success()
                }
                errors.add(result.error ?: "Unknown error")
            }

            return ValidationResult.failure(
                "Value does not match any schema: ${
                    errors.joinToString(
                        "; "
                    )
                }"
            )
        }
    }

    /**
     * 组合类型 Schema - allOf (满足所有 Schema)
     */
    @Serializable
    @SerialName("allOf")
    data class AllOfSchema(
        val schemas: List<Schema>
    ) : Schema() {
        override fun validate(json: JsonElement): ValidationResult {
            for (schema in schemas) {
                val result = schema.validate(json)
                if (!result.isValid) {
                    return result
                }
            }
            return ValidationResult.success()
        }
    }
}

/**
 * 验证结果类
 */
data class ValidationResult(
    val isValid: Boolean,
    val error: String? = null
) {
    companion object {
        fun success() = ValidationResult(true)
        fun failure(error: String) = ValidationResult(false, error)
    }
}

/**
 * Schema 构建辅助函数
 */
object SchemaBuilder {
    fun obj(
        vararg properties: Pair<String, Schema>,
        required: List<String> = emptyList(),
    ) =
        Schema.ObjectSchema(properties.toMap(), required)

    fun arr(items: Schema, minItems: Int = 0, maxItems: Int? = null, unique: Boolean = false) =
        Schema.ArraySchema(items, minItems, maxItems, unique)

    fun str(minLength: Int = 0, maxLength: Int? = null, pattern: String? = null) =
        Schema.StringSchema(minLength, maxLength, pattern)

    fun num(
        min: Double? = null,
        max: Double? = null,
        exclusiveMin: Boolean = false,
        exclusiveMax: Boolean = false,
        multipleOf: Double? = null
    ) =
        Schema.NumberSchema(min, max, exclusiveMin, exclusiveMax, multipleOf)

    fun int(
        min: Long? = null,
        max: Long? = null,
    ) = Schema.IntegerSchema(min, max)

    val boolean = Schema.BooleanSchema

    val nullValue = Schema.NullSchema

    fun enum(vararg values: JsonElement) = Schema.EnumSchema(values.toList())

    fun anyOf(vararg schemas: Schema) = Schema.AnyOfSchema(schemas.toList())

    fun allOf(vararg schemas: Schema) = Schema.AllOfSchema(schemas.toList())
}

// 使用示例
fun main() {
    val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
    }

    // 定义一个用户 Schema
    val userSchema = SchemaBuilder.obj(
        "name" to SchemaBuilder.str(minLength = 2, maxLength = 50),
        "age" to SchemaBuilder.int(min = 0, max = 120),
        "email" to SchemaBuilder.str(pattern = "^[\\w-\\.]+@([\\w-]+\\.)+[\\w-]{2,4}$"),
        "tags" to SchemaBuilder.arr(SchemaBuilder.str(), unique = true),
        "address" to SchemaBuilder.obj(
            "city" to SchemaBuilder.str(),
            "zipcode" to SchemaBuilder.str(pattern = "\\d{5}")
        )
    )

    // 有效的用户 JSON
    val validUserJson = json.parseToJsonElement(
        """
        {
          "name": "Alice",
          "age": 30,
          "email": "alice@example.com",
          "tags": ["developer", "kotlin"],
          "address": {
            "city": "New York",
            "zipcode": "10001"
          }
        }
    """.trimIndent()
    )

    // 无效的用户 JSON
    val invalidUserJson = json.parseToJsonElement(
        """
        {
          "name": "B",
          "age": 150,
          "email": "not-an-email",
          "tags": ["kotlin", "kotlin"],
          "address": {
            "city": "Los Angeles",
            "zipcode": "9000"
          }
        }
    """.trimIndent()
    )

    // 验证
    val validResult = userSchema.validate(validUserJson)
    val invalidResult = userSchema.validate(invalidUserJson)

    println(json.encodeToString<Schema>(userSchema))

    println("Valid user validation: ${validResult.isValid}")
    println("Invalid user validation: ${invalidResult.isValid}")
    println("Error: ${invalidResult.error}")
}