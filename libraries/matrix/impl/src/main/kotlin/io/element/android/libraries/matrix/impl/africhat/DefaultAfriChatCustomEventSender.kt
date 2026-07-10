/*
 * AfriChat — Plateforme de communication panafricaine souveraine.
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package io.element.android.libraries.matrix.impl.africhat

import io.element.android.libraries.matrix.api.africhat.AfriChatCustomEventSender
import io.element.android.libraries.matrix.api.core.RoomId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import timber.log.Timber
import java.util.UUID

/**
 * Implémentation de l'envoi d'événements Matrix custom via l'API CS directe.
 *
 * Utilise OkHttp pour appeler :
 *   PUT /_matrix/client/v3/rooms/{roomId}/send/{eventType}/{txnId}
 *   PUT /_matrix/client/v3/rooms/{roomId}/state/{eventType}/{stateKey}
 *
 * @param homeserverUrl URL du homeserver (ex: https://jn.rtn.sn)
 * @param accessToken Token d'accès Matrix de l'utilisateur courant
 * @param okHttpClient Client HTTP partagé avec l'app
 */
class DefaultAfriChatCustomEventSender(
    private val homeserverUrl: String,
    private val accessToken: String,
    private val okHttpClient: OkHttpClient = OkHttpClient(),
) : AfriChatCustomEventSender {

    private val json = "application/json; charset=utf-8".toMediaType()

    override suspend fun sendMessageEvent(
        roomId: RoomId,
        eventType: String,
        content: Map<String, Any?>,
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val txnId = "africhat_${UUID.randomUUID().toString().replace("-", "")}"
            val url = "$homeserverUrl/_matrix/client/v3/rooms/${roomId.value}/send/$eventType/$txnId"
            val body = JSONObject(content.filterValues { it != null }).toString()
                .toRequestBody(json)
            val request = Request.Builder()
                .url(url)
                .put(body)
                .header("Authorization", "Bearer $accessToken")
                .header("Content-Type", "application/json")
                .build()

            val response = okHttpClient.newCall(request).execute()
            val responseBody = response.body?.string() ?: ""
            if (!response.isSuccessful) {
                Timber.e("[AfriChat] sendMessageEvent $eventType failed ${response.code}: $responseBody")
                error("Matrix error ${response.code}: $responseBody")
            }
            val json = JSONObject(responseBody)
            json.getString("event_id").also {
                Timber.d("[AfriChat] sendMessageEvent $eventType → $it")
            }
        }
    }

    override suspend fun sendStateEvent(
        roomId: RoomId,
        eventType: String,
        stateKey: String,
        content: Map<String, Any?>,
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val url = "$homeserverUrl/_matrix/client/v3/rooms/${roomId.value}/state/$eventType/$stateKey"
            val body = JSONObject(content.filterValues { it != null }).toString()
                .toRequestBody(json)
            val request = Request.Builder()
                .url(url)
                .put(body)
                .header("Authorization", "Bearer $accessToken")
                .header("Content-Type", "application/json")
                .build()

            val response = okHttpClient.newCall(request).execute()
            val responseBody = response.body?.string() ?: ""
            if (!response.isSuccessful) {
                Timber.e("[AfriChat] sendStateEvent $eventType failed ${response.code}: $responseBody")
                error("Matrix error ${response.code}: $responseBody")
            }
            JSONObject(responseBody).getString("event_id").also {
                Timber.d("[AfriChat] sendStateEvent $eventType → $it")
            }
        }
    }
}
