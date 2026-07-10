/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2023-2025 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.appconfig

object ElementCallConfig {
    /**
     * The default duration of a ringing call in seconds before it's automatically dismissed.
     */
    const val RINGING_CALL_DURATION_SECONDS = 90

    /** AfriChat LiveKit server WebSocket URL (media SFU). */
    const val LIVEKIT_URL = "wss://livekit.ec2lt.sn"

    /** AfriChat LiveKit API key. */
    const val LIVEKIT_API_KEY = "livekit-prod"

    /** AfriChat LiveKit REST API HTTP URL. */
    const val LIVEKIT_API_URL = "http://62.171.190.6:7880"

    /**
     * Token server LiveKit — génère des JWT après vérification du token Matrix.
     * GET <TOKEN_SERVER_URL>?room=<callId>
     * Authorization: Bearer <matrix_access_token>
     * → { "token": "eyJ...", "url": "wss://..." }
     *
     * Compatible avec le client web SENDT (même serveur, même format).
     */
    const val LIVEKIT_TOKEN_SERVER_URL = "https://telephone.rtn.sn/api/connection-details"

    /**
     * Type d'événement Matrix custom AfriChat pour les appels de groupe.
     * Compatible avec io.sendt.group_call côté web.
     */
    const val AFRICHAT_GROUP_CALL_EVENT_TYPE = "io.africhat.group_call"
}
