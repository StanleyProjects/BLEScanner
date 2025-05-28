package sp.sample.blescanner

import sp.ax.blescanner.BLEScannerService

internal class ScannerService : BLEScannerService(
    context = App.contexts.main,
)
