enum class WsResponseHeader(val asString: String) {
    Malformed("MALFORMED"), Information("INFORMATION"), MoveResult("MOVE_RESULT"),
    OpponentMove("OPPONENT_MOVE"), OpponentResigned("OPPONENT_RESIGN"), Message("MESSAGE"),
    OpponentJoined("OPPONENT_JOINED"), Joined("JOINED"), Rejected("REJECTED")
}