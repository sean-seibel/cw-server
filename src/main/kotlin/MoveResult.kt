enum class MoveResult(val asString: String) {
    Valid("Valid"), Invalid("Invalid"), Win("Win"), Draw("Draw"), Malformed("Malformed");

    fun isOver(): Boolean {
        return when (this) {
            Valid, Invalid, Malformed -> false
            Win, Draw -> true
        }
    }
}