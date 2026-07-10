/*
 * AfriChat — Plateforme de communication panafricaine souveraine.
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package io.element.android.libraries.matrix.impl.africhat

import io.element.android.libraries.matrix.api.africhat.AfriChatPresence
import io.element.android.libraries.matrix.api.africhat.AfriChatPresenceService
import io.element.android.libraries.matrix.api.africhat.AfriChatUserPresence
import io.element.android.libraries.matrix.api.core.UserId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import timber.log.Timber
import java.net.URLEncoder

/**
 * Implémentation du service de présence AfriChat.
 *
 * Utilise l'API Matrix Presence standard :
 *   PUT /_matrix/client/v3/presence/{userId}/status
 *   GET /_matrix/client/v3/presence/{userId}/status
 *
 * Le polling est utilisé comme fallback pour les serveurs
 * qui ne supportent pas les push de présence.
 */
class DefaultAfriChatPresenceService(
    private val homeserverUrl: String,
    private val currentUserId: UserId,
    private val accessToken: String,
    private val coroutineScope: CoroutineScope,
    private val okHttpClient: OkHttpClient = OkHttpClient(),
) : AfriChatPresenceService {

    private val json = "application/json; charset=utf-8".toMediaType()
    private val _presenceFlow = MutableSharedFlow<AfriChatUserPresence>(extraBufferCapacity = 32)
    override val presenceFlow: Flow<AfriChatUserPresence> = _presenceFlow

    private var pollingJob: Job? = null

    override suspend fun setPresence(
        presence: AfriChatPresence,
        statusMessage: String?,
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val matrixPresence = when (presence) {
                AfriChatPresence.ONLINE -> "online"
                AfriChatPresence.AWAY -> "unavailable"
                AfriChatPresence.OFFLINE -> "offline"
                AfriChatPresence.BUSY -> "unavailable"
            }
            val content = buildMap<String, Any> {
                put("presence", matrixPresence)
                if (statusMessage != null) put("status_msg", statusMessage)
            }
            val url = "$homeserverUrl/_matrix/client/v3/presence/${URLEncoder.encode(currentUserId.value, "UTF-8")}/status"
            val body = JSONObject(content).toString().toRequestBody(json)
            val request = Request.Builder()
                .url(url)
                .put(body)
                .header("Authorization", "Bearer $accessToken")
                .build()

            val response = okHttpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                Timber.w("[AfriChat] setPresence failed: ${response.code}")
            } else {
                Timber.d("[AfriChat] Presence set to $presence")
            }
        }
    }

    override suspend fun getUserPresence(userId: UserId): Result<AfriChatUserPresence> =
        withContext(Dispatchers.IO) {
            runCatching {
                val url = "$homeserverUrl/_matrix/client/v3/presence/${URLEncoder.encode(userId.value, "UTF-8")}/status"
                val request = Request.Builder()
                    .url(url)
                    .get()
                    .header("Authorization", "Bearer $accessToken")
                    .build()

                val response = okHttpClient.newCall(request).execute()
                val body = response.body?.string() ?: "{}"
                val json = JSONObject(body)

                val matrixPresence = json.optString("presence", "offline")
                val presence = when {
                    json.optBoolean("currently_active", false) -> AfriChatPresence.ONLINE
                    matrixPresence == "online" -> AfriChatPresence.ONLINE
                    matrixPresence == "unavailable" -> AfriChatPresence.AWAY
                    else -> AfriChatPresence.OFFLINE
                }
                AfriChatUserPresence(
                    userId = userId,
                    presence = presence,
                    statusMessage = json.optString("status_msg").takeIf { it.isNotEmpty() },
                    lastActiveAgo = json.optLong("last_active_ago").takeIf { it > 0 },
                )
            }
        }

    override suspend fun startPresencePolling(userIds: List<UserId>) {
        pollingJob?.cancel()
        pollingJob = coroutineScope.launch {
            while (isActive) {
                userIds.forEach { userId ->
                    getUserPresence(userId).onSuccess { presence ->
                        _presenceFlow.tryEmit(presence)
                    }
                }
                delay(30_000) // Poll toutes les 30 secondes
            }
        }
    }

    override fun stopPresencePolling() {
        pollingJob?.cancel()
        pollingJob = null
    }
}
