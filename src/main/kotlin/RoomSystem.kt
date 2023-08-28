import arrow.core.None
import arrow.core.Option
import arrow.core.Some
import arrow.core.getOrNone
import kotlinx.serialization.Serializable

class RoomSystem {
    companion object {
        const val MAX_ROOMS = 50
    }
    private val rooms: MutableMap<RoomID, Room> = mutableMapOf()
    // private val playersInRooms: HashSet<PlayerID> = hashSetOf() // protect against multiboxing later

    fun createRoom(playerID: PlayerID, w: Int, h: Int, connect: Int, gravity: Boolean): Option<RoomID> {
        if (rooms.size >= MAX_ROOMS) return None
        val newRoom = Room(w, h, connect, gravity)
//        newRoom.join(playerID)
        rooms[newRoom.id] = newRoom
        return Some(newRoom.id)
    }

    fun roomData(): List<RoomData> {
        return rooms.values.map { it.toData() }
    }

    fun roomIsOpenTo(roomID: RoomID, playerID: PlayerID): Boolean {
        return getRoom((roomID)).fold({false}) { room ->
            room.eligibleToJoin(playerID)
        }
    }

    fun joinRoom(playerID: PlayerID, roomID: RoomID): Boolean {
        return getRoom(roomID).fold({false}) { room ->
            room.join(playerID)
        }
    }

    fun roomSocket(roomID: RoomID): Option<SocketData> {
        return getRoom(roomID).fold({None}) { room ->
            Some(SocketData(
                room.port,
                room.socket.id
            ))
        }
    }

    fun deleteRoom(roomID: RoomID): Boolean {
        return if (rooms.containsKey(roomID)) {
            rooms.remove(roomID)
            true
        } else false
    }

    fun playerInRoom(playerID: PlayerID, roomID: RoomID): Boolean {
        return getRoom(roomID).fold({ false }) { room ->
            room.hasPlayer(playerID)
        }
    }

    fun getRoom(roomID: RoomID) = rooms.getOrNone(roomID)
}

@Serializable
data class SocketData(
    val port: Int,
    val socketID: String
)