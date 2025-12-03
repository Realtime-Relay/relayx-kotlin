package relay

import java.io.File
import java.io.FileOutputStream

class Utils {
    var namespace: String = "<API Key>"
    var hash: String = "<Secret Key>"

    var debug: Boolean = false;

    var errorLogging = ErrorLogging()

    fun createNatsCredsFile(jwt: String, nkeySeed: String): File {
        val filename = "nats_user.creds"

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
        val file = File(filename)
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

    fun logError(err: String?, topic: String?){
        errorLogging.log(err, topic)
    }

    fun finalTopic(topic: String): String = "$hash.$topic"

    class ErrorLogging(){

        fun log(err: String?, topic: String?){
            if(err == "Authorization Violation"){
                println("-------------------------------------------------")
                println("Event: Authentication Failure")
                println("Description: User failed to authenticate. Check if API key exists & if it is enabled")
                println("Docs to Solve Issue: <>")
                println("-------------------------------------------------")
            }else if(err == "Timeout or no response waiting for NATS JetStream server"){
                println("-------------------------------------------------")
                println("Event: Publish Permissions Violation")
                println("Description: User is not permitted to publish on '$topic'")
                println("Topic: $topic")
                println("Docs to Solve Issue: <>")
                println("-------------------------------------------------")
            }
        }

    }
}