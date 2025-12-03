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
import io.nats.client.api.PublishAck
import kotlinx.serialization.encodeToByteArray
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.time.Duration
import java.time.Instant
import java.util.Collections
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

class Queue {

    var natsClient: Connection? = null;
    var jetstream: JetStream? = null

    var jetstreamManager: JetStreamManagement? = null;

    var apiKey: String? = null;

    var debug: Boolean = false;

    private var queueID: String? = null;

    private var namespace: String? = null;
    private var hash: String? = null;

    private val reservedTopics = setOf(CONNECTED, RECONNECT, MESSAGE_RESEND, DISCONNECTED, RECONNECTING, RECONNECTED, RECONN_FAIL)
    private var isConnected = AtomicBoolean(true)
    private var isReconnecting = AtomicBoolean(false)

    private val offlineMessages = Collections.synchronizedList(mutableListOf<OfflineMessage>())

    private val utils = Utils();

    fun init(queueID: String): Boolean{
        this.queueID = queueID;

        validateClassInputs();

        utils.debug = this.debug;

        val result = getQueueNamespace();

        return result;
    }

    suspend fun publish(topic: String, message: Any): Boolean = withContext(Dispatchers.IO) {
        validateTopic(topic)
        validateEmptyMessage(message)
        validateMessage(message)

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

    private fun validateTopic(topic: String) {
        val topicNotNull = !topic.isBlank()

        val topicRegex = Regex("^(?!.*\\\$)(?:[A-Za-z0-9_*~-]+(?:\\.[A-Za-z0-9_*~-]+)*(?:\\.>)?|>)\$")

        val spaceStarCheck = !topic.contains(" ") && topicRegex.matches(topic)

        require(spaceStarCheck && topicNotNull) { "Invalid topic" }
    }

    private fun validateMessage(msg: Any) {
        require(msg is String || msg is Number || msg is Map<*, *>) { "Message must be string, number or Map<String, String | Number | Map>" }
    }

    fun isMessageValid(msg: Any) {
        require(msg is String || msg is Number || msg is Map<*, *>) { "Message must be string, number or Map<String, String | Number | Map>" }
    }

    private fun validateEmptyMessage(msg: Any) {
        require(msg != null) { "Message must not be null or empty" }
    }
}
