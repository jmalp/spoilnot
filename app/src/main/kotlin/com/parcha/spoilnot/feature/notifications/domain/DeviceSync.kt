package com.parcha.spoilnot.feature.notifications.domain

import com.parcha.spoilnot.core.coroutines.ApplicationScope
import com.parcha.spoilnot.feature.household.domain.Session
import com.parcha.spoilnot.feature.household.domain.SessionManager
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNot
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Registers this phone for digests once per signed-in user per app start, and again when FCM rotates the token.
 * While nobody is signed in the phone keeps no push token (DESIGN 10.2): it is deleted after every sign-out and at
 * every start without a signed-in user, which also stops digests to a device document that a sign-out left behind.
 * The steps run one at a time, so a sign-in right after a start registers only once the old token is gone.
 */
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
                .filterNot { it is Session.Loading }
                .map { (it as? Session.NoHousehold)?.user?.uid ?: (it as? Session.InHousehold)?.user?.uid }
                .distinctUntilChanged()
                .collect { uid -> if (uid == null) registry.deleteToken() else registry.sync(uid) }
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
