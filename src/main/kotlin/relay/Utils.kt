package relay

import io.nats.client.JetStreamApiException
import io.nats.client.JetStreamStatusException
import java.io.File
import java.io.FileOutputStream

class Utils {
    var namespace: String = "<API Key>"
    var hash: String = "<Secret Key>"

    var debug: Boolean = false;

    var errorLogging = ErrorLogging()

    fun createNatsCredsFile(parentDir: File, jwt: String, nkeySeed: String): File {
        val filename = "user_creds.creds"

        val fileContent = """
        -----BEGIN NATS USER JWT-----
        $jwt
        ------END NATS USER JWT------

        ************************* IMPORTANT *************************
        NKEY Seed printed below can be used to sign and prove identity.
        NKEYs are sensitive and should be treated as secrets.

        -----BEGIN USER NKEY SEED-----
        $nkeySeed
        ------END USER NKEY SEED------

        *************************************************************
    """.trimIndent()

        // Save to current working directory
        val file = File(parentDir, filename)
        FileOutputStream(file).use { output ->
            output.write(fileContent.toByteArray(Charsets.UTF_8))
        }

        return file
    }

    fun log(obj: Any){
        if(this.debug){
            println(obj)
        }
    }

    fun logError(err: Any?, topic: String?){
        errorLogging.log(err, topic)
    }

    fun finalTopic(topic: String): String = "$hash.$topic"

    fun getQueueName(): String = "Q_$namespace"

    fun topicPatternMatcher(patternA: String, patternB: String): Boolean {
        val a = patternA.split(".")
        val b = patternB.split(".")

        var i = 0
        var j = 0

        var starAi = -1
        var starAj = -1
        var starBi = -1
        var starBj = -1

        while (i < a.size || j < b.size) {
            val tokA = a.getOrNull(i)
            val tokB = b.getOrNull(j)

            val singleWildcard =
                (tokA == "*" && j < b.size) ||
                        (tokB == "*" && i < a.size)

            if ((tokA != null && tokA == tokB) || singleWildcard) {
                i++
                j++
                continue
            }

            // Handle multi-token wildcard ">" — must be final
            if (tokA == ">") {
                if (i != a.lastIndex) return false // '>' must be at the end
                if (j >= b.size) return false       // must consume at least one token
                starAi = i++
                starAj = ++j
                continue
            }
            if (tokB == ">") {
                if (j != b.lastIndex) return false
                if (i >= a.size) return false
                starBi = j++
                starBj = ++i
                continue
            }

            // Backtrack if previous '>' was seen
            if (starAi != -1) {
                j = ++starAj
                continue
            }
            if (starBi != -1) {
                i = ++starBj
                continue
            }

            return false // No match possible
        }

        return true
    }

    fun stripTopicHash(topic: String): String = topic.replace("${hash}.", "")

    fun validateMessage(msg: Any) {
        require(msg is String || msg is Number || msg is Map<*, *>) { "Message must be string, number or Map<String, String | Number | Map>" }
    }

    fun validateEmptyMessage(msg: Any) {
        require(msg != null) { "Message must not be null or empty" }
    }

    fun validateTopic(topic: String) {
        val topicNotNull = !topic.isBlank()

        val topicRegex = Regex("^(?!.*\\\$)(?:[A-Za-z0-9_*~-]+(?:\\.[A-Za-z0-9_*~-]+)*(?:\\.>)?|>)\$")

        val spaceStarCheck = !topic.contains(" ") && topicRegex.matches(topic)

        require(spaceStarCheck && topicNotNull) { "Invalid topic" }
    }

    // Error Logging Class
    class ErrorLogging(){

        fun log(err: Any?, topic: String?){

            if(err is String){
                if(err == "Authorization Violation"){
                    println("-------------------------------------------------")
                    println("Event: Authentication Failure")
                    println("Description: User failed to authenticate. Check if API key exists & if it is enabled")
                    println("Docs to Solve Issue: <>")
                    println("-------------------------------------------------")
                }else if(err == "Timeout or no response waiting for NATS JetStream server"){
                    println("-------------------------------------------------")
                    println("Event: Publish / Subscribe Permissions Violation")
                    println("Description: User is not permitted to publish / subscribe on '$topic'")
                    println("Topic: $topic")
                    println("Docs to Solve Issue: <>")
                    println("-------------------------------------------------")
                }
            }

            if(err is JetStreamApiException){
                val apiErrorCode = err.apiErrorCode;
                val code = err.errorCode

                if(code == 503 && apiErrorCode == 10077){
                    println("-------------------------------------------------")
                    println("Event: Message Limit Exceeded")
                    println("Description: Current message count for account exceeds plan defined limits. Upgrade plan to remove limits")
                    println("Link: https://console.relay-x.io/billing")
                    println("-------------------------------------------------")
                }
            }

            if(err is JetStreamStatusException){
                if(err.message == "409 Consumer Deleted"){
                    println("-------------------------------------------------")
                    println("Event: Consumer Manually Deleted!")
                    println("Description: Consumer was manually deleted by user using deleteConsumer() or the library equivalent")
                    println("Docs to Solve Issue: <>")
                    println("-------------------------------------------------")
                }
            }

        }

    }
}