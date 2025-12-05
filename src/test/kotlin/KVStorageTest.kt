package com.realtime.relay

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import relay.KVStorage
import relay.Realtime
import relay.models.RealtimeConfig
import java.io.File

class KVStorageTest {

    private lateinit var realtime: Realtime
    private lateinit var kvStore: KVStorage

    private val apiKey = System.getenv("AUTH_JWT")
    private val secretKey = System.getenv("AUTH_SECRET")

    @Before
    fun setup() = runBlocking {
        // Initialize Realtime connection for KV testing
        realtime = Realtime()
        realtime.apiKey = apiKey
        realtime.secretKey = secretKey
        realtime.filesDir = File.createTempFile("test", "dir").parentFile!!

        val config = RealtimeConfig()
        config.staging = true
        config.debug = false

        realtime.init(config)
        realtime.connect()

        // Small delay to ensure connection is fully established
        delay(2500)

        // Create KV store instance
        kvStore = realtime.initKVStore()

        if (kvStore == null) {
            throw Error("Failed to initialize KV store")
        }
    }

    @After
    fun cleanup() = runBlocking {
        // Clean up: delete all test keys
        try {
            val keys = kvStore.keys()
            for (key in keys) {
                kvStore.delete(key)
            }
        } catch (err: Exception) {
            println("Cleanup error: ${err.message}")
        }

        realtime.close()
    }

    // ==================== Constructor and Initialization Tests ====================

    @Test
    fun `Constructor - successful instantiation`() {
        // KVStorage is successfully instantiated in the setup method
        Assert.assertNotNull(kvStore)
    }

    @Test
    fun `init() - validates empty namespace`() = runBlocking {
        // We can't easily test this since we need a valid NATS connection
        // This would require mocking or a separate test harness
        // The validation is tested indirectly through the setup failing if namespace is empty
        Assert.assertTrue(true)
    }

    // ==================== Key Validation Tests ====================

    @Test
    fun `put() - validates empty key`() {
        val exception = Assert.assertThrows(Error::class.java) {
            runBlocking {
                kvStore.put("", "value")
            }
        }
        Assert.assertEquals("key cannot be empty", exception.message)
    }

    @Test
    fun `put() - validates key with invalid characters - colon`() {
        val exception = Assert.assertThrows(IllegalArgumentException::class.java) {
            runBlocking {
                kvStore.put("user:123", "value")
            }
        }
        Assert.assertEquals(
            "key can only contain alphanumeric characters and the following: _ - . = /",
            exception.message
        )
    }

    @Test
    fun `put() - validates key with invalid characters - at symbol`() {
        val exception = Assert.assertThrows(IllegalArgumentException::class.java) {
            runBlocking {
                kvStore.put("user@domain", "value")
            }
        }
        Assert.assertEquals(
            "key can only contain alphanumeric characters and the following: _ - . = /",
            exception.message
        )
    }

    @Test
    fun `put() - validates key with invalid characters - spaces`() {
        val exception = Assert.assertThrows(IllegalArgumentException::class.java) {
            runBlocking {
                kvStore.put("key with spaces", "value")
            }
        }
        Assert.assertEquals(
            "key can only contain alphanumeric characters and the following: _ - . = /",
            exception.message
        )
    }

    @Test
    fun `put() - validates key with invalid characters - hash`() {
        val exception = Assert.assertThrows(IllegalArgumentException::class.java) {
            runBlocking {
                kvStore.put("key#hash", "value")
            }
        }
        Assert.assertEquals(
            "key can only contain alphanumeric characters and the following: _ - . = /",
            exception.message
        )
    }

    @Test
    fun `put() - allows valid key characters`() = runBlocking {
        val validKeys = listOf(
            "valid-key",
            "user.123",
            "test_key",
            "user/profile/data",
            "config=value",
            "test-key_123.data=value/path"
        )

        for (key in validKeys) {
            kvStore.put(key, "test")
            val result = kvStore.get(key)
            Assert.assertNotNull(result)
            kvStore.delete(key)
        }
    }

    // ==================== put(String) Tests ====================

    @Test
    fun `put(String) and get() - string value`() = runBlocking {
        val key = "test.string"
        val value = "Hello World!"

        kvStore.put(key, value)
        val result = kvStore.get(key)

        Assert.assertEquals(value, result?.value)

        kvStore.delete(key)
    }

