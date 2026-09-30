package `in`.raahi.app.network

import `in`.raahi.app.data.TokenManager
import com.google.gson.Gson
import com.google.gson.JsonObject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import javax.inject.Inject
import javax.inject.Singleton

sealed class WsEvent {
    data class JobStatusChange(val jobId: String, val status: String) : WsEvent()
    data class JobHelperLocation(val jobId: String, val lat: Double, val lng: Double) : WsEvent()
    data class SosNearbyAlert(val sosId: String, val lat: Double, val lng: Double) : WsEvent()
}

enum class WsConnectionState { DISCONNECTED, CONNECTING, CONNECTED }

/**
 * Backend contract (see API_CONTRACT.md): identity comes from the Authorization header on
 * the handshake request, never a query param — the same Bearer token every REST call uses.
 * okhttp3's WebSocket handshake supports arbitrary headers on the initial Request, so this
 * sets it exactly the way the backend's AuthHandshakeInterceptor expects.
 */
@Singleton
class RaahiWebSocketClient @Inject constructor(
    private val okHttpClient: OkHttpClient,
    private val tokenManager: TokenManager,
) {
    private val gson = Gson()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _events = MutableSharedFlow<WsEvent>(extraBufferCapacity = 16)
    val events: SharedFlow<WsEvent> = _events

    private val _connectionState = MutableSharedFlow<WsConnectionState>(replay = 1, extraBufferCapacity = 4)
    val connectionState: SharedFlow<WsConnectionState> = _connectionState

    private var socket: WebSocket? = null
    private var shouldReconnect = false
    private var reconnectAttempt = 0

    fun connect(baseHttpUrl: String) {
        shouldReconnect = true
        scope.launch { openSocket(baseHttpUrl) }
    }

    fun disconnect() {
        shouldReconnect = false
        socket?.close(1000, "client disconnect")
        socket = null
    }

    private suspend fun openSocket(baseHttpUrl: String) {
        val token = tokenManager.getToken() ?: return
        val wsUrl = toWsUrl(baseHttpUrl)
        _connectionState.tryEmit(WsConnectionState.CONNECTING)

        val request = Request.Builder()
            .url(wsUrl)
            .addHeader("Authorization", "Bearer $token")
            .build()

        socket = okHttpClient.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                reconnectAttempt = 0
                _connectionState.tryEmit(WsConnectionState.CONNECTED)
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                parseEvent(text)?.let { _events.tryEmit(it) }
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                webSocket.close(1000, null)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                _connectionState.tryEmit(WsConnectionState.DISCONNECTED)
                scheduleReconnect(baseHttpUrl)
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                _connectionState.tryEmit(WsConnectionState.DISCONNECTED)
                scheduleReconnect(baseHttpUrl)
            }
        })
    }

    fun sendJobLocationUpdate(jobId: String, lat: Double, lng: Double) {
        val payload = """{"event":"job:location_update","data":{"job_id":"$jobId","lat":$lat,"lng":$lng}}"""
        socket?.send(payload)
    }

    private fun scheduleReconnect(baseHttpUrl: String) {
        if (!shouldReconnect) return
        reconnectAttempt++
        val backoffMs = minOf(30_000L, 2_000L * reconnectAttempt)
        scope.launch {
            delay(backoffMs)
            if (shouldReconnect) openSocket(baseHttpUrl)
        }
    }
    private fun parseEvent(text: String): WsEvent? = runCatching {
        val root = gson.fromJson(text, JsonObject::class.java)
        val event = root.get("event")?.asString ?: return null
        val data = root.getAsJsonObject("data") ?: return null
        when (event) {
            "job:status_change" -> WsEvent.JobStatusChange(
                jobId = data.get("jobId").asString, status = data.get("status").asString,
            )
            "job:helper_location" -> WsEvent.JobHelperLocation(
                jobId = data.get("jobId").asString, lat = data.get("lat").asDouble, lng = data.get("lng").asDouble,
            )
            "sos:nearby_alert" -> WsEvent.SosNearbyAlert(
                sosId = data.get("sosId").asString, lat = data.get("lat").asDouble, lng = data.get("lng").asDouble,
            )
            else -> null
        }
    }.getOrNull()

    private fun toWsUrl(baseHttpUrl: String): String {
        val httpUrl = baseHttpUrl.toHttpUrl()
        val scheme = if (httpUrl.isHttps) "wss" else "ws"
        val portPart = if ((httpUrl.isHttps && httpUrl.port != 443) || (!httpUrl.isHttps && httpUrl.port != 80)) ":${httpUrl.port}" else ""
        return "$scheme://${httpUrl.host}$portPart/ws"
    }
}
