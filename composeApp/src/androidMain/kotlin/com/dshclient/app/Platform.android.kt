package com.dshclient.app

import android.content.Context
import com.russhwolf.settings.Settings
import com.russhwolf.settings.SharedPreferencesSettings
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.okhttp.OkHttp

/** 由 MainActivity 注入 */
lateinit var appContext: Context

actual fun createHttpEngine(): HttpClientEngine = OkHttp.create {
    config {
        retryOnConnectionFailure(true)
        connectTimeout(java.time.Duration.ofSeconds(10))
        readTimeout(java.time.Duration.ofSeconds(30))
        writeTimeout(java.time.Duration.ofSeconds(30))
    }
}

actual fun createSettings(): Settings =
    SharedPreferencesSettings(appContext.getSharedPreferences("dsh_client_prefs", Context.MODE_PRIVATE))

actual fun platformName(): String = "Android"