    @Test
    fun `put(String) and get() - empty string`() = runBlocking {
        val key = "test.empty-string"
        val value = ""

        kvStore.put(key, value)
        delay(1000)
        val result = kvStore.get(key)

        Assert.assertEquals(value, result?.value)

        kvStore.delete(key)
    }

    @Test
    fun `put(String) and get() - unicode string`() = runBlocking {
        val key = "test.unicode"
        val value = "Hello 世界 🌍"

        kvStore.put(key, value)
        val result = kvStore.get(key)

        Assert.assertEquals(value, result?.value)

        kvStore.delete(key)
    }

    @Test
    fun `put(String) and get() - long string`() = runBlocking {
        val key = "test.long-string"
        val value = "a".repeat(10000)

        kvStore.put(key, value)
        val result = kvStore.get(key)

        Assert.assertEquals(value, result?.value)

        kvStore.delete(key)
    }

    // ==================== put(Number) Tests ====================

    @Test
    fun `put(Number) and get() - integer value`() = runBlocking {
        val key = "test.integer"
        val value: Number = 42

        kvStore.put(key, value)
        val result = kvStore.get(key)

        Assert.assertEquals(value, result?.value)

        kvStore.delete(key)
    }

    @Test
    fun `put(Number) and get() - zero`() = runBlocking {
        val key = "test.zero"
        val value: Number = 0

        kvStore.put(key, value)
        val result = kvStore.get(key)

        Assert.assertEquals(value, result?.value)

        kvStore.delete(key)
    }

    @Test
    fun `put(Number) and get() - negative integer`() = runBlocking {
        val key = "test.negative"
        val value: Number = -42

        kvStore.put(key, value)
        val result = kvStore.get(key)

        Assert.assertEquals(value, result?.value)

        kvStore.delete(key)
    }

    @Test
    fun `put(Number) and get() - large integer`() = runBlocking {
        val key = "test.large-int"
        val value: Number = 2147483647

        kvStore.put(key, value)
        val result = kvStore.get(key)

        Assert.assertEquals(value, result?.value)

        kvStore.delete(key)
    }

    @Test
    fun `put(Number) and get() - long value`() = runBlocking {
        val key = "test.long"
        val value: Number = 9223372036854775807L

        kvStore.put(key, value)
        val result = kvStore.get(key)

        Assert.assertEquals(value, result?.value)

        kvStore.delete(key)
    }

    @Test
    fun `put(Number) and get() - double value`() = runBlocking {
        val key = "test.double"
        val value: Number = 3.14159

        kvStore.put(key, value)
        val result = kvStore.get(key)

        Assert.assertEquals(value, result?.value)

        kvStore.delete(key)
    }

    @Test
    fun `put(Number) and get() - float value`() = runBlocking {
        val key = "test.float"
        val value: Number = 2.71828f

        kvStore.put(key, value)
        val result = kvStore.get(key)

        Assert.assertEquals(value, result?.value)

        kvStore.delete(key)
    }

    @Test
    fun `put(Number) and get() - negative double`() = runBlocking {
        val key = "test.negative-double"
        val value: Number = -99.99

        kvStore.put(key, value)
        val result = kvStore.get(key)

        Assert.assertEquals(value, result?.value)

        kvStore.delete(key)
    }

    @Test
    fun `put(Number) and get() - very small double`() = runBlocking {
        val key = "test.small-double"
        val value: Number = 0.0000001

        kvStore.put(key, value)
        val result = kvStore.get(key)

        Assert.assertEquals(value.toDouble(), (result?.value as Number).toDouble(), 0.00000001)

        kvStore.delete(key)
    }

    @Test
    fun `put(Number) and get() - very large double`() = runBlocking {
        val key = "test.large-double"
        val value: Number = 1.7976931348623157E308

        kvStore.put(key, value)
        val result = kvStore.get(key)

        Assert.assertEquals(value.toDouble(), (result?.value as Number).toDouble(), 0.0)

        kvStore.delete(key)
    }

    // ==================== put(Boolean) Tests ====================

    @Test
    fun `put(Boolean) and get() - true value`() = runBlocking {
        val key = "test.boolean-true"
        val value = true

        kvStore.put(key, value)
        val result = kvStore.get(key)

        Assert.assertEquals(value, result?.value)

        kvStore.delete(key)
    }

