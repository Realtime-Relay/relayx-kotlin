package relay

import com.ensarsarajcic.kotlinx.serialization.msgpack.MsgPack
import com.google.gson.Gson
import com.google.gson.JsonParser
import relay.models.RequestBody
import io.nats.client.Connection
import io.nats.client.JetStream
import io.nats.client.JetStreamManagement
import io.nats.client.impl.NatsMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import relay.Realtime.Companion.CONNECTED
import relay.Realtime.Companion.DISCONNECTED
import relay.Realtime.Companion.MESSAGE_RESEND
import relay.Realtime.Companion.RECONNECT
import relay.Realtime.Companion.RECONNECTED
import relay.Realtime.Companion.RECONNECTING
import relay.Realtime.Companion.RECONN_FAIL
import io.nats.client.ConnectionListener
import io.nats.client.ConsumerContext
import io.nats.client.JetStreamApiException
import io.nats.client.JetStreamStatusException
import io.nats.client.api.AckPolicy
import io.nats.client.api.ConsumerConfiguration
import io.nats.client.api.DeliverPolicy
import io.nats.client.api.PublishAck
import io.nats.client.api.ReplayPolicy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.encodeToByteArray
import relay.models.ConsumerConfig
import relay.models.QueueMessage
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.time.Duration
import java.time.Instant
import java.time.ZonedDateTime
import java.util.Collections
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArraySet
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.collections.set

class Queue {

    var natsClient: Connection? = null;
    var jetstream: JetStream? = null

    var callbackScope: CoroutineScope? = null

    private val listeners = ConcurrentHashMap<String, (Any) -> Unit>()
    private val subscribedTopics = CopyOnWriteArraySet<String>()

    var apiKey: String? = null;

    var debug: Boolean = false;

    private var queueID: String? = null;

    private var namespace: String? = null;
    private var hash: String? = null;

    private val reservedTopics = setOf(CONNECTED, RECONNECT, MESSAGE_RESEND, DISCONNECTED, RECONNECTING, RECONNECTED, RECONN_FAIL)
    private var isConnected = AtomicBoolean(true)
    private var isReconnecting = AtomicBoolean(false)

    private val offlineMessages = Collections.synchronizedList(mutableListOf<OfflineMessage>())

    private val consumerMap = ConcurrentHashMap<String, ConsumerContext>()
    private val consumerJobs = ConcurrentHashMap<String, Job>()

    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val utils = Utils();

    fun init(queueID: String): Boolean{
        this.queueID = queueID;

        validateClassInputs();

        utils.debug = this.debug;

        val result = getQueueNamespace();

        return result;
    }

    suspend fun publish(topic: String, message: Any): Boolean = withContext(Dispatchers.IO) {
        utils.validateTopic(topic)
        utils.validateEmptyMessage(message)
        utils.validateMessage(message)

        if (reservedTopics.contains(topic)) throw IllegalArgumentException("Cannot publish to a reserved SDK topic: $topic")

        val finalTopic = utils.finalTopic(topic)
        val sendMessage = Message(id = UUID.randomUUID().toString(), room = topic, message = message, start = Instant.now().toEpochMilli())

        val packed: ByteArray = MsgPack.encodeToByteArray(sendMessage)

        if (isConnected.get()) {
            var ack: PublishAck? = null;

            try{
                val natsMsg = NatsMessage.builder()
                    .subject(finalTopic)
                    .data(packed)
                    .build()

                ack = jetstream?.publish(natsMsg)
            }catch (e : IOException){
                utils.logError(e.message!!, topic)
            }catch (e : JetStreamApiException){
                utils.logError(e, topic)
            }

            return@withContext ack?.error == null;
        } else {
            offlineMessages.add(OfflineMessage(
                msg=sendMessage,
                resent=false
            ))
            false
        }
    }

    suspend fun consume(config: ConsumerConfig, listener: (Any) -> Unit) = withContext(Dispatchers.IO){
        validateConfig(config)

        val topic = config.topic!!;

        if(listeners.containsKey(topic)){
            return@withContext false
        }

        listeners[topic] = listener

        if(!reservedTopics.contains(topic)){
            subscribedTopics.add(topic)

            if(isConnected.get()){
                startConsumer(config)
            }
        }

        return@withContext true
    }

    fun detachConsumer(topic: String){
        utils.validateTopic(topic)
        listeners.remove(topic)
        subscribedTopics.remove(topic)

        consumerJobs.get(topic)?.cancel()

        consumerJobs.remove(topic)

        consumerMap.remove(topic)
    }

    suspend fun deleteConsumer(name: String): Boolean = withContext(Dispatchers.IO) {
        var deleted: Boolean = false;

        try{
            deleted = jetstream?.getStreamContext(utils.getQueueName())!!.deleteConsumer(name)
        }catch (e : Exception){
            utils.log("Failed to delete consumer $name")
            utils.log(e.message!!)
        }

        return@withContext deleted
    }

