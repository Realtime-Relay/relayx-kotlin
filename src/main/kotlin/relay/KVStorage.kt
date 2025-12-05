package relay

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import io.nats.client.Connection
import io.nats.client.KeyValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import relay.Utils.*
import relay.models.KeyValueData
import java.io.IOException
import kotlin.math.log

class KVStorage (
    var namespace: String,
    var natsClient: Connection,
    var debug: Boolean
) {

    private val errorLogger: ErrorLogging = ErrorLogging();
    private lateinit var logger: Logging;

    private lateinit var kvStore: KeyValue;

    fun init(): Boolean{
        validateInput();

        this.kvStore = natsClient.keyValue(this.namespace)

        logger = Logging(debug)

        return true;
    }

    suspend fun get(key: String): KeyValueData? = withContext(Dispatchers.IO){
        validateKey(key)

        var value: ByteArray? = null

        try{
            value = kvStore.get(key).value
        }catch (err : IOException){
            errorLogger.log(err.message!!, null, "kv_read")
        }

        var fVal: Any? = null;
        var kvData: KeyValueData? = null;

        try{
            fVal = value!!.decodeToString().toBooleanStrict();
        }catch (ex: Exception){
            fVal = null;
        }

        if(fVal == null){
            try{
                fVal = parseNumber(value!!.decodeToString())
            }catch (ex: Exception){
                fVal = null;
            }
        }

        if(fVal == null){
            try{
                fVal = JsonParser.parseString(value!!.decodeToString()).asJsonObject
            }catch (ex: Exception){
                fVal = null;
            }
        }

        if(fVal == null){
            try{
                fVal = JsonParser.parseString(value!!.decodeToString()).asJsonArray
            }catch (ex: Exception){
                fVal = null;
            }
        }

        if(fVal == null){
            try{
                fVal = value!!.decodeToString()
            }catch (ex: Exception){
                fVal = null;
            }
        }

        if(fVal == "null"){
            fVal = null
        }

        kvData = KeyValueData(key=key, value=fVal);
        return@withContext kvData;
    }

    suspend fun put(key: String, value: Number) = withContext(Dispatchers.IO){
        validateKey(key)
        validateValue(value)

        try{
            kvStore.put(key, value)
        }catch (err: IOException){
            errorLogger.log(err.message!!, null, "kv_write")
        }
    }

    suspend fun put(key: String, value: String) = withContext(Dispatchers.IO){
        validateKey(key)
        validateValue(value)

        try{
            kvStore.put(key, value)
        }catch (err: IOException){
            errorLogger.log(err.message!!, null, "kv_write")
        }
    }

    suspend fun put(key: String, value: Boolean) = withContext(Dispatchers.IO){
        validateKey(key)
        validateValue(value)

        try{
            kvStore.put(key, value.toString().toByteArray(Charsets.UTF_8))
        }catch (err: IOException){
            errorLogger.log(err.message!!, null, "kv_write")
        }
    }

    suspend fun put(key: String, value: JsonObject) = withContext(Dispatchers.IO){
        validateKey(key)
        validateValue(value)

        val fValue = value.toString().toByteArray(Charsets.UTF_8)

        try{
            kvStore.put(key, fValue)
        }catch (err: IOException){
            errorLogger.log(err.message!!, null, "kv_write")
        }
    }

    suspend fun put(key: String, value: JsonArray) = withContext(Dispatchers.IO){
        validateKey(key)
        validateValue(value)

        val fValue = value.toString().toByteArray(Charsets.UTF_8)

        try{
            kvStore.put(key, fValue)
        }catch (err: IOException){
            errorLogger.log(err.message!!, null, "kv_write")
        }
    }

    suspend fun delete(key: String) = withContext(Dispatchers.IO){
        validateKey(key)

        try{
            kvStore.purge(key)
        }catch (err : IOException){
            errorLogger.log(err.message!!, null, "kv_delete")
        }
    }

    suspend fun keys(): List<String>{
        var keys: List<String> = listOf();

        try{
            keys = kvStore.keys();
        }catch (err : IOException){
            errorLogger.log(err.message!!, null, "kv_read")
        }

        logger.log(keys)

        return keys
    }

    // Utility Functions
    private fun validateInput(){
        if(this.namespace == ""){
            throw Error("namespace cannot be empty!")
        }
    }

    private fun validateKey(key: String){
        if(key == null){
            throw Error("key cannot be null")
        }

        if(key == ""){
            throw Error("key cannot be empty")
        }

        val validKeyPattern = Regex("^[a-zA-Z0-9_\\-.=/]+$")
        if (!validKeyPattern.matches(key)) {
            throw IllegalArgumentException(
                "key can only contain alphanumeric characters and the following: _ - . = /"
            )
        }
    }

    private fun validateValue(value: Any?) {
        val valueValid =
            value == null ||
                    value is String ||
                    value is Number ||
                    value is Boolean ||
                    value is JsonArray ||
                    value is JsonObject

        if (!valueValid) {
            throw IllegalArgumentException(
                "\$value MUST be null, string, number, boolean, array or json! \$value is \"${value?.let { it::class.simpleName } ?: "null"}\""
            )
        }
    }

    fun parseNumber(str: String): Number? {
        return str.toIntOrNull()        // Try Int first
            ?: str.toLongOrNull()       // Then Long
            ?: str.toDoubleOrNull()     // Then Double
            ?: str.toFloatOrNull()      // Then Float
    }

}