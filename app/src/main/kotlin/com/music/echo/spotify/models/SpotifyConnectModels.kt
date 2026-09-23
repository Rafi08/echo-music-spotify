package echo.music.iad1tya.spotify.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class SpotifyConnectDevice(
  val id: String,
  val name: String,
  val type: String,
  @SerialName("is_active") val isActive: Boolean = false,
  @SerialName("is_private_session") val isPrivateSession: Boolean = false,
  @SerialName("is_restricted") val isRestricted: Boolean = false,
  @SerialName("volume_percent") val volumePercent: Int? = null,
)

@Serializable
data class SpotifyConnectDevices(
  val devices: List<SpotifyConnectDevice> = emptyList(),
)
