import MoveResult.*
import arrow.core.None
import arrow.core.Option
import java.lang.StringBuilder

class GameBoard(val w: Int, val h: Int, val connect: Int, val gravity: Boolean) {
    companion object {
        const val EMPTY = ' '
        const val RED = 'R'
        const val YELLOW = 'Y'
        val OFFSETS = arrayOf(arrayOf(-1, -1), arrayOf(-1, 0), arrayOf(0, -1), arrayOf(1, -1))
    }

    init {
        if (w < 1 || h < 1 || connect < 1) {
            throw IllegalArgumentException("Invalid game parameters")
        }
    }

    val pieces = Array(w) { Array(h) { EMPTY } }
    var active = RED

    /**
     * One indexed
     */
    fun makeMove(c: Int, r: Option<Int> = None): MoveResult { // row , col from top left = 1,1
        if (c < 1 || c > w) return Invalid
        val column = c - 1
        if (gravity && pieces[column].last() != EMPTY) return Invalid
        val row = if (!gravity) {
            r.fold({ return@makeMove Malformed }) {
                if (it < 1 || it > h || pieces[column][it - 1] != EMPTY) return@makeMove Invalid
                it - 1
            }
        } else {
            pieces[column].indexOfFirst { it == EMPTY }
        }
        pieces[column][row] = active
        if (this.checkWin(row, column)) return Win
        for (checkRow in 0 until h) {
            for (checkCol in 0 until w) {
                if (pieces[checkRow][checkCol] == EMPTY) {
                    this.active = if (this.active == RED) YELLOW else RED
                    return Valid
                }
            }
        }
        return Draw
    }

    fun checkWin(fromR: Int, fromC: Int): Boolean {
        for ((offX, offY) in OFFSETS) {
            var connected = 1
            for (mult in arrayOf(1, -1)) {
                val rx = offX * mult
                val cx = offY * mult
                for (i in 1 until connect) {
                    val r = fromR + rx * i
                    val c = fromC + cx * i
                    if (r < 0 || r >= h || c < 0 || c >= w || pieces[c][r] != active) break
                    connected++
                }
            }
            if (connected >= connect) return true
        }
        return false
    }

    override fun toString(): String {
        val sb = StringBuilder()
        for (r in 0 until h) {
            for (c in 0 until w) {
                sb.append(pieces[c][r])
            }
            sb.append('\n')
        }
        return sb.toString()
    }
}