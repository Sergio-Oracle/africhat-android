/*
 * AfriChat — Plateforme de communication panafricaine souveraine.
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package io.element.android.libraries.matrix.api.africhat

import io.element.android.libraries.matrix.api.core.UserId
import kotlinx.coroutines.flow.Flow

enum class AfriChatPresence {
    ONLINE,     // En ligne
    AWAY,       // Absent (inactif depuis > 5 min)
    OFFLINE,    // Hors ligne
    BUSY,       // Occupé (en appel ou dans une réunion)
}

data class AfriChatUserPresence(
    val userId: UserId,
    val presence: AfriChatPresence,
    val statusMessage: String? = null,
    val lastActiveAgo: Long? = null,
)

/**
 * Gestion de la présence et du statut des utilisateurs AfriChat.
 *
 * Utilise l'API Matrix Presence (PUT /_matrix/client/v3/presence/{userId}/status)
 * combinée avec un événement custom io.africhat.presence pour le statut riche.
 */
interface AfriChatPresenceService {

    /**
     * Flux des changements de présence des contacts de l'utilisateur connecté.
     */
    val presenceFlow: Flow<AfriChatUserPresence>

    /**
     * Définit la présence de l'utilisateur courant.
     * @param presence Nouveau statut de présence
     * @param statusMessage Message de statut optionnel (ex: "En réunion")
     */
    suspend fun setPresence(
        presence: AfriChatPresence,
        statusMessage: String? = null,
    ): Result<Unit>

    /**
     * Obtient la présence d'un utilisateur spécifique.
     */
    suspend fun getUserPresence(userId: UserId): Result<AfriChatUserPresence>

    /**
     * Démarre le polling de présence pour une liste d'utilisateurs.
     * Utile pour les contacts dont le homeserver ne supporte pas la présence push.
     */
    suspend fun startPresencePolling(userIds: List<UserId>)

    /**
     * Arrête le polling de présence.
     */
    fun stopPresencePolling()
}
