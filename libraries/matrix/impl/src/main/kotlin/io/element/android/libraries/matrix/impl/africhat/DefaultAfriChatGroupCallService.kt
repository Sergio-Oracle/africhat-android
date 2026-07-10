/*
 * AfriChat — Plateforme de communication panafricaine souveraine.
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package io.element.android.libraries.matrix.impl.africhat

import io.element.android.libraries.matrix.api.africhat.AfriChatCustomEventSender
import io.element.android.libraries.matrix.api.africhat.AfriChatGroupCallEvent
import io.element.android.libraries.matrix.api.africhat.AfriChatGroupCallService
import io.element.android.libraries.matrix.api.africhat.LiveKitConnectionDetails
import io.element.android.libraries.matrix.api.core.RoomId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import timber.log.Timber

/**
 * Événement custom Matrix pour les appels de groupe AfriChat.
 *
 * Compatible avec le client web SENDT (io.sendt.group_call) — même structure JSON,
 * type différent (io.africhat.group_call). Les deux clients peuvent coexister
 * sur le même serveur LiveKit.
 */
private const val GROUP_CALL_EVENT_TYPE = "io.africhat.group_call"

/**
 * Implémentation du service d'appels de groupe AfriChat.
 *
 * Flux de signaling :
 * 1. Initiateur → génère callId, obtient token LiveKit, envoie io.africhat.group_call (invite)
 * 2. Destinataires → reçoivent l'événement, affichent l'UI d'appel entrant
 * 3. Destinataire accepte → appelle joinGroupCall() avec le callId reçu
 * 4. N'importe quel participant → peut envoyer hangup
 *
 * @param eventSender Envoyeur d'événements Matrix custom
 * @param currentUserId ID Matrix de l'utilisateur courant (ex: @user:jn.rtn.sn)
 * @param tokenServerUrl URL du token server LiveKit
 * @param livekitServerUrl URL du serveur LiveKit (wss://...)
 * @param accessToken Token Matrix pour authentifier les requêtes au token server
 * @param okHttpClient Client HTTP partagé
 */
class DefaultAfriChatGroupCallService(
    private val eventSender: AfriChatCustomEventSender,
    private val currentUserId: String,
    override val tokenServerUrl: String,
    override val livekitUrl: String,
    private val accessToken: String,
    private val okHttpClient: OkHttpClient = OkHttpClient(),
) : AfriChatGroupCallService {

    private val _incomingCallFlow = MutableSharedFlow<AfriChatGroupCallEvent>(extraBufferCapacity = 8)
    override val incomingGroupCallFlow: Flow<AfriChatGroupCallEvent> = _incomingCallFlow

    /**
     * Appelé par le layer Matrix (timeline event listener) quand un événement
     * io.africhat.group_call est reçu dans une room.
     */
    fun onGroupCallEventReceived(roomId: RoomId, content: Map<String, Any?>) {
        val callerId = content["caller"] as? String ?: return
        if (callerId == currentUserId) return  // ignorer nos propres événements
        val action = when (content["action"] as? String) {
            "invite" -> AfriChatGroupCallEvent.Action.INVITE
            "hangup" -> AfriChatGroupCallEvent.Action.HANGUP
            else -> return
        }
        val event = AfriChatGroupCallEvent(
            callId = content["call_id"] as? String ?: return,
            action = action,
            withVideo = content["with_video"] as? Boolean ?: false,
            livekitUrl = content["livekit_url"] as? String ?: livekitUrl,
            livekitRoom = content["livekit_room"] as? String ?: return,
            callerId = callerId,
            roomId = roomId,
            timestamp = content["timestamp"] as? Long ?: System.currentTimeMillis(),
        )
        _incomingCallFlow.tryEmit(event)
    }

    override suspend fun startGroupCall(
        roomId: RoomId,
        withVideo: Boolean,
    ): Result<LiveKitConnectionDetails> {
        val callId = "group_${System.currentTimeMillis()}"
        return fetchLiveKitToken(callId).onSuccess { details ->
            // Notifier les autres membres de la salle
            eventSender.sendMessageEvent(
                roomId = roomId,
                eventType = GROUP_CALL_EVENT_TYPE,
                content = mapOf(
                    "call_id" to callId,
                    "version" to 1,
                    "action" to "invite",
                    "with_video" to withVideo,
                    "livekit_url" to livekitUrl,
                    "livekit_room" to callId,
                    "caller" to currentUserId,
                    "timestamp" to System.currentTimeMillis(),
                ),
            ).onFailure { Timber.w(it, "[AfriChat] Failed to send group call invite") }
        }
    }

    override suspend fun joinGroupCall(
        roomId: RoomId,
        callId: String,
        livekitRoom: String,
        withVideo: Boolean,
    ): Result<LiveKitConnectionDetails> = fetchLiveKitToken(livekitRoom)

    override suspend fun endGroupCall(
        roomId: RoomId,
        callId: String,
    ): Result<Unit> = eventSender.sendMessageEvent(
        roomId = roomId,
        eventType = GROUP_CALL_EVENT_TYPE,
        content = mapOf(
            "call_id" to callId,
            "version" to 1,
            "action" to "hangup",
            "caller" to currentUserId,
            "timestamp" to System.currentTimeMillis(),
        ),
    ).map { Unit }

    /**
     * Obtient un token JWT LiveKit depuis le token server.
     * Le token server vérifie le token Matrix avant de délivrer un token LiveKit.
     *
     * URL: GET <tokenServerUrl>?room=<roomName>
     * Header: Authorization: Bearer <matrix_access_token>
     * Réponse: { "token": "eyJ...", "url": "wss://..." }
     */
    private suspend fun fetchLiveKitToken(roomName: String): Result<LiveKitConnectionDetails> =
        withContext(Dispatchers.IO) {
            runCatching {
                val url = "$tokenServerUrl?room=${java.net.URLEncoder.encode(roomName, "UTF-8")}"
                val request = Request.Builder()
                    .url(url)
                    .get()
                    .header("Authorization", "Bearer $accessToken")
                    .build()

                val response = okHttpClient.newCall(request).execute()
                val body = response.body?.string() ?: error("Empty response from token server")
                if (!response.isSuccessful) {
                    Timber.e("[AfriChat] Token server error ${response.code}: $body")
                    error("Token server returned ${response.code}")
                }
                val json = JSONObject(body)
                LiveKitConnectionDetails(
                    token = json.getString("token"),
                    url = json.optString("url", livekitUrl),
                ).also {
                    Timber.d("[AfriChat] LiveKit token obtained for room $roomName")
                }
            }
        }
}
