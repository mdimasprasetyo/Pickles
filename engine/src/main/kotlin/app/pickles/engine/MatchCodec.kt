package app.pickles.engine

import kotlinx.serialization.json.Json

/**
 * Serializes the authoritative [MatchState] for local persistence. Platform-independent so that
 * restoration after an app restart can be unit-tested on the JVM.
 */
object MatchCodec {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        classDiscriminator = "type"
    }

    fun encode(state: MatchState): String = json.encodeToString(MatchState.serializer(), state)

    /** Returns null for unreadable, incompatible, or internally inconsistent data. */
    fun decode(text: String): MatchState? = try {
        val state = json.decodeFromString(MatchState.serializer(), text)
        if (PicklesEngine.validate(state).isEmpty()) state else null
    } catch (e: Exception) {
        null
    }
}
