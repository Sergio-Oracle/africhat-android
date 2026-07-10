/*
 * AfriChat — Plateforme de communication panafricaine souveraine.
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package io.element.android.libraries.matrix.impl.africhat

import io.element.android.libraries.matrix.api.africhat.AfriChatCustomEventSender
import io.element.android.libraries.matrix.api.africhat.AfriChatPollService
import io.element.android.libraries.matrix.api.africhat.AfriChatReaction
import io.element.android.libraries.matrix.api.africhat.AfriChatReactionService
import io.element.android.libraries.matrix.api.core.EventId
import io.element.android.libraries.matrix.api.core.RoomId
import timber.log.Timber

private const val POLL_START_EVENT = "io.africhat.poll.start"
private const val POLL_RESPONSE_EVENT = "io.africhat.poll.response"
private const val POLL_END_EVENT = "io.africhat.poll.end"
private const val RICH_REACTION_EVENT = "io.africhat.reaction"

/**
 * Implémentation du service de sondages AfriChat.
 *
 * Utilise des événements Matrix custom dédiés plutôt que m.poll (MSC3381)
 * pour avoir plus de flexibilité (résultats en temps réel, anonymat, limite de temps).
 */
class DefaultAfriChatPollService(
    private val eventSender: AfriChatCustomEventSender,
    private val currentUserId: String,
) : AfriChatPollService {

    override suspend fun createPoll(
        roomId: RoomId,
        question: String,
        options: List<String>,
        maxSelections: Int,
        durationMinutes: Int?,
        anonymous: Boolean,
    ): Result<EventId> {
        val pollOptions = options.mapIndexed { index, text ->
            mapOf("id" to "opt_$index", "text" to text)
        }
        val content = buildMap<String, Any?> {
            put("question", question)
            put("options", pollOptions)
            put("max_selections", maxSelections)
            put("anonymous", anonymous)
            put("creator", currentUserId)
            if (durationMinutes != null) {
                put("end_at", System.currentTimeMillis() + durationMinutes * 60_000L)
            }
        }
        return eventSender.sendMessageEvent(
            roomId = roomId,
            eventType = POLL_START_EVENT,
            content = content,
        ).map { EventId(it) }.also {
            Timber.d("[AfriChat] Poll created in $roomId: $question")
        }
    }

    override suspend fun respondToPoll(
        roomId: RoomId,
        pollEventId: EventId,
        selectedOptionIds: List<String>,
    ): Result<Unit> = eventSender.sendMessageEvent(
        roomId = roomId,
        eventType = POLL_RESPONSE_EVENT,
        content = mapOf(
            "m.relates_to" to mapOf(
                "rel_type" to "io.africhat.poll.response",
                "event_id" to pollEventId.value,
            ),
            "selections" to selectedOptionIds,
            "voter" to currentUserId,
            "timestamp" to System.currentTimeMillis(),
        ),
    ).map { Unit }

    override suspend fun closePoll(
        roomId: RoomId,
        pollEventId: EventId,
    ): Result<Unit> = eventSender.sendMessageEvent(
        roomId = roomId,
        eventType = POLL_END_EVENT,
        content = mapOf(
            "m.relates_to" to mapOf(
                "rel_type" to "io.africhat.poll.end",
                "event_id" to pollEventId.value,
            ),
            "closed_by" to currentUserId,
            "timestamp" to System.currentTimeMillis(),
        ),
    ).map { Unit }
}

/**
 * Implémentation du service de réactions AfriChat.
 */
class DefaultAfriChatReactionService(
    private val eventSender: AfriChatCustomEventSender,
) : AfriChatReactionService {

    override suspend fun sendReaction(
        roomId: RoomId,
        targetEventId: EventId,
        emoji: String,
    ): Result<Unit> = eventSender.sendMessageEvent(
        roomId = roomId,
        eventType = "m.reaction",
        content = mapOf(
            "m.relates_to" to mapOf(
                "rel_type" to "m.annotation",
                "event_id" to targetEventId.value,
                "key" to emoji,
            ),
        ),
    ).map { Unit }

    override suspend fun sendRichReaction(
        roomId: RoomId,
        targetEventId: EventId,
        reaction: AfriChatReaction,
    ): Result<Unit> = eventSender.sendMessageEvent(
        roomId = roomId,
        eventType = RICH_REACTION_EVENT,
        content = buildMap {
            put("m.relates_to", mapOf(
                "rel_type" to "io.africhat.annotation",
                "event_id" to targetEventId.value,
            ))
            put("emoji", reaction.emoji)
            if (reaction.label != null) put("label", reaction.label)
            if (reaction.gifUrl != null) put("gif_url", reaction.gifUrl)
        },
    ).map { Unit }
}
