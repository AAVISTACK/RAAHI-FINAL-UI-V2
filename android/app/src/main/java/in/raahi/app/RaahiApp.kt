package `in`.raahi.app

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import `in`.raahi.app.push.RaahiNotifications

@HiltAndroidApp
class RaahiApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // Must exist before the first FCM message arrives — notification channels are
        // mandatory on API 26+, which is this app's exact minSdk, so there's no legacy
        // pre-channel code path to fall back to.
        RaahiNotifications.createChannel(this)
    }
}
