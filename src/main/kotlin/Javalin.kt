import io.javalin.Javalin
import io.javalin.community.ssl.SSLPlugin
import java.net.ServerSocket

fun makeServer(randomPorts: Boolean = false): Javalin = Javalin.create { javalinConfig ->
    javalinConfig.plugins.register(SSLPlugin { conf ->
        conf.pemFromPath("/Users/seanseibel/.certs/localhost.pem", "/Users/seanseibel/.certs/localhost-key.pem")
        if (randomPorts) {
            val port = unusedPort()
            println(port)
            conf.securePort = port
        }
        conf.insecure = false
        conf.redirect = true
        // conf.sniHostCheck = false // this is probably a bad idea, also I think the web won't trust us anyway
    }) // make keys on the ec2?
    javalinConfig.plugins.enableCors { cors ->
        cors.add { it.anyHost() } // try to figure this out
    }
}

//fun makeServer(randomPorts: Boolean = false): Javalin = Javalin.create { javalinConfig ->
//    javalinConfig.plugins.enableCors { cors ->
//        cors.add { it.anyHost() } // try to figure this out
//    }
//}

fun unusedPort(): Int {
    val ssock = ServerSocket(0)
    val port = ssock.localPort
    ssock.close()
    return port
} // this is so uggo