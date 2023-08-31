sealed class RoomEvent {
    class TimeOut(val player: Room.Companion.ActivePlayer) : RoomEvent()
    object GameStarted : RoomEvent()
}