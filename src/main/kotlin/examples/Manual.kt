package examples

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import relay.Realtime
import relay.models.ConsumerConfig
import relay.models.QueueMessage
import relay.models.RealtimeConfig
import java.io.File
import java.util.stream.IntStream.range

fun main() = runBlocking {
    val realtime = Realtime()
    realtime.apiKey = ""
    realtime.secretKey = ""
    realtime.filesDir = File("/Users")

    val config = RealtimeConfig()
    config.staging = true
    config.debug = true

    realtime.init(config)

    realtime.on(Realtime.CONNECTED) { status ->
        val fState = status as Boolean
        println(if (fState) "Connected" else "Authorization Violation")

        runBlocking {
            val kvStore = realtime.initKVStore();
            kvStore.put("key1", JsonObject().apply{
                addProperty("hey", "world")
            })

            kvStore.put("key2", 123)

            kvStore.put("key3", true)

            kvStore.put("key4", false)

            kvStore.put("key5", 10.123)

            kvStore.put("key6", "HELLO EVERYBODY!")

            kvStore.put("key7", JsonArray().apply {
                add("ehy")
                add("Sup")
                add(123)
                add(123.123)
                add(true)
                add(false)
                add(JsonObject().apply {
                    addProperty("hey", "gang")
                })
            })

            println("Read back...")
            kvStore.delete("key5")

            var keys = kvStore.keys();
            println(keys)

            for(key in keys){
                val value = kvStore.get(key)

                println("get(${value!!.key}) = ${value!!.value}")
                println(value!!.value?.javaClass)
            }
//
//            for(key in keys){
//                println("delete($key)")
//                kvStore.delete(key)
//            }
//
//            println("Read back...")
//            keys = kvStore.keys();
//            println(keys)

            val queue = realtime.initQueue("692adca3af5ed9d55e1b1ece")
            println("Queue => $queue")

            var config = ConsumerConfig()
            config.name = "Test434"
            config.group = "test-group"
            config.topic = "queue.123"
            config.ack_wait = 2L

            queue!!.consume(config, { message ->
                var queueMsg = message as QueueMessage

                println("queue.123 => $queueMsg")

                queueMsg.ack()
            })
        }
    }

    realtime.on("hello.>") { data ->
        println(data)

        realtime.off("hello.>")
    }

    realtime.on("power-telemetry") { data ->
        println(data)
    }

    realtime.connect()
}