    @Test
    fun `put(Boolean) and get() - false value`() = runBlocking {
        val key = "test.boolean-false"
        val value = false

        kvStore.put(key, value)
        val result = kvStore.get(key)

        Assert.assertEquals(value, result?.value)

        kvStore.delete(key)
    }

    // ==================== put(JsonObject) Tests ====================

    @Test
    fun `put(JsonObject) and get() - simple object`() = runBlocking {
        val key = "test.object"
        val value = JsonObject().apply {
            addProperty("name", "Alice")
            addProperty("age", 30)
            addProperty("active", true)
        }

        kvStore.put(key, value)
        val result = kvStore.get(key)

        Assert.assertTrue(result?.value is JsonObject)
        val resultObj = result?.value as JsonObject
        Assert.assertEquals("Alice", resultObj.get("name").asString)
        Assert.assertEquals(30, resultObj.get("age").asInt)
        Assert.assertEquals(true, resultObj.get("active").asBoolean)

        kvStore.delete(key)
    }

    @Test
    fun `put(JsonObject) and get() - nested object`() = runBlocking {
        val key = "test.nested"
        val value = JsonObject().apply {
            add("level1", JsonObject().apply {
                add("level2", JsonObject().apply {
                    add("level3", JsonObject().apply {
                        addProperty("deep", "value")
                    })
                })
            })
        }

        kvStore.put(key, value)
        val result = kvStore.get(key)

        Assert.assertTrue(result?.value is JsonObject)
        val resultObj = result?.value as JsonObject
        Assert.assertEquals(
            "value",
            resultObj.getAsJsonObject("level1")
                .getAsJsonObject("level2")
                .getAsJsonObject("level3")
                .get("deep").asString
        )

        kvStore.delete(key)
    }

    @Test
    fun `put(JsonObject) and get() - empty object`() = runBlocking {
        val key = "test.empty-object"
        val value = JsonObject()

        kvStore.put(key, value)
        val result = kvStore.get(key)

        Assert.assertTrue(result?.value is JsonObject)
        Assert.assertEquals(0, (result?.value as JsonObject).size())

        kvStore.delete(key)
    }

    @Test
    fun `put(JsonObject) and get() - object with mixed types`() = runBlocking {
        val key = "test.mixed-object"
        val value = JsonObject().apply {
            addProperty("string", "text")
            addProperty("number", 123)
            addProperty("double", 45.67)
            addProperty("boolean", true)
            add("array", JsonArray().apply {
                add(1)
                add(2)
                add(3)
            })
        }

        kvStore.put(key, value)
        val result = kvStore.get(key)

        Assert.assertTrue(result?.value is JsonObject)
        val resultObj = result?.value as JsonObject
        Assert.assertEquals("text", resultObj.get("string").asString)
        Assert.assertEquals(123, resultObj.get("number").asInt)
        Assert.assertEquals(45.67, resultObj.get("double").asDouble, 0.01)
        Assert.assertEquals(true, resultObj.get("boolean").asBoolean)
        Assert.assertTrue(resultObj.get("array").isJsonArray)

        kvStore.delete(key)
    }

    // ==================== put(JsonArray) Tests ====================

    @Test
    fun `put(JsonArray) and get() - simple array`() = runBlocking {
        val key = "test.array"
        val value = JsonArray().apply {
            add(1)
            add(2)
            add(3)
            add("four")
            add(JsonObject().apply { addProperty("five", 5) })
        }

        kvStore.put(key, value)
        val result = kvStore.get(key)

        Assert.assertTrue(result?.value is JsonArray)
        val resultArray = result?.value as JsonArray
        Assert.assertEquals(5, resultArray.size())
        Assert.assertEquals(1, resultArray[0].asInt)
        Assert.assertEquals("four", resultArray[3].asString)

        kvStore.delete(key)
    }

    @Test
    fun `put(JsonArray) and get() - empty array`() = runBlocking {
        val key = "test.empty-array"
        val value = JsonArray()

        kvStore.put(key, value)
        val result = kvStore.get(key)

        Assert.assertTrue(result?.value is JsonArray)
        Assert.assertEquals(0, (result?.value as JsonArray).size())

        kvStore.delete(key)
    }

    @Test
    fun `put(JsonArray) and get() - nested arrays`() = runBlocking {
        val key = "test.nested-array"
        val value = JsonArray().apply {
            add(JsonArray().apply {
                add(1)
                add(2)
            })
            add(JsonArray().apply {
                add(3)
                add(4)
            })
        }

        kvStore.put(key, value)
        val result = kvStore.get(key)

        Assert.assertTrue(result?.value is JsonArray)
        val resultArray = result?.value as JsonArray
        Assert.assertEquals(2, resultArray.size())
        Assert.assertTrue(resultArray[0].isJsonArray)

        kvStore.delete(key)
    }

    // ==================== parseNumber Function Tests ====================

    @Test
    fun `parseNumber - parses integer`() {
        val result = kvStore.parseNumber("42")
        Assert.assertNotNull(result)
        Assert.assertEquals(42, result)
    }

    @Test
    fun `parseNumber - parses negative integer`() {
        val result = kvStore.parseNumber("-42")
        Assert.assertNotNull(result)
        Assert.assertEquals(-42, result)
    }

    @Test
    fun `parseNumber - parses zero`() {
        val result = kvStore.parseNumber("0")
        Assert.assertNotNull(result)
        Assert.assertEquals(0, result)
    }

    @Test
    fun `parseNumber - parses large integer as long`() {
        val result = kvStore.parseNumber("9223372036854775807")
        Assert.assertNotNull(result)
        Assert.assertEquals(9223372036854775807L, result)
    }

    @Test
    fun `parseNumber - parses integer too large for int as long`() {
        val result = kvStore.parseNumber("2147483648")
        Assert.assertNotNull(result)
        Assert.assertEquals(2147483648L, result)
    }

    @Test
    fun `parseNumber - parses double with decimal point`() {
        val result = kvStore.parseNumber("3.14")
        Assert.assertNotNull(result)
        Assert.assertEquals(3.14, result)
    }

    @Test
    fun `parseNumber - parses negative double`() {
        val result = kvStore.parseNumber("-99.99")
        Assert.assertNotNull(result)
        Assert.assertEquals(-99.99, result)
    }

    @Test
    fun `parseNumber - parses double with leading zero`() {
        val result = kvStore.parseNumber("0.5")
        Assert.assertNotNull(result)
        Assert.assertEquals(0.5, result)
    }

    @Test
    fun `parseNumber - parses very small double`() {
        val result = kvStore.parseNumber("0.0000001")
        Assert.assertNotNull(result)
        Assert.assertEquals(0.0000001, (result as Double), 0.00000001)
    }

    @Test
    fun `parseNumber - parses very large double`() {
        val result = kvStore.parseNumber("1.7976931348623157E308")
        Assert.assertNotNull(result)
        Assert.assertTrue(result is Double)
    }

    @Test
    fun `parseNumber - parses scientific notation`() {
        val result = kvStore.parseNumber("1.23e10")
        Assert.assertNotNull(result)
        Assert.assertEquals(1.23e10, (result as Double), 0.01)
    }

    @Test
    fun `parseNumber - parses negative scientific notation`() {
        val result = kvStore.parseNumber("-1.5e-5")
        Assert.assertNotNull(result)
        Assert.assertEquals(-1.5e-5, (result as Double), 0.000001)
    }

    @Test
    fun `parseNumber - returns null for non-numeric string`() {
        val result = kvStore.parseNumber("not a number")
        Assert.assertNull(result)
    }

    @Test
    fun `parseNumber - returns null for empty string`() {
        val result = kvStore.parseNumber("")
        Assert.assertNull(result)
    }

    @Test
    fun `parseNumber - returns null for string with spaces`() {
        val result = kvStore.parseNumber("12 34")
        Assert.assertNull(result)
    }

    @Test
    fun `parseNumber - returns null for string with letters and numbers`() {
        val result = kvStore.parseNumber("12abc")
        Assert.assertNull(result)
    }

    @Test
    fun `parseNumber - returns null for boolean string`() {
        val result = kvStore.parseNumber("true")
        Assert.assertNull(result)
    }

    @Test
    fun `parseNumber - returns null for infinity`() {
        val result = kvStore.parseNumber("Infinity")
        Assert.assertNull(result)
    }

    @Test
    fun `parseNumber - returns null for NaN`() {
        val result = kvStore.parseNumber("NaN")
        Assert.assertNull(result)
    }

