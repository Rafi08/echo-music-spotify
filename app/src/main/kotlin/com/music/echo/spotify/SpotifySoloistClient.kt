package echo.music.iad1tya.spotify

import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject

/** Client for the optional local WebSocket API exposed by Spotify Soloist. */
class SpotifySoloistClient(
  private val httpClient: OkHttpClient = OkHttpClient(),
) {
  private var webSocket: WebSocket? = null
  private val closed = AtomicBoolean(true)

  var onEvent: ((SpotifySoloistEvent) -> Unit)? = null
  var onError: ((Throwable) -> Unit)? = null
  var onClosed: (() -> Unit)? = null

  suspend fun connect(
    host: String,
    port: Int,
    apiKey: String? = null,
  ) = withContext(Dispatchers.IO) {
    check(webSocket == null) { "Spotify Soloist is already connected" }
    require(port in 1..65535) { "Invalid Spotify Soloist port: $port" }

    val opened = CompletableDeferred<Unit>()
    val requestBuilder = Request.Builder().url("ws://$host:$port")
    apiKey?.takeIf { it.isNotBlank() }?.let { requestBuilder.header("Authorization", "Bearer $it") }

    closed.set(false)
    webSocket =
      httpClient.newWebSocket(
        requestBuilder.build(),
        object : WebSocketListener() {
          override fun onOpen(webSocket: WebSocket, response: Response) {
            response.close()
            opened.complete(Unit)
          }

          override fun onMessage(webSocket: WebSocket, text: String) {
            handleMessage(text)
          }

          override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
            webSocket.close(code, reason)
          }

          override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            closeState()
          }

          override fun onFailure(webSocket: WebSocket, throwable: Throwable, response: Response?) {
            response?.close()
            closeState()
            if (!opened.isCompleted) opened.completeExceptionally(throwable)
            onError?.invoke(throwable)
          }
        },
      )

    opened.await()
  }

  fun requestAuthState(): Boolean = sendCommand("get_auth_state")

  fun requestState(): Boolean = sendCommand("get_state")

  fun activate(): Boolean = sendCommand("activate")

  fun deactivate(): Boolean = sendCommand("deactivate")

  fun requestQueue(limit: Int = 10): Boolean =
    sendCommand("get_queue", JSONObject().put("limit", limit.coerceAtLeast(0)))

  fun play(): Boolean = sendCommand("play")

  fun pause(): Boolean = sendCommand("pause")

  fun skipNext(): Boolean = sendCommand("skip_next")

  fun skipPrevious(): Boolean = sendCommand("skip_prev")

  fun seek(positionMs: Long): Boolean =
    sendCommand("seek", JSONObject().put("position_ms", positionMs.coerceAtLeast(0)))

  fun setVolume(volume: Int): Boolean =
    sendCommand("set_volume", JSONObject().put("volume", volume.coerceIn(0, 100)))

  fun setShuffle(enabled: Boolean): Boolean =
    sendCommand("set_shuffle", JSONObject().put("enabled", enabled))

  fun setRepeatContext(enabled: Boolean): Boolean =
    sendCommand("set_repeat_context", JSONObject().put("enabled", enabled))

  fun setRepeatTrack(enabled: Boolean): Boolean =
    sendCommand("set_repeat_track", JSONObject().put("enabled", enabled))

  fun addToQueue(uri: String): Boolean =
    sendCommand("add_to_queue", JSONObject().put("uri", uri))

  fun close() {
    webSocket?.close(1000, "Client closed")
    closeState()
  }

  private fun sendCommand(command: String, payload: JSONObject? = null): Boolean {
    val message = JSONObject().put("type", "command").put("command", command)
    payload?.keys()?.forEach { key -> message.put(key, payload.get(key)) }
    return webSocket?.send(message.toString()) == true
  }

  private fun handleMessage(text: String) {
    try {
      val message = JSONObject(text)
      when (message.optString("type")) {
        "auth_state" ->
          onEvent?.invoke(
            SpotifySoloistEvent.AuthState(
              loggedIn = message.optBoolean("logged_in"),
              deviceName = message.optString("device_name").ifBlank { null },
              active = message.optNullableBoolean("is_active"),
            ),
          )
        "playback_state", "playback_changed" -> onEvent?.invoke(parsePlaybackState(message))
        "command_result" ->
          onEvent?.invoke(
            SpotifySoloistEvent.CommandResult(
              command = message.optString("command").ifBlank { null },
              success = message.optBoolean("success", true),
              error = message.optString("error").ifBlank { null },
            ),
          )
        else -> onEvent?.invoke(SpotifySoloistEvent.Raw(text))
      }
    } catch (error: Exception) {
      onError?.invoke(error)
    }
  }

  private fun parsePlaybackState(message: JSONObject): SpotifySoloistEvent.PlaybackState {
    val item = message.optJSONObject("item")
    val playback = item?.optJSONObject("decorations")?.optJSONObject("playback")
      ?: message.optJSONObject("playback")
    val options = message.optJSONObject("options")
    val position = message.optJSONObject("position")
    return SpotifySoloistEvent.PlaybackState(
      status = message.optString("status").ifBlank { null },
      trackUri = item?.optString("uri")?.ifBlank { null },
      positionMs = position?.optLong("position_ms", 0L) ?: message.optLong("position_ms", 0L),
      durationMs = playback?.optLong("duration_ms", 0L) ?: 0L,
      volume = message.optNullableInt("volume"),
      shuffle = options?.optNullableBoolean("shuffle") ?: message.optNullableBoolean("shuffle"),
      repeat = options?.optString("repeat")?.ifBlank { null }
        ?: message.optString("repeat").ifBlank { null },
    )
  }

  private fun closeState() {
    if (closed.compareAndSet(false, true)) {
      webSocket = null
      onClosed?.invoke()
    }
  }

  private fun JSONObject.optNullableBoolean(name: String): Boolean? =
    if (has(name) && !isNull(name)) optBoolean(name) else null

  private fun JSONObject.optNullableInt(name: String): Int? =
    if (has(name) && !isNull(name)) optInt(name) else null
}
