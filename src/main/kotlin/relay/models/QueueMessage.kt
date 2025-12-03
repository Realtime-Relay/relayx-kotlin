package relay.models

import relay.Message

data class QueueMessage(
    val message: Message,
    val ack: () -> Unit,
    val nack: (Long) -> Unit
)
