package org.aprsdroid.app.ui.viewmodel

import org.aprsdroid.app.service.ServiceEventBus

internal fun ServiceEventBus.resetForTesting() {
    resetState()
}
