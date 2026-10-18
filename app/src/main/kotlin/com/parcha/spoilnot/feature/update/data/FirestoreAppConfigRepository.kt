package com.parcha.spoilnot.feature.update.data

import com.parcha.spoilnot.core.firebase.FirestoreProvider
import com.parcha.spoilnot.core.firebase.snapshotFlow
import com.parcha.spoilnot.feature.update.domain.AppConfig
import com.parcha.spoilnot.feature.update.domain.AppConfigRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map

@OptIn(ExperimentalCoroutinesApi::class)
class FirestoreAppConfigRepository @Inject constructor(private val firestore: FirestoreProvider) : AppConfigRepository {
    // One document; a missing or unreadable one never blocks the app.
    override fun observe(): Flow<AppConfig?> = firestore.instance
        .flatMapLatest { db -> db.document("config/app").snapshotFlow() }
        .map { doc ->
            if (!doc.exists()) {
                null
            } else {
                AppConfig(
                    minSupportedBuild = doc.getLong("minSupportedBuild")?.toInt() ?: 0,
                    message = doc.getString("message")?.takeIf { it.isNotBlank() },
                    updateUrl = doc.getString("updateUrl")?.takeIf { it.startsWith("https://") },
                )
            }
        }
        .catch { emit(null) }
        .distinctUntilChanged()
}

@Module
@InstallIn(SingletonComponent::class)
interface UpdateDataModule {
    @Binds
    fun appConfigRepository(impl: FirestoreAppConfigRepository): AppConfigRepository
}
