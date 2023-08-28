import io.javalin.Javalin
import io.javalin.community.ssl.SSLPlugin
import io.javalin.http.HttpStatus.*
import io.javalin.websocket.WsContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.Timer
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantLock
import java.util.logging.Handler
import kotlin.concurrent.timerTask

fun main() {
    val app = makeServer()
    val sys = RoomSystem()

    val bigLock = ReentrantLock() // incredibly mystical and powerful solution
    app.before { bigLock.lock() }
    app.after { bigLock.unlock() }

    // return serialized list of room data
    // basically whatever you would need to populate the list of rooms to choose from
    app.get("/rooms") { ctx ->
         ctx.result(Json.encodeToString(sys.roomData()))
        ctx.status(OK)
    }

    app.get("/player_id") { ctx ->
        ctx.result(generatePlayerID().id)
        ctx.status(OK)
    }

    app.post("/room_socket/{roomId}") { ctx ->
        sys.getRoom(RoomID(ctx.pathParam("roomId"))).fold({
            ctx.status(FORBIDDEN)
        }) { room ->
            val playerID = PlayerID(ctx.body())
            if (room.hasPlayer(playerID) || true) {
                val socketData = SocketData(room.port, room.socket.id)
                ctx.result(Json.encodeToString(socketData))
                ctx.status(OK)
            } else {
                ctx.result("PlayerID not found in room")
                ctx.status(FORBIDDEN)
            }
        }
    }

    app.post("/create_room") { ctx ->
        val data: CreateRoom
        try {
            println("create_room decoding: \n${ctx.body()}")
            data = Json.decodeFromString<CreateRoom>(ctx.body())
        } catch (e: Exception) {
            ctx.status(BAD_REQUEST)
            ctx.result("Malformed or incorrect JSON")
            return@post
        }
        try {
            println("create_room failed at decoding: \n${ctx.body()}")
            sys.createRoom(
                PlayerID(data.playerID),
                data.w,
                data.h,
                data.connect,
                data.gravity,
            ).fold({
                ctx.status(FORBIDDEN)
            }) { roomID ->
                sys.getRoom(roomID).fold({
                    ctx.status(INTERNAL_SERVER_ERROR)
                }) { room ->
                    createSocketRoom(sys, room, bigLock)
                    ctx.result(roomID.id)
                    ctx.status(CREATED)
                }
            }
        } catch (e: IllegalArgumentException) {
            ctx.status(BAD_REQUEST)
            ctx.result("Invalid room parameters")
        }
    }

    // more like (can I) join room)
    app.post("/join_room/{roomId}") { ctx ->
        if (sys.roomIsOpenTo(
                RoomID(ctx.pathParam("roomId")),
                PlayerID(ctx.body()),
        )) {
            ctx.status(OK)
        } else {
            ctx.status(FORBIDDEN)
        }
    }

    app.start()
}

/**
 * I got so many servers bro im fuckin loaded on servers
 */
fun createSocketRoom(sys: RoomSystem, room: Room, bigLock: ReentrantLock): Javalin {
    val socketRoom = makeServer(randomPorts = true)
    val path = "/socket/${room.socket.id}"
    // println(path)

    val handler = WsApiHandler(room)

    val joined: MutableSet<WsContext> = mutableSetOf()

    val littleLock = ReentrantLock() // incredibly mystical and powerful solution parte dois
    socketRoom.ws(path) { ws ->
        ws.onConnect { ctx ->
            littleLock.lock()
            joined.add(ctx)
            println("Adding ${ctx.sessionId} to call")
            ctx.enableAutomaticPings(10, TimeUnit.SECONDS) // ping every 10s // disable this ??
            littleLock.unlock()
        }
        ws.onClose { ctx ->
            littleLock.lock()
            joined.remove(ctx)
            println("Dropping ${ctx.sessionId}")
            if (joined.isEmpty()) {
                sys.deleteRoom(room.id) // once everyone leaves shut it down yo
                socketRoom.close()
            }
            littleLock.unlock()
        }
        ws.onMessage { ctx ->
            littleLock.lock()
            handler.handle(ctx.message()).send(ctx, joined)
            littleLock.unlock()
        }
    }
    socketRoom.start()
    room.port = socketRoom.port()
    Timer().schedule(
        timerTask {
            littleLock.lock()
            if (joined.isEmpty()) {
                bigLock.lock()
                sys.deleteRoom(room.id)
                bigLock.unlock()
                socketRoom.close()
            }
            littleLock.unlock()
        },
        100000 // 100 seconds
    )
    return socketRoom
}

fun WsResponse.send(origin: WsContext, connected: Set<WsContext>) {
    this.reflect.fold({}) { origin.send(it) }
    this.propagate.fold({}) {
        for (ctx in connected) {
            if (ctx.sessionId != origin.sessionId) ctx.send(it)
        }
    }
}

@Serializable
data class CreateRoom(
    val playerID: String,
    val w: Int,
    val h: Int,
    val connect: Int,
    val gravity: Boolean,
)