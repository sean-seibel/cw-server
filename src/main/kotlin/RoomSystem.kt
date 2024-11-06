import arrow.core.None
import arrow.core.Option
import arrow.core.Some
import arrow.core.getOrNone
import kotlinx.serialization.Serializable

class RoomSystem {
    companion object {
        const val MAX_ROOMS = 5
    }
    private val rooms: MutableMap<RoomID, Room> = mutableMapOf()
    private val occupiedSockets: MutableMap<SocketID, Room> = mutableMapOf()
    private val sockets: MutableSet<SocketID> = mutableSetOf()

    fun createRoom(
        playerID: PlayerID,
        w: Int, h: Int, connect: Int,
        gravity: Boolean,
        minutes: Long,
        increment: Long
    ): Option<Room> {
        if (rooms.size >= MAX_ROOMS) return None
        if (sockets.isEmpty()) return None
        val chosenSocket = sockets.first()
        val newRoom = Room(w, h, connect, gravity, minutes * 60, increment, chosenSocket)
        sockets.remove(chosenSocket)
        occupiedSockets[chosenSocket] = newRoom
        rooms[newRoom.id] = newRoom
        return Some(newRoom)
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

    /**
     * The only way to delete a room.
     *
     * Also frees the socket.
     */
    fun deleteRoom(roomID: RoomID): Boolean {
        return if (rooms.containsKey(roomID)) {
            val sock = rooms[roomID]!!.socket
            if (occupiedSockets.containsKey(sock)) {
                occupiedSockets.remove(sock)
                sockets.add(sock)
            }
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

    fun declareSocket(socketID: SocketID) = sockets.add(socketID)
    fun getSocketOccupant(socketID: SocketID) = occupiedSockets.getOrNone(socketID)
}

@Serializable
data class SocketData(
    val port: Int,
    val socketID: String
)