package relay.models

import kotlinx.serialization.Serializable

@Serializable
data class RequestBody(var api_key: String?, var queue_id: String?)