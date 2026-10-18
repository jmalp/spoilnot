package com.parcha.spoilnot.feature.update.domain

import kotlinx.coroutines.flow.Flow

/** config/app (DESIGN 8.2, 13.3): read-only for clients, edited in the console. */
data class AppConfig(val minSupportedBuild: Int, val message: String?, val updateUrl: String?)

interface AppConfigRepository {
    /** The document, or null when it does not exist. */
    fun observe(): Flow<AppConfig?>
}

object UpdateGate {
    /** True when this build is older than the oldest one the household data still supports. */
    fun required(config: AppConfig?, versionCode: Int): Boolean =
        config != null && versionCode < config.minSupportedBuild
}
