package examples

import kotlinx.coroutines.runBlocking
import relay.Realtime
import relay.models.RealtimeConfig

fun main() = runBlocking {
    val realtime = Realtime()
    realtime.apiKey = "eyJ0eXAiOiJKV1QiLCJhbGciOiJlZDI1NTE5LW5rZXkifQ.eyJhdWQiOiJOQVRTIiwibmFtZSI6IjY5MmFkY2JkYWY1ZWQ5ZDU1ZTFiMWVjZiIsInN1YiI6IlVBM0JERzc0QVhUVEVMNlZONlhSNUpWMkNMWE8yVUFHSTVLNlNWVkJXU1NES0c2T1dIN1FRN0syIiwibmF0cyI6eyJkYXRhIjotMSwicGF5bG9hZCI6LTEsInN1YnMiOi0xLCJwdWIiOnsiZGVueSI6WyI-Il19LCJzdWIiOnsiZGVueSI6WyI-Il19LCJvcmdfZGF0YSI6eyJvcmdfaWQiOiI2OTI1ZTAzMTFiNjFkNDljZGVjMDMyNzgiLCJ2YWxpZGl0eV9rZXkiOiI4NTQ1NzY1Mi0wMWNiLTRlYWEtOGZkMi05MWRmZmE2NTJiZDciLCJyb2xlIjoidXNlciIsImFwaV9rZXlfaWQiOiI2OTJhZGNiZGFmNWVkOWQ1NWUxYjFlY2YiLCJlbnYiOiJ0ZXN0In0sImlzc3Vlcl9hY2NvdW50IjoiQUFZWENCNVZHR0tDSlRKRkZJRUY3WVMzUFZBNk1PQVRYRVRRSDdMM0hXT01TNVJTSFRQWFJCSEsiLCJ0eXBlIjoidXNlciIsInZlcnNpb24iOjJ9LCJpc3MiOiJBQVlYQ0I1VkdHS0NKVEpGRklFRjdZUzNQVkE2TU9BVFhFVFFIN0wzSFdPTVM1UlNIVFBYUkJISyIsImlhdCI6MTc2NDQxNjcwMiwianRpIjoiaU5hLzBhek9GN3hZak9aUmpYbEE5RE1jQ3JVL2FEQ0YxWmU1YlRibXk4L2pVYUpEanFBRGpTd2hGRmREcVlXS2VjeWZ4Z1VIWlhiWnJKYW1kWGRkSnc9PSJ9.PkbEkqW_rQ4iMX_BJPH6Qio3AluAo0c-VYwnobnybiCUc7PKn1bOWDuUbmIrhNr8v_fwcWmMGS2v_Na7u8SpCA"
    realtime.secretKey = "SUAFR63MY4H7HB7YGHJJHE6HKCAGPAXKSSY4IGGIZHQ4A4WGXV7XSKMQ2Y"

    val config = RealtimeConfig()
    config.staging = true
    config.debug = true

    realtime.init(config)

    realtime.on(Realtime.CONNECTED) {
        runBlocking {
            val queue = realtime.initQueue("692adca3af5ed9d55e1b1ece")
            println("Queue => $queue")

            realtime.publish("hello.>", "Sup")
        }
    }

    realtime.on("hello.>") { data ->
        println(data)
    }

    realtime.connect()
}