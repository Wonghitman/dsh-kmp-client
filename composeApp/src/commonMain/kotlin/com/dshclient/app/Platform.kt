package com.dshclient.app

import io.ktor.client.engine.HttpClientEngine
import com.russhwolf.settings.Settings

expect fun createHttpEngine(): HttpClientEngine

expect fun createSettings(): Settings

expect fun platformName(): String
