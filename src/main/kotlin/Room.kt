import arrow.core.None
import arrow.core.Option
import arrow.core.Some
import arrow.core.getOrElse
import kotlinx.serialization.Serializable

class Room(val w: Int, val h: Int, val connect: Int, val gravity: Boolean) {
    companion object {
        enum class ActivePlayer(val asString: String) { ONE("ONE"), TWO("TWO"), NONE("NONE") }
    }
    val id = generateRoomID()

    var Player1: Option<PlayerID> = None // red player
    var Player2: Option<PlayerID> = None // yellow player
    var activePlayer = ActivePlayer.ONE

    val socket = generateSocketID()
    var port = -1

    val gameBoard = GameBoard(w, h, connect, gravity)

    var madeMoves = 0

    fun eligibleToJoin(playerID: PlayerID): Boolean {
        return Player1.fold({
            Player2.fold({ true }) {p2 -> p2 != playerID }
        }) { p1 ->
            p1 != playerID &&
            Player2.fold({ true }) {false}
        }
    }

    fun join(playerID: PlayerID): Boolean {
        if (!eligibleToJoin(playerID)) return false
        if (Player1.isNone()) {
            Player1 = Some(playerID)
            return true
        }
        if (Player2.isNone()) {
            Player2 = Some(playerID)
            return true
        }
        return false
    }

    fun gameActive(): Boolean {
        return Player1.isSome() && Player2.isSome()
    }

    fun makeMove(column: Int, row: Option<Int>): MoveResult {
        val res = gameBoard.makeMove(column, row)
        if (res == MoveResult.Valid) {
            activePlayer = if (gameBoard.active == GameBoard.RED) ActivePlayer.ONE else ActivePlayer.TWO
            madeMoves++
        }
        return res
    }

    fun getIdentity(playerID: PlayerID): ActivePlayer {
        if (Player1.getOrNull() == playerID) { return ActivePlayer.ONE }
        if (Player2.getOrNull() == playerID) { return ActivePlayer.TWO }
        return ActivePlayer.NONE
    }

    fun validateMoveFrom(playerID: PlayerID): Boolean {
        return when (activePlayer) {
            ActivePlayer.ONE -> playerID == Player1.getOrNull()
            ActivePlayer.TWO -> playerID == Player2.getOrNull()
            ActivePlayer.NONE -> false
        }
    }

    fun getActivePlayer(): PlayerID {
        return when (activePlayer) {
            ActivePlayer.ONE -> Player1.getOrElse { PlayerID("") }
            ActivePlayer.TWO -> Player2.getOrElse { PlayerID("") }
            ActivePlayer.NONE -> PlayerID("")
        }
    }

    fun hasPlayer(playerID: PlayerID): Boolean {
        return Player1 == Some(playerID) || Player2 == Some(playerID)
    }

    fun toData(): RoomData {
        return RoomData(
            id = id.id,
            w = w,
            h = h,
            connect = connect,
            numPlayers = Player1.fold({0}) { 1 } + Player2.fold({0}) { 1 },
            gravity = gravity
        )
    }

    fun boardData(): BoardData {
        return BoardData(w, h, connect,
            gameBoard.pieces,
            activePlayer.asString,
            madeMoves,
            gravity,
        )
    }
}

@Serializable
data class BoardData(
    val w: Int,
    val h: Int,
    val connect: Int,
    val board: Array<Array<Char>>,
    val activePlayer: String,
    val madeMoves: Int,
    val gravity: Boolean,
)