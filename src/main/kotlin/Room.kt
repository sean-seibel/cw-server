import arrow.core.None
import arrow.core.Option
import arrow.core.Some
import arrow.core.getOrElse
import kotlinx.serialization.Serializable
import java.util.*
import kotlin.concurrent.schedule
import kotlin.random.Random
import kotlin.time.ExperimentalTime
import kotlin.time.TimeSource

@OptIn(ExperimentalTime::class)
class Room(
    val w: Int,
    val h: Int,
    val connect: Int,
    val gravity: Boolean,
    val time: Long = 300, // in seconds
    val increment: Long = 15, // in seconds
    var eventHandler: (RoomEvent) -> Unit = {},
    ) {
    companion object {
        enum class ActivePlayer(val asString: String) { ONE("ONE"), TWO("TWO"), NONE("NONE") }
    }
    val id = generateRoomID()

    var Player1: Option<PlayerID> = None // red player
    var p1time = time * 1000 // in ms now
    var Player2: Option<PlayerID> = None // yellow player
    var p2time = time * 1000 // in ms now

    var sinceLastMove = TimeSource.Monotonic.markNow()
    lateinit var activeTask: TimerTask

    var gameStarted = false

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

    private fun checkStart() {
        gameStarted = Player1.isSome() && Player2.isSome()
        if (gameStarted) {
            eventHandler(RoomEvent.GameStarted)
            startTimerForActivePlayer()
        }
    }

    private fun stopTimeForActivePlayer() {
        // cancel last scheduled event
        activeTask.cancel()
        // subtract elapsed time from active players remaining time
        when (activePlayer) {
            ActivePlayer.ONE -> {
                p1time -= (sinceLastMove.elapsedNow().inWholeMilliseconds - increment * 1000)
            }
            else -> {
                p2time -= (sinceLastMove.elapsedNow().inWholeMilliseconds - increment * 1000)
            }
        }
    }

    private fun startTimerForActivePlayer() {
        // set time since last move
        sinceLastMove = TimeSource.Monotonic.markNow()
        // schedule Timeout event for this player
        val delay = when (activePlayer) {
            ActivePlayer.ONE -> p1time
            else -> p2time
        }
        activeTask = Timer().schedule(delay) {
            gameStarted = false
            eventHandler(RoomEvent.TimeOut(activePlayer))
        }
    }

    fun join(playerID: PlayerID): Boolean {
        if (!eligibleToJoin(playerID)) return false
        if (Player1.isNone()) {
            if (Player2.isNone() && Random.nextBoolean()) { // if both are empty, 50/50 which one gets the first player
                Player2 = Some(playerID)
                checkStart()
                return true
            }
            Player1 = Some(playerID)
            checkStart()
            return true
        }
        if (Player2.isNone()) {
            Player2 = Some(playerID)
            checkStart()
            return true
        }
        return false
    }

    fun makeMove(column: Int, row: Option<Int>): MoveResult {
        if (!gameStarted) return MoveResult.Invalid
        val res = gameBoard.makeMove(column, row)
        if (res == MoveResult.Valid) {
            stopTimeForActivePlayer()
            activePlayer = if (gameBoard.active == GameBoard.RED) ActivePlayer.ONE else ActivePlayer.TWO
            madeMoves++
            startTimerForActivePlayer()
        }
        if (res.isOver()) {
            stopTimeForActivePlayer()
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
            time,
            increment,
            p1time,
            p2time
        )
    }
}

@Serializable
data class BoardData(
    val w: Int,
    val h: Int,
    val connect: Int,
    val board: Array<Array<Byte>>,
    val activePlayer: String,
    val madeMoves: Int,
    val gravity: Boolean,
    val time: Long,
    val increment: Long,
    val p1time: Long,
    val p2time: Long,
)