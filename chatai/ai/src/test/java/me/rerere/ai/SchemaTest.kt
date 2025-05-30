package me.rerere.ai

import kotlinx.serialization.json.Json
import me.rerere.ai.core.SchemaBuilder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SchemaTest {
    private val json = Json { ignoreUnknownKeys = true }
    private val userSchema = SchemaBuilder.obj(
        "name" to SchemaBuilder.str(minLength = 2, maxLength = 50),
        "age" to SchemaBuilder.int(min = 0, max = 120),
        "email" to SchemaBuilder.str(pattern = "^[\\w-\\.]+@([\\w-]+\\.)+[\\w-]{2,4}$"),
        "tags" to SchemaBuilder.arr(SchemaBuilder.str(), unique = true),
        "address" to SchemaBuilder.obj(
            "city" to SchemaBuilder.str(),
            "zipcode" to SchemaBuilder.str(pattern = "\\d{5}")
        )
    )

    @Test
    fun `valid user json should pass validation`() {
        val validJson = json.parseToJsonElement(
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
        val result = userSchema.validate(validJson)
        assertTrue("Expected validation to succeed", result.isValid)
    }

    @Test
    fun `invalid name should fail validation`() {
        val invalidJson = json.parseToJsonElement(
            """
            {
              "name": "A",
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
        val result = userSchema.validate(invalidJson)
        assertFalse(result.isValid)
        assertEquals("Property 'name': String length 1 is less than minimum 2", result.error)
    }

    @Test
    fun `invalid email format should fail validation`() {
        val invalidJson = json.parseToJsonElement(
            """
            {
              "name": "Alice",
              "age": 30,
              "email": "not-an-email",
              "tags": ["developer", "kotlin"],
              "address": {
                "city": "New York",
                "zipcode": "10001"
              }
            }
        """.trimIndent()
        )
        val result = userSchema.validate(invalidJson)
        assertFalse(result.isValid)
        assertTrue(result.error?.contains("email") == true)
    }

    @Test
    fun `duplicate tags should fail validation`() {
        val invalidJson = json.parseToJsonElement(
            """
            {
              "name": "Alice",
              "age": 30,
              "email": "alice@example.com",
              "tags": ["kotlin", "kotlin"],
              "address": {
                "city": "New York",
                "zipcode": "10001"
              }
            }
        """.trimIndent()
        )
        val result = userSchema.validate(invalidJson)
        assertFalse(result.isValid)
        assertTrue(result.error?.contains("must be unique") == true)
    }

    @Test
    fun `zipcode does not match pattern`() {
        val invalidJson = json.parseToJsonElement(
            """
            {
              "name": "Alice",
              "age": 30,
              "email": "alice@example.com",
              "tags": ["kotlin", "java"],
              "address": {
                "city": "New York",
                "zipcode": "1234"
              }
            }
        """.trimIndent()
        )
        val result = userSchema.validate(invalidJson)
        assertFalse(result.isValid)
        assertTrue(result.error?.contains("zipcode") == true)
    }
}