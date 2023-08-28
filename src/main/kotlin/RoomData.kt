import kotlinx.serialization.Serializable

@Serializable
data class RoomData(
    val id: String,
    val w: Int,
    val h: Int,
    val connect: Int,
    val numPlayers: Int,
    val gravity: Boolean
)