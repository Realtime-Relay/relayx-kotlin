package relay.models

class ConsumerConfig {
    var name: String? = null;
    var group: String? = null;
    var topic: String? = null;

    var ack_wait: Long? = null;
    var back_off: List<Long>? = null;
    var max_deliver: Long? = null;
    var max_ack_pending: Long? = null;
}