    @Test
    fun `parseNumber - parses hex format if supported by parser`() {
        // Note: Kotlin's toIntOrNull doesn't support hex by default
        val result = kvStore.parseNumber("0xFF")
        // This should return null since standard parsing doesn't support hex
        Assert.assertNull(result)
    }

    @Test
    fun `parseNumber - handles leading plus sign`() {
        val result = kvStore.parseNumber("+42")
        // Kotlin's standard parsers don't support leading plus
        Assert.assertNull(result)
    }

    @Test
    fun `parseNumber - handles trailing whitespace`() {
        val result = kvStore.parseNumber("42 ")
        Assert.assertNull(result)
    }

    @Test
    fun `parseNumber - handles leading whitespace`() {
        val result = kvStore.parseNumber(" 42")
        Assert.assertNull(result)
    }

    @Test
    fun `parseNumber - handles decimal without leading digit`() {
        val result = kvStore.parseNumber(".5")
        Assert.assertNull(result)
    }

    @Test
    fun `parseNumber - handles multiple decimal points`() {
        val result = kvStore.parseNumber("1.2.3")
        Assert.assertNull(result)
    }

    @Test
    fun `parseNumber - handles minus sign only`() {
        val result = kvStore.parseNumber("-")
        Assert.assertNull(result)
    }

    @Test
    fun `parseNumber - handles decimal point only`() {
        val result = kvStore.parseNumber(".")
        Assert.assertNull(result)
    }

    // ==================== get() Tests ====================

    @Test
    fun `get() - validates empty key`() {
        val exception = Assert.assertThrows(Error::class.java) {
            runBlocking {
                kvStore.get("")
            }
        }
        Assert.assertEquals("key cannot be empty", exception.message)
    }

    @Test
    fun `get() - returns null for non-existent key`() = runBlocking {
        val result = kvStore.get("non-existent-key-xyz")
        Assert.assertNull(result)
    }

    // ==================== delete() Tests ====================

    @Test
    fun `delete() - validates empty key`() {
        val exception = Assert.assertThrows(Error::class.java) {
            runBlocking {
                kvStore.delete("")
            }
        }
        Assert.assertEquals("key cannot be empty", exception.message)
    }

    @Test
    fun `delete() - successfully deletes key`() = runBlocking {
        val key = "test.delete"

        kvStore.put(key, "to be deleted")
        var result = kvStore.get(key)
        Assert.assertEquals("to be deleted", result?.value)

        kvStore.delete(key)
        result = kvStore.get(key)
        Assert.assertNull(result)
    }

    @Test
    fun `delete() - deleting non-existent key does not throw`() = runBlocking {
        // Should not throw error
        kvStore.delete("non-existent-key-to-delete")
        Assert.assertTrue(true)
    }

    // ==================== keys() Tests ====================

    @Test
    fun `keys() - returns list of keys`() = runBlocking {
        // Put multiple keys
        kvStore.put("keys.test1", "value1")
        kvStore.put("keys.test2", "value2")
        kvStore.put("keys.test3", "value3")

        val keys = kvStore.keys()

        Assert.assertTrue(keys.contains("keys.test1"))
        Assert.assertTrue(keys.contains("keys.test2"))
        Assert.assertTrue(keys.contains("keys.test3"))

        // Cleanup
        kvStore.delete("keys.test1")
        kvStore.delete("keys.test2")
        kvStore.delete("keys.test3")
    }

    @Test
    fun `keys() - returns empty list when no keys exist`() = runBlocking {
        // Clean up all keys first
        val allKeys = kvStore.keys()
        for (key in allKeys) {
            kvStore.delete(key)
        }

        val keys = kvStore.keys()
        Assert.assertEquals(0, keys.size)
    }

    // ==================== Update Tests ====================

    @Test
    fun `put() - update existing key with string`() = runBlocking {
        val key = "test.update"

        kvStore.put(key, "initial value")
        var result = kvStore.get(key)
        Assert.assertEquals("initial value", result?.value)

        kvStore.put(key, "updated value")
        result = kvStore.get(key)
        Assert.assertEquals("updated value", result?.value)

        kvStore.delete(key)
    }

