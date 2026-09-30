package `in`.raahi.app.push

import android.util.Log
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import dagger.hilt.android.AndroidEntryPoint
import `in`.raahi.app.data.AuthRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class RaahiFirebaseMessagingService : FirebaseMessagingService() {

    @Inject lateinit var authRepository: AuthRepository

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        // Fires on first install and whenever FCM rotates the token — the backend must be
        // told every time, not just once at sign-in, or delivery silently breaks after a
        // rotation. If the user isn't signed in yet, AuthRepository.registerFcmToken just
        // no-ops (no auth token to call the API with) and the real one is sent right after
        // sign-in instead (see AuthViewModel).
        CoroutineScope(Dispatchers.IO).launch {
            runCatching { authRepository.registerFcmToken(token) }
                .onFailure { Log.w("RaahiFCM", "Could not register FCM token: ${it.message}") }
        }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        // Notification-only payloads are shown automatically by the system when the app is
        // backgrounded, but NOT when foregrounded — that gap is what RaahiNotifications.show
        // closes. Every push this backend actually sends (see NotificationService.java) sets
        // both notification.title/body AND a data payload, so falling back to the data map's
        // own title/body (when a message is data-only) covers both real shapes without
        // fabricating content that wasn't sent.
        val title = message.notification?.title ?: message.data["title"] ?: "Raahi"
        val body = message.notification?.body ?: message.data["body"] ?: return
        RaahiNotifications.show(applicationContext, title, body, message.data)
    }
}
