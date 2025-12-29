# RelayX Kotlin SDK

![License](https://img.shields.io/badge/Apache_2.0-green?label=License)

Official RelayX SDK for Kotlin and JVM-based applications.

---

## What is RelayX?

RelayX is a real-time messaging platform that provides pub/sub messaging, distributed queues, and a key-value store. It enables developers to build real-time applications without managing infrastructure.

---

## Installation

Add the dependency to your `build.gradle.kts`:

```kotlin
dependencies {
    implementation("com.realtime.relay:relayx-kotlin:1.0.0")
}
```

Or for Gradle (Groovy):

```groovy
dependencies {
    implementation 'com.realtime.relay:relayx-kotlin:1.1.0'
}
```

---

## Quick Start

```kotlin
import relay.Realtime
import relay.models.RealtimeConfig
import java.io.File
import kotlinx.coroutines.runBlocking

fun main() = runBlocking {
    val realtime = Realtime()
    realtime.apiKey = "your-api-key"
    realtime.secretKey = "your-secret-key"
    realtime.filesDir = File("/path/to/credentials")

    val config = RealtimeConfig()
    config.staging = false
    config.debug = true

    realtime.init(config)

    realtime.on("events.user") { data ->
        println("Received: $data")
    }

    realtime.connect()

    realtime.publish("events.user", "Hello from RelayX!")
}
```

---

## Messaging (Pub/Sub)

Publish and subscribe to topics with wildcard support:

```kotlin
realtime.on("notifications.*") { data ->
    println("Notification: $data")
}

realtime.publish("notifications.email", "New email received")
```

Supports multiple data types:

```kotlin
realtime.publish("data.metrics", 42)
realtime.publish("data.config", jsonObject)
```

---

## Queues

Distribute work across multiple consumers with guaranteed delivery:

```kotlin
import relay.models.ConsumerConfig
import relay.models.QueueMessage

val queue = realtime.initQueue("your-queue-id")

val config = ConsumerConfig()
config.name = "worker-1"
config.group = "workers"
config.topic = "tasks.process"
config.ack_wait = 5L
config.max_deliver = 10L

queue?.consume(config) { message ->
    val queueMsg = message as QueueMessage
    println("Processing: ${queueMsg.message}")
    queueMsg.ack()
}

queue?.publish("tasks.process", "Process this task")
```

---

## Key-Value Store

Simple distributed key-value storage:

```kotlin
val kvStore = realtime.initKVStore()

kvStore.put("user:123", jsonObject)
kvStore.put("count", 42)

val data = kvStore.get("user:123")
println("Value: ${data?.value}")
```

Supports strings, numbers, booleans, JSON objects, and JSON arrays.

---

## Documentation

Full documentation including delivery guarantees, permissions, and advanced features:

https://docs.relay-x.io

---

## License

This SDK is licensed under the Apache 2.0 License.
