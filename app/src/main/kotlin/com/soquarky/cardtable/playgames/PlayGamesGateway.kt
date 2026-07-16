package com.soquarky.cardtable.playgames

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

sealed interface PlayerIdentityState {
    data object Guest : PlayerIdentityState
    data object Checking : PlayerIdentityState
    data class Authenticated(val playerId: String, val displayName: String) : PlayerIdentityState
    data class Error(val message: String) : PlayerIdentityState
}

interface PlayGamesGateway {
    val identity: Flow<PlayerIdentityState>
    suspend fun checkAuthentication()
    suspend fun showAchievements()
    suspend fun showLeaderboards()
}

/**
 * Development-only implementation. It does not impersonate a Google account and
 * never grants access to production multiplayer services.
 */
class LocalPlayGamesGateway : PlayGamesGateway {
    private val mutableIdentity = MutableStateFlow<PlayerIdentityState>(PlayerIdentityState.Guest)
    override val identity: Flow<PlayerIdentityState> = mutableIdentity

    override suspend fun checkAuthentication() {
        mutableIdentity.value = PlayerIdentityState.Guest
    }

    override suspend fun showAchievements() = Unit
    override suspend fun showLeaderboards() = Unit
}
