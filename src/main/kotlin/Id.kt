@JvmInline
value class PlayerID(val id: String)

@JvmInline
value class RoomID(val id: String)

@JvmInline
value class SocketID(val id: String)

fun generatePlayerID(): PlayerID {
    return PlayerID(generateID())
}

fun generateRoomID(): RoomID {
    return RoomID(generateID())
}

fun generateSocketID(): SocketID {
    return SocketID(generateID())
}

/**
 * For room, player IDs.
 * Essentially unique.
 */
fun generateID() : String {
    val allowedChars = ('A'..'Z') + ('a'..'z') + ('0'..'9')
    return (1..32)
        .map { allowedChars.random() }
        .joinToString("")
}