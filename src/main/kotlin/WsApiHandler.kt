import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import WsResponseHeader.*
import arrow.core.None
import arrow.core.Option
import arrow.core.toOption
import kotlinx.serialization.encodeToString

class WsApiHandler(
    private val room: Room,
    handleRoomEvents: (RoomEvent) -> Unit,
) {
    init {
        room.eventHandler = handleRoomEvents
    }

    fun handle(msgReceived: String): WsResponse {
        // println("trying to parse : $msgReceived")
        val playerID: String
        val move: Int?
        val moveRow: Option<Int>
        val message: String?
        val resigns: Boolean?
        val joining: Boolean?
        try {
            val (nplayerID, nmove, nmoveRow, nmessage, nresigns, njoining) =
                Json.decodeFromString<WebSocketIn>(msgReceived)
            playerID = nplayerID
            move = nmove
            moveRow = nmoveRow.toOption()
            message = nmessage
            resigns = nresigns
            joining = njoining
        } catch (e: Exception) { // bad json
            // println("failed to parse : $msgReceived")
            return WsResponse(
                reflect = Json.encodeToString(SimpleResponse(
                    Malformed.asString
                )).toOption()
            )
        }
        if (joining != null) {
            return if (joining && room.join(PlayerID(playerID))) {
                // println("joining")
                WsResponse(
                    reflect = Json.encodeToString(SimpleResponse(Joined.asString)).toOption(),
                    propagate = Json.encodeToString(SimpleResponse(OpponentJoined.asString)).toOption()
                )
            } else {
                println("rejected $playerID")
                println(room.Player1)
                println(room.Player2)
                WsResponse(
                    reflect = Json.encodeToString(SimpleResponse(Rejected.asString)).toOption()
                )
            }
        }
        if (!room.hasPlayer(PlayerID(playerID))) {
            return WsResponse(None) // don't not be in the room
        }
        if (move != null) { // make move
            if (!room.validateMoveFrom(PlayerID(playerID))) {
                return WsResponse(
                    Json.encodeToString(MoveResultResponse(
                        move,
                        MoveResult.Malformed.asString,
                        room.boardData(),
                        WsResponseHeader.MoveResult.asString
                    )).toOption()
                )
            } // move is legal
            val res = room.makeMove(move, moveRow)
            val reflect = MoveResultResponse(
                move,
                res.asString,
                room.boardData(),
                WsResponseHeader.MoveResult.asString
            )
            if (res == MoveResult.Invalid) {
                return WsResponse(Json.encodeToString(reflect).toOption(), endRoom = res.isOver())
            }
            return WsResponse(
                Json.encodeToString(reflect).toOption(),
                Json.encodeToString(
                    reflect.copy(header = OpponentMove.asString)
                ).toOption(),
                endRoom = res.isOver()
            )
        }
        if (message != null) {
            return WsResponse(
                reflect = None,
                propagate = Json.encodeToString(
                    MessageResponse(message, Message.asString)
                ).toOption()
            )
        }
        if (resigns != null) {
            if (resigns) {
                return WsResponse(
                    reflect = None,
                    propagate = Json.encodeToString(
                        SimpleResponse(OpponentResigned.asString)
                    ).toOption()
                )
            }
        }
        // information
        return WsResponse(
            Json.encodeToString(InformationResponse(
                room.getIdentity(PlayerID(playerID)).asString,
                room.boardData(),
                header = Information.asString
            )).toOption()
        )
    }
}

data class WsResponse(
    val reflect: Option<String>, // send back to original sender
    val propagate: Option<String> = None, // notify other connections
    val endRoom: Boolean = false,
)

@Serializable // per contract, sender should only fill out one of these fields at a time
data class WebSocketIn(
    val playerID: String,
    val move: Int? = null,
    val moveRow: Int? = null,
    val message: String? = null,
    val resigns: Boolean? = null,
    val joining: Boolean? = null,
)

@Serializable
data class SimpleResponse(
    val header: String
)

@Serializable
data class MoveResultResponse(
    val move: Int, // only tells you column
    val result: String,
    val boardData: BoardData, // but this tells you everything
    val header: String,
)

@Serializable
data class InformationResponse(
    val role: String,
    val boardData: BoardData,
    val header: String,
)

@Serializable
data class MessageResponse(
    val message: String,
    val header: String
)

