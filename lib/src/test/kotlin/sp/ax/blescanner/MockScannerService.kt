package sp.ax.blescanner

import android.app.Notification
import android.app.NotificationChannel

internal class MockScannerService : BLEScannerService(
    main = MockEnvironment.main,
    scanner = MockEnvironment.scanner,
    channel = mockNotificationChannel(),
) {
    override fun onStartNotification(channel: NotificationChannel): Notification {
        TODO("MockScannerService:onStartNotification($channel")
    }
}
