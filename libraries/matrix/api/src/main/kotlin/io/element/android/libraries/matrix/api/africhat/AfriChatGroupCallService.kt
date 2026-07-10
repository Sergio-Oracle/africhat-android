/*
 * AfriChat — Plateforme de communication panafricaine souveraine.
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package io.element.android.libraries.matrix.api.africhat

import io.element.android.libraries.matrix.api.core.RoomId
import kotlinx.coroutines.flow.Flow

/**
 * Événement de groupe AfriChat.
 *
 * Compatible avec le format du client web SENDT (io.sendt.group_call),
 * adapté ici pour AfriChat (io.africhat.group_call).
 *
 * Structure JSON envoyée dans la room Matrix :
 * {
 *   "call_id": "group_1234567890",
 *   "version": 1,
 *   "action": "invite" | "hangup",
 *   "with_video": true | false,
 *   "livekit_url": "wss://livekit.ec2lt.sn",
 *   "livekit_room": "group_1234567890",
 *   "caller": "@user:jn.rtn.sn"
 * }
 */
data class AfriChatGroupCallEvent(
    val callId: String,
    val action: Action,
    val withVideo: Boolean,
    val livekitUrl: String,
    val livekitRoom: String,
    val callerId: String,
    val roomId: RoomId,
    val timestamp: Long = System.currentTimeMillis(),
) {
    enum class Action { INVITE, HANGUP }
}

/**
 * Informations de connexion LiveKit retournées par le token server.
 * GET /api/connection-details?room=<callId>
 * Authorization: Bearer <matrix_access_token>
 */
data class LiveKitConnectionDetails(
    val token: String,
    val url: String,
)

/**
 * Service de gestion des appels de groupe AfriChat.
 *
 * Le signaling passe par Matrix (événement io.africhat.group_call),
 * la média passe par LiveKit directement (pas d'Element Call / WebView).
 */
interface AfriChatGroupCallService {

    /**
     * Flux des invitations d'appel de groupe reçues dans toutes les salles.
     */
    val incomingGroupCallFlow: Flow<AfriChatGroupCallEvent>

    /**
     * Démarre un appel de groupe dans une salle.
     * 1. Génère un callId unique
     * 2. Récupère un token LiveKit depuis le token server
     * 3. Envoie l'événement io.africhat.group_call avec action=invite
     *
     * @param roomId Salle où démarrer l'appel
     * @param withVideo true pour inclure la caméra
     * @return Détails de connexion LiveKit (token + URL du serveur)
     */
    suspend fun startGroupCall(
        roomId: RoomId,
        withVideo: Boolean,
    ): Result<LiveKitConnectionDetails>

    /**
     * Rejoint un appel de groupe existant.
     *
     * @param roomId Salle de l'appel
     * @param callId ID de l'appel (extrait de l'événement reçu)
     * @param livekitRoom Nom de la room LiveKit
     * @param withVideo true pour inclure la caméra
     * @return Détails de connexion LiveKit
     */
    suspend fun joinGroupCall(
        roomId: RoomId,
        callId: String,
        livekitRoom: String,
        withVideo: Boolean,
    ): Result<LiveKitConnectionDetails>

    /**
     * Termine un appel de groupe en cours.
     * Envoie l'événement io.africhat.group_call avec action=hangup.
     */
    suspend fun endGroupCall(
        roomId: RoomId,
        callId: String,
    ): Result<Unit>

    /**
     * URL du token server pour obtenir les JWT LiveKit.
     * Ex: https://telephone.rtn.sn/api/connection-details
     */
    val tokenServerUrl: String

    /**
     * URL du serveur LiveKit.
     * Ex: wss://livekit.ec2lt.sn
     */
    val livekitUrl: String
}