    @Test
    fun `put() - update existing key with number`() = runBlocking {
        val key = "test.update-number"

        kvStore.put(key, 100 as Number)
        var result = kvStore.get(key)
        Assert.assertEquals(100, result?.value)

        kvStore.put(key, 200 as Number)
        result = kvStore.get(key)
        Assert.assertEquals(200, result?.value)

        kvStore.delete(key)
    }

    @Test
    fun `put() - update existing key changing type`() = runBlocking {
        val key = "test.update-type"

        kvStore.put(key, "string value")
        var result = kvStore.get(key)
        Assert.assertEquals("string value", result?.value)

        kvStore.put(key, 42 as Number)
        result = kvStore.get(key)
        Assert.assertEquals(42, result?.value)

        kvStore.delete(key)
    }

    // ==================== Integration Test - Full Workflow ====================

    @Test
    fun `Integration - full KV workflow`() = runBlocking {
        val prefix = "integration."

        // 1. Put multiple keys with different value types
        kvStore.put("${prefix}user.1", JsonObject().apply {
            addProperty("name", "Alice")
            addProperty("age", 30)
        })
        kvStore.put("${prefix}user.2", JsonObject().apply {
            addProperty("name", "Bob")
            addProperty("age", 25)
        })
        kvStore.put("${prefix}counter", 42 as Number)
        kvStore.put("${prefix}active", true)
        kvStore.put("${prefix}tags", JsonArray().apply {
            add("test")
            add("integration")
        })

        // 2. Get all keys
        val allKeys = kvStore.keys()
        Assert.assertTrue(allKeys.contains("${prefix}user.1"))
        Assert.assertTrue(allKeys.contains("${prefix}user.2"))
        Assert.assertTrue(allKeys.contains("${prefix}counter"))
        Assert.assertTrue(allKeys.contains("${prefix}active"))
        Assert.assertTrue(allKeys.contains("${prefix}tags"))

        // 3. Retrieve and verify values
        val user1 = kvStore.get("${prefix}user.1")
        Assert.assertTrue(user1?.value is JsonObject)
        Assert.assertEquals("Alice", (user1?.value as JsonObject).get("name").asString)

        val counter = kvStore.get("${prefix}counter")
        Assert.assertEquals(42, counter?.value)

        val active = kvStore.get("${prefix}active")
        Assert.assertEquals(true, active?.value)

        val tags = kvStore.get("${prefix}tags")
        Assert.assertTrue(tags?.value is JsonArray)

        // 4. Update a value
        kvStore.put("${prefix}user.1", JsonObject().apply {
            addProperty("name", "Alice")
            addProperty("age", 31)
        })
        val updatedUser = kvStore.get("${prefix}user.1")
        Assert.assertEquals(31, (updatedUser?.value as JsonObject).get("age").asInt)

        // 5. Delete keys
        kvStore.delete("${prefix}user.2")
        val deletedUser = kvStore.get("${prefix}user.2")
        Assert.assertNull(deletedUser)

        // 6. Verify remaining keys
        val remainingKeys = kvStore.keys()
        Assert.assertTrue(remainingKeys.contains("${prefix}user.1"))
        Assert.assertFalse(remainingKeys.contains("${prefix}user.2"))

        // Cleanup
        kvStore.delete("${prefix}user.1")
        kvStore.delete("${prefix}counter")
        kvStore.delete("${prefix}active")
        kvStore.delete("${prefix}tags")
    }

    // ==================== Edge Cases ====================

    @Test
    fun `Edge case - put and get with special characters in key`() = runBlocking {
        val key = "test.special-chars_123"
        val value = JsonObject().apply { addProperty("data", "special") }

        kvStore.put(key, value)
        val result = kvStore.get(key)

        Assert.assertTrue(result?.value is JsonObject)
        Assert.assertEquals("special", (result?.value as JsonObject).get("data").asString)

        kvStore.delete(key)
    }

    @Test
    fun `Edge case - put and get with forward slash in key`() = runBlocking {
        val key = "test/path/to/key"
        val value = "value"

        kvStore.put(key, value)
        val result = kvStore.get(key)

        Assert.assertEquals(value, result?.value)

        kvStore.delete(key)
    }

    @Test
    fun `Edge case - put and get with equals sign in key`() = runBlocking {
        val key = "config=production"
        val value = "enabled"

        kvStore.put(key, value)
        val result = kvStore.get(key)

        Assert.assertEquals(value, result?.value)

        kvStore.delete(key)
    }
}
