package echo.music.iad1tya.spotify

sealed interface SpotifySoloistEvent {
  data class AuthState(
    val loggedIn: Boolean,
    val deviceName: String?,
    val active: Boolean?,
  ) : SpotifySoloistEvent

  data class PlaybackState(
    val status: String?,
    val trackUri: String?,
    val positionMs: Long,
    val durationMs: Long,
    val volume: Int?,
    val shuffle: Boolean?,
    val repeat: String?,
  ) : SpotifySoloistEvent

  data class CommandResult(
    val command: String?,
    val success: Boolean,
    val error: String?,
  ) : SpotifySoloistEvent

  data class Raw(val payload: String) : SpotifySoloistEvent
}
