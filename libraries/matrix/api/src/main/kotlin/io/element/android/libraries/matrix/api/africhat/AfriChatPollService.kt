/*
 * AfriChat — Plateforme de communication panafricaine souveraine.
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package io.element.android.libraries.matrix.api.africhat

import io.element.android.libraries.matrix.api.core.EventId
import io.element.android.libraries.matrix.api.core.RoomId

/**
 * Sondage AfriChat utilisant l'événement io.africhat.poll.
 *
 * Différence avec m.poll (MSC3381) : permet des sondages multi-choix
 * avec limite de temps et résultats en temps réel.
 *
 * JSON de l'événement io.africhat.poll.start :
 * {
 *   "question": "Quel est votre pays ?",
 *   "options": [{ "id": "opt1", "text": "Sénégal" }, ...],
 *   "max_selections": 1,
 *   "end_at": 1700000000000,
 *   "anonymous": false
 * }
 */
data class AfriChatPoll(
    val eventId: EventId,
    val question: String,
    val options: List<PollOption>,
    val maxSelections: Int = 1,
    val endAt: Long? = null,
    val anonymous: Boolean = false,
    val results: Map<String, Int> = emptyMap(),
    val totalVotes: Int = 0,
    val isClosed: Boolean = false,
) {
    data class PollOption(val id: String, val text: String)
}

/**
 * Service de sondages AfriChat.
 * Utilise des événements Matrix custom : io.africhat.poll.start / io.africhat.poll.response / io.africhat.poll.end
 */
interface AfriChatPollService {

    /**
     * Crée un nouveau sondage dans une salle.
     */
    suspend fun createPoll(
        roomId: RoomId,
        question: String,
        options: List<String>,
        maxSelections: Int = 1,
        durationMinutes: Int? = null,
        anonymous: Boolean = false,
    ): Result<EventId>

    /**
     * Envoie une réponse à un sondage.
     */
    suspend fun respondToPoll(
        roomId: RoomId,
        pollEventId: EventId,
        selectedOptionIds: List<String>,
    ): Result<Unit>

    /**
     * Clôture un sondage (seulement le créateur peut le faire).
     */
    suspend fun closePoll(
        roomId: RoomId,
        pollEventId: EventId,
    ): Result<Unit>
}

/**
 * Réaction AfriChat étendue — permet des réactions avec texte (pas juste un emoji).
 *
 * Utilise l'événement standard m.reaction mais ajoute io.africhat.reaction
 * pour les réactions riches (GIF, texte court).
 */
data class AfriChatReaction(
    val emoji: String,
    val label: String? = null,
    val gifUrl: String? = null,
)

interface AfriChatReactionService {

    /**
     * Envoie une réaction standard (emoji) — utilise m.reaction standard.
     */
    suspend fun sendReaction(
        roomId: RoomId,
        targetEventId: EventId,
        emoji: String,
    ): Result<Unit>

    /**
     * Envoie une réaction riche AfriChat (emoji + texte ou GIF).
     * Utilise io.africhat.reaction.
     */
    suspend fun sendRichReaction(
        roomId: RoomId,
        targetEventId: EventId,
        reaction: AfriChatReaction,
    ): Result<Unit>
}
