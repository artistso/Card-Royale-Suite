package com.soquarky.cardtable.protocol

import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

internal fun Json.encodeToString(events: List<EventEnvelope>): String =
    encodeToString(ListSerializer(EventEnvelope.serializer()), events)
