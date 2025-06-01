package sp.ax.blescanner

import android.app.NotificationChannel
import android.app.NotificationManager

internal fun mockNotificationChannel(
    id: String = "MockNotificationChannel:id",
    name: CharSequence = "MockNotificationChannel:name",
    importance: Int = NotificationManager.IMPORTANCE_HIGH,
): NotificationChannel {
    return NotificationChannel(id, name, importance)
}
