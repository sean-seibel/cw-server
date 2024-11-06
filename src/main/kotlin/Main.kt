import arrow.core.None
import arrow.core.Option
import arrow.core.getOrElse
import arrow.core.toOption
import io.javalin.Javalin
import io.javalin.http.HttpStatus.*
import io.javalin.websocket.WsContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.Timer
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.timerTask

fun main() {
    val app = makeServer()
    val sys = RoomSystem()

    val bigLock = ReentrantLock() // incredibly mystical and powerful solution
    app.before {
        // println("received ${it.method().name} ${it.fullUrl()}")
        bigLock.lock()
    }
    app.after { bigLock.unlock() }

    val mediumLock = ReentrantLock() // just for allocating, deallocating sockets

    // return serialized list of room data
    // basically whatever you would need to populate the list of rooms to choose from
    app.get("/rooms") { ctx ->
        ctx.result(Json.encodeToString(sys.roomData()))
        ctx.status(OK)
        println("memory (during run)=${ Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory() } bytes")
    }

    app.get("/player_id") { ctx ->
        ctx.result(generatePlayerID().id)
        ctx.status(OK)
        // println(ctx.ip())
    }

    app.post("/room_socket/{roomId}") { ctx ->
        sys.getRoom(RoomID(ctx.pathParam("roomId"))).fold({
            ctx.status(FORBIDDEN)
        }) { room ->
//            val playerID = PlayerID(ctx.body())
//            if (room.hasPlayer(playerID) || true) {
            val socketData = SocketData(room.port, room.socket.id)
            ctx.result(Json.encodeToString(socketData))
            ctx.status(OK)
//            } else {
//                ctx.result("PlayerID not found in room")
//                ctx.status(FORBIDDEN)
//            }
        }
    }

    app.post("/create_room") { ctx ->
        val data: CreateRoom
        try {
            // println("create_room decoding: \n${ctx.body()}")
            data = Json.decodeFromString<CreateRoom>(ctx.body())
        } catch (e: Exception) {
            ctx.status(BAD_REQUEST)
            println("create_room failed at decoding: \n${ctx.body()}")
            ctx.result("Malformed or incorrect JSON")
            return@post
        }
        try {
            mediumLock.lock()
            sys.createRoom(
                PlayerID(data.playerID),
                data.w,
                data.h,
                data.connect,
                data.gravity,
                data.minutes,
                data.increment,
            ).fold({
                ctx.status(FORBIDDEN)
            }) { room ->
                Timer().schedule(
                    timerTask {
                        sys.getRoom(room.id).ifSome { room ->
                            room.eventHandler(RoomEvent.EmptyCheck(room.id))
                        }
                    },
                    50000 // 50 seconds
                )
                room.eventHandler = {
                    if (it is RoomEvent.EmptyCheck) {
                        if (room.isEmpty()) {
                            mediumLock.lock()
                            sys.deleteRoom(it.roomID)
                            println("Shutting down ${it.roomID} for inactivity")
                            mediumLock.unlock()
                        }
                    }
                }
                ctx.result(room.id.id)
                ctx.status(CREATED)
            }
            mediumLock.unlock()
        } catch (e: IllegalArgumentException) {
            ctx.status(BAD_REQUEST)
            ctx.result("Invalid room parameters")
            mediumLock.unlock()
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

    repeat(5) {
        app.createSocketRoom(sys, mediumLock)
    }

    app.start(80) // default http
}

/**
 * Contractual obligation: any room assigned to this socket will never be deleted from under it
 */
fun Javalin.createSocketRoom(sys: RoomSystem, mediumLock: ReentrantLock): SocketID {
    val sock = generateSocketID()
    sys.declareSocket(sock)

    val path = "/socket/${sock.id}"
    // write this

    val joined: MutableSet<WsContext> = mutableSetOf()
    val joinedIDs: MutableMap<String, PlayerID> = mutableMapOf()

    val littleLock = ReentrantLock() // incredibly mystical and powerful solution parte tres

    var handler: Option<WsApiHandler> = None // reset this whenever we delete the room

    this.ws(path) { ws ->
        ws.onConnect { ctx ->
            littleLock.lock()
            mediumLock.lock()
            val room = sys.getSocketOccupant(sock).getOrElse {
                ctx.closeSession()
                littleLock.unlock()
                return@onConnect
            }
            if (handler.isNone()) {
                handler = WsApiHandler(room, {
                    littleLock.lock()
                    when (it) {
                        is RoomEvent.TimeOut -> {
                            Json.encodeToString(
                                SimpleResponse(
                                    when (it.player) {
                                        Room.Companion.ActivePlayer.ONE -> WsResponseHeader.P1TimeOut
                                        else -> WsResponseHeader.P2TimeOut
                                    }.asString
                                )
                            ).sendAll(joined)
                        }
                        is RoomEvent.GameStarted -> {
                            Json.encodeToString(
                                SimpleResponse(WsResponseHeader.GameStarted.asString)
                            ).sendAll(joined)
                        }
                        is RoomEvent.EmptyCheck -> {
                            if (joined.isEmpty() && room.isEmpty()) { // this will probably never happen
                                mediumLock.lock()
                                sys.deleteRoom(it.roomID)
                                handler = None
                                mediumLock.unlock()
                            }
                        }
                    }
                    littleLock.unlock()
                }) { handlerCtx, pid ->
                    joinedIDs[handlerCtx.sessionId] = pid
                }.toOption()
                // println("set handler")
            }
            joined.add(ctx)
            // println("Adding ${ctx.sessionId} to call")
            ctx.enableAutomaticPings(10, TimeUnit.SECONDS) // ping every 10s // disable this ??
            mediumLock.unlock()
            littleLock.unlock()
        }
        ws.onClose { ctx ->
            littleLock.lock()
            joined.remove(ctx)
            val pid = joinedIDs[ctx.sessionId]
            joinedIDs.remove(ctx.sessionId)
            // println("Dropping ${ctx.sessionId}")
            // println("Their pid was $pid")
            val room = sys.getSocketOccupant(sock).getOrElse {
                littleLock.unlock()
                return@onClose
            }
            if (pid != null && room.hasPlayer(pid)) {
                // println("Announcing disconnect of ${ctx.sessionId}")
                WsResponse(
                    None,
                    Json.encodeToString(
                        SimpleResponse(WsResponseHeader.OpponentDisconnect.asString)
                    ).toOption()
                ).send(ctx, joined)
            }
            if (joined.isEmpty()) {
                mediumLock.lock()
                sys.deleteRoom(room.id) // once everyone leaves shut it down yo
                handler = None
                mediumLock.unlock()
            }
            littleLock.unlock()
        }
        ws.onMessage { ctx ->
            littleLock.lock()
            handler.fold({
                ctx.closeSession() // harsh but also this probably won't happen
            }) { it.handle(ctx).send(ctx, joined) }
            littleLock.unlock()
        }
    }

    return sock
}

fun WsResponse.send(origin: WsContext, connected: Set<WsContext>) {
    this.reflect.fold({}) { origin.send(it) }
    this.propagate.fold({}) {
        for (ctx in connected) {
            if (ctx.sessionId != origin.sessionId) ctx.send(it)
        }
    }
}

fun String.sendAll(connected: Set<WsContext>) {
    for (ctx in connected) {
        ctx.send(this)
    }
}

fun <A> Option<A>.ifSome(run: (A) -> Unit) = this.fold({}, run)

@Serializable
data class CreateRoom(
    val playerID: String,
    val w: Int,
    val h: Int,
    val connect: Int,
    val gravity: Boolean,
    val minutes: Long,
    val increment: Long,
)