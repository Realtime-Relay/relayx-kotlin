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
    realtime.apiKey = "eyJ0eXAiOiJKV1QiLCJhbGciOiJlZDI1NTE5LW5rZXkifQ.eyJhdWQiOiJOQVRTIiwibmFtZSI6IjY5NTdkZDU4YTlkNmU3ZWNlOGQ2ZGEwMSIsInN1YiI6IlVCSFVKQVlGRlozQjVBQVAyQzI1NjM2N1RBNFFRQldXQk9LRFJERFhOV05GUDRBQ0pPVVY0TjVKIiwibmF0cyI6eyJkYXRhIjotMSwicGF5bG9hZCI6LTEsInN1YnMiOi0xLCJwdWIiOnsiZGVueSI6WyI-Il19LCJzdWIiOnsiZGVueSI6WyI-Il19LCJvcmdfZGF0YSI6eyJvcmdfaWQiOiI2OGRiYjYxZjk2NjM4NzdkMzg5NTlkMDkiLCJ2YWxpZGl0eV9rZXkiOiJmMWE0MWIxMi0yMDc4LTQ5Y2YtOWRiYS01N2ZkOTI0MWZiYWMiLCJyb2xlIjoidXNlciIsImFwaV9rZXlfaWQiOiI2OTU3ZGQ1OGE5ZDZlN2VjZThkNmRhMDEiLCJlbnYiOiJ0ZXN0In0sImlzc3Vlcl9hY2NvdW50IjoiQUNKS01NSzZUUEk1S1hYNjQ0TEFISk9WT0FGQ1haVFJLR0JVVzJDNDROSERIVFZMWjdPVkpEN0siLCJ0eXBlIjoidXNlciIsInZlcnNpb24iOjJ9LCJpc3MiOiJBQ0pLTU1LNlRQSTVLWFg2NDRMQUhKT1ZPQUZDWFpUUktHQlVXMkM0NE5IREhUVkxaN09WSkQ3SyIsImlhdCI6MTc2NzM2NTk3NiwianRpIjoiTElLZ1JNOG9EbzFPYUlmdlBnRlMwTkIwQjVReWJMUTBIUUtEbnZtTTdrWUIvTXo0S0RvSEphbmNRY1JiQ0pTSFJlWjVnTE5HdjZUYW03eTVGcXRVc1E9PSJ9.pYrrYMQgc6BlRgji4ngt32mm4cIlqjYA7NNiCzA1C4K9mBmIfXB9AKHRkmLhodA7bl_URfLa0PDaBLCPC1vdAA"
    realtime.secretKey = "SUACQ74RVLKXW3J4AA2GGXDYF33YESNFAORELNLVH62MSKKTJUVSISOT74"
    realtime.filesDir = File("/Users/arjun/Code/Relay")

    val config = RealtimeConfig()

    realtime.init(config)

    realtime.on(Realtime.CONNECTED) { status ->
        val fState = status as Boolean
        println(if (fState) "Connected" else "Authorization Violation")

        runBlocking {
            val since = System.currentTimeMillis() - 5 * 60 * 60 * 1_000
            val end = System.currentTimeMillis()

            val history = realtime.history("poll.22535aa5-1e07-469a-a665-a639ee8af40d", since, end, null)
            println(history)

//            val kvStore = realtime.initKVStore();
//            kvStore.put("key1", JsonObject().apply{
//                addProperty("hey", "world")
//            })
//
//            kvStore.put("key2", 123)
//
//            kvStore.put("key3", true)
//
//            kvStore.put("key4", false)
//
//            kvStore.put("key5", 10.123)
//
//            kvStore.put("key6", "HELLO EVERYBODY!")
//
//            kvStore.put("key7", JsonArray().apply {
//                add("ehy")
//                add("Sup")
//                add(123)
//                add(123.123)
//                add(true)
//                add(false)
//                add(JsonObject().apply {
//                    addProperty("hey", "gang")
//                })
//            })
//
//            println("Read back...")
//            kvStore.delete("key5")
//
//            var keys = kvStore.keys();
//            println(keys)
//
//            for(key in keys){
//                val value = kvStore.get(key)
//
//                println("get(${value!!.key}) = ${value!!.value}")
//                println(value!!.value?.javaClass)
//            }
//
//            for(key in keys){
//                println("delete($key)")
//                kvStore.delete(key)
//            }
//
//            println("Read back...")
//            keys = kvStore.keys();
//            println(keys)

//            val queue = realtime.initQueue("692adca3af5ed9d55e1b1ece")
//            println("Queue => $queue")
//
//            var config = ConsumerConfig()
//            config.name = "Test434"
//            config.group = "test-group"
//            config.topic = "queue.123"
//            config.ack_wait = 2L
//
//            queue!!.consume(config, { message ->
//                var queueMsg = message as QueueMessage
//
//                println("queue.123 => $queueMsg")
//
//                queueMsg.ack()
//            })
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