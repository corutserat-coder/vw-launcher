package com.vw.carlauncher
import android.app.Notification
import android.content.Intent
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
class NavNotificationListener : NotificationListenerService() {
    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        val pkg = sbn?.packageName ?: return
        if (pkg == "com.google.android.apps.maps" || pkg == "ru.yandex.yandexmaps") {
            val extras = sbn.notification.extras
            val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: ""
            val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""
            sendBroadcast(Intent("com.vw.carlauncher.NAV_UPDATE").apply {
                putExtra("distance", title)
                putExtra("street", text)
            })
        }
    }
}
