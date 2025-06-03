package sp.ax.blescanner

import android.app.Notification
import android.app.NotificationChannel

internal class MockScannerService : BLEScannerService(
    main = MockEnvironment.main,
    scanner = MockEnvironment.scanner ?: error("No scanner!"),
    channel = mockNotificationChannel(),
) {
    override fun onStartNotification(channel: NotificationChannel): Notification {
        return Notification()
    }
}
