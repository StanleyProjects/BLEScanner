package sp.ax.blescanner

import android.app.Notification

internal class MockScannerService : BLEScannerService(
    main = MockEnvironment.main,
    scanner = MockEnvironment.scanner ?: error("No scanner!"),
    channel = mockNotificationChannel(),
) {
    override fun onStartNotification(): Notification {
        return Notification()
    }
}
