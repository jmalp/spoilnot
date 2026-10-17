package com.parcha.spoilnot.feature.notifications.domain

import com.parcha.spoilnot.core.coroutines.ApplicationScope
import com.parcha.spoilnot.feature.household.domain.Session
import com.parcha.spoilnot.feature.household.domain.SessionManager
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/** Registers this phone for digests once per signed-in user per app start, and again when FCM rotates the token. */
@Singleton
class DeviceSync @Inject constructor(
    private val session: SessionManager,
    private val registry: DeviceRegistry,
    @param:ApplicationScope private val scope: CoroutineScope,
) {
    private var started = false

    fun start() {
        if (started) return
        started = true
        scope.launch {
            session.session
                .map { (it as? Session.NoHousehold)?.user?.uid ?: (it as? Session.InHousehold)?.user?.uid }
                .distinctUntilChanged()
                .filterNotNull()
                .collect { uid -> registry.sync(uid) }
        }
    }

    /** FirebaseMessagingService.onNewToken. */
    fun onNewToken() {
        val uid = when (val s = session.session.value) {
            is Session.NoHousehold -> s.user.uid
            is Session.InHousehold -> s.user.uid
            else -> return
        }
        scope.launch { registry.sync(uid) }
    }
}
