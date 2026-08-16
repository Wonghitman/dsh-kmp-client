package com.dshclient.app

import com.russhwolf.settings.Settings
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.darwin.Darwin
import platform.Foundation.NSUserDefaults

actual fun createHttpEngine(): HttpClientEngine = Darwin.create()

actual fun createSettings(): Settings = com.russhwolf.settings.NSUserDefaultsSettings(
    NSUserDefaults.standardUserDefaults,
)

actual fun platformName(): String = "iOS"
