/*
 * AfriChat — Plateforme de communication panafricaine souveraine.
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package io.element.android.libraries.matrix.api.africhat

import io.element.android.libraries.matrix.api.core.RoomId

/**
 * Permet d'envoyer des événements Matrix arbitraires (message-like ou state)
 * directement via l'API Client-Serveur Matrix, sans passer par le SDK Rust.
 *
 * Utilisé pour les événements custom AfriChat (appels de groupe, sondages, etc.)
 */
interface AfriChatCustomEventSender {

    /**
     * Envoie un événement de type message (room timeline event).
     *
     * @param roomId ID de la salle Matrix cible
     * @param eventType Type de l'événement (ex: "io.africhat.group_call")
     * @param content Corps JSON de l'événement
     * @return ID de l'événement créé, ou failure
     */
    suspend fun sendMessageEvent(
        roomId: RoomId,
        eventType: String,
        content: Map<String, Any?>,
    ): Result<String>

    /**
     * Envoie un événement d'état (state event), ex: topic, membres, etc.
     *
     * @param roomId ID de la salle Matrix cible
     * @param eventType Type de l'événement d'état
     * @param stateKey Clé d'état (souvent "" ou un userId)
     * @param content Corps JSON
     */
    suspend fun sendStateEvent(
        roomId: RoomId,
        eventType: String,
        stateKey: String = "",
        content: Map<String, Any?>,
    ): Result<String>
}
