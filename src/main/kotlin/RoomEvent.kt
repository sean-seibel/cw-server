sealed class RoomEvent {
    class TimeOut(val player: Room.Companion.ActivePlayer) : RoomEvent()
    class EmptyCheck(val roomID: RoomID) : RoomEvent()
    object GameStarted : RoomEvent()
}