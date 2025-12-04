package examples

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
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

            config = ConsumerConfig()
            config.name = "Test4342"
            config.group = "test-group"
            config.topic = "queue.*.123"
            config.ack_wait = 2L

            queue!!.consume(config, { message ->
                var queueMsg = message as QueueMessage

                println("queue.*.123 => $queueMsg")

                queueMsg.ack()
            })

            config = ConsumerConfig()
            config.name = "Test4343"
            config.group = "test-group"
            config.topic = "queue.>"
            config.ack_wait = 2L

            queue!!.consume(config, { message ->
                var queueMsg = message as QueueMessage

                println("queue.> => $queueMsg")

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