    // Support functions
    private fun startConsumer(config: ConsumerConfig) {
        val topic = config.topic!!

        val job = ioScope.launch {

            val finalTopic = utils.finalTopic(topic)

            val consumerConfigBuilder = ConsumerConfiguration.builder()
                .name(config.name)
                .durable(config.name)
                .deliverGroup(config.group)
                .filterSubject(finalTopic)
                .ackPolicy(AckPolicy.Explicit)
                .deliverPolicy(DeliverPolicy.New)
                .replayPolicy(ReplayPolicy.Instant)

            if(config.ack_wait != null){
                consumerConfigBuilder.ackWait(Duration.ofSeconds(config.ack_wait!!))
            }

            if(config.back_off != null){
                val fBackOff = config.back_off;

                consumerConfigBuilder.backoff(*fBackOff!!.map { Duration.ofSeconds(it) }.toTypedArray())
            }

            if(config.max_deliver != null){
                consumerConfigBuilder.maxDeliver(config.max_deliver)
            }

            if(config.max_ack_pending != null){
                consumerConfigBuilder.maxDeliver(config.max_ack_pending)
            }

            val consumerConfig = consumerConfigBuilder.build();

            val streamContext = jetstream?.getStreamContext(utils.getQueueName())

            var consumer: ConsumerContext? = null;

            try{
                consumer = streamContext?.createOrUpdateConsumer(consumerConfig);
            }catch (e : IOException){
                utils.logError(e.message!!, topic)

                cancel();
            }

            consumerMap[topic] = consumer!!

            while(isActive){
                var msg: io.nats.client.Message? = null;

                try {
                    msg = consumer.next(1000)
                }catch (e : JetStreamStatusException){
                    // We want to catch the error, print it and kill the conusmer

                    utils.logError(e, null)
                    detachConsumer(topic)
                }

                if(msg == null){
                    continue
                }

                val msgTopic = utils.stripTopicHash(msg.subject)

                try {
                    msg.inProgress();

                    val unpacked = MsgPack.decodeFromByteArray<Message>(msg.data)

                    utils.log(unpacked.toString())

                    val match = utils.topicPatternMatcher(topic, msgTopic)
                    utils.log("$topic || $msgTopic => $match")

                    if(match){
                        val queueMsg = QueueMessage(
                            message = unpacked,
                            ack = { msg.ack() },
                            nack = { msg.nakWithDelay(it) }
                        )

                        callbackScope!!.launch {
                            listeners[topic]?.invoke(queueMsg)
                        }
                    }
                } catch (e: Exception) {
                    msg.nakWithDelay(Duration.ofSeconds(5))
                    utils.log("Consumer error [$topic]: ${e.message}")
                }
            }
        }

        consumerJobs[topic] = job
    }

    private fun getQueueNamespace(): Boolean{
        val requestBody = RequestBody(api_key = this.apiKey, queue_id = this.queueID)

        try {
            val originalJson = Gson().toJson(requestBody).toByteArray()
            val responseMsg = natsClient?.request("accounts.user.get_queue_namespace", originalJson, Duration.ofSeconds(5))

            val responseStr = String(responseMsg?.data ?: byteArrayOf(), StandardCharsets.UTF_8)

            val responseJson = JsonParser.parseString(responseStr).asJsonObject

            utils.log(responseJson)

            if(responseJson.get("status").asString == "NAMESPACE_RETRIEVE_SUCCESS") {
                val data = responseJson.get("data").asJsonObject

                this.namespace = data.get("namespace").asString;
                this.hash = data.get("hash").asString;

                utils.namespace = this.namespace!!;
                utils.hash = this.hash!!;

                return true;
            }else{
                val code = responseJson.get("code").asString;

                if(code.equals("QUEUE_NOT_FOUND")){
                    println("-------------------------------------------------")
                    println("Event: Queue Not Found")
                    println("Description: The queue does not exist OR has been disabled")
                    println("Queue ID: ${this.queueID}")
                    println("Docs to Solve Issue: <>")
                    println("-------------------------------------------------")
                }

                return false;
            }
        } catch (e: Exception) {
            utils.log("Namespace fetch failed: ${e.message}")

            return false;
        }
    }

    internal fun onConnectionEvent(event: ConnectionListener.Events) {
        utils.log("Queue connection event: ${event.name}")
        when (event) {
            ConnectionListener.Events.CONNECTED -> {
                isConnected.set(true)
                isReconnecting.set(false)
            }
            ConnectionListener.Events.RECONNECTED -> {
                isConnected.set(true)
                isReconnecting.set(false)
            }
            ConnectionListener.Events.DISCONNECTED -> {
                isConnected.set(false)
                isReconnecting.set(true)
            }
            ConnectionListener.Events.CLOSED -> {
                isConnected.set(false)
                isReconnecting.set(false)
            }
            else -> {}
        }
    }

    // Utility
    private fun validateClassInputs(){
        requireNotNull(this.natsClient) {
            "natsClient must not be null"
        }

        requireNotNull(this.jetstream) {
            "jetstream must not be null"
        }

        requireNotNull(this.apiKey) {
            "apiKey must not be null"
        }
    }

    private fun validateConfig(config: ConsumerConfig){
        require(config.name != null && !config.name!!.isEmpty()){
            "config.name cannot be blank / empty"
        }

        require(config.name != null && !config.topic!!.isEmpty()){
            "config.topic cannot be blank / empty"
        }

        require(config.name != null && !config.group!!.isEmpty()){
            "config.group cannot be blank / empty"
        }
    }


}
