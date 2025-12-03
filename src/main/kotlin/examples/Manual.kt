package examples

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import relay.Realtime
import relay.models.ConsumerConfig
import relay.models.QueueMessage
import relay.models.RealtimeConfig
import java.util.stream.IntStream.range

fun main() = runBlocking {
    val realtime = Realtime()
    realtime.apiKey = ""
    realtime.secretKey = ""

    val config = RealtimeConfig()
    config.staging = true
    config.debug = true

    realtime.init(config)

    realtime.on(Realtime.CONNECTED) {
        runBlocking {
            val queue = realtime.initQueue("692adca3af5ed9d55e1b1ece")
            println("Queue => $queue")

            val config = ConsumerConfig()
            config.name = "Test434"
            config.group = "test-group"
            config.topic = "queue.123"
            config.ack_wait = 2L

            var count = 0;

            queue!!.consume(config, { message ->
                var queueMsg = message as QueueMessage

                println(queueMsg)

                ++count;

                if(count == 2){
                    println("Detatching consumer")
                    queue.detachConsumer("queue.123")
                }

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