package app.pickles.data

import android.content.Context
import android.util.AtomicFile
import app.pickles.engine.MatchCodec
import app.pickles.engine.MatchState
import java.io.File
import java.io.IOException

/**
 * Local, offline persistence of the single authoritative [MatchState].
 * Written atomically after every committed transition so an interrupted match can be resumed.
 */
class MatchRepository(context: Context) {
    private val file = AtomicFile(File(context.filesDir, "current_match.json"))

    fun load(): MatchState? = try {
        MatchCodec.decode(String(file.readFully(), Charsets.UTF_8))
    } catch (e: IOException) {
        null
    }

    fun save(state: MatchState) {
        val bytes = MatchCodec.encode(state).toByteArray(Charsets.UTF_8)
        val out = try {
            file.startWrite()
        } catch (e: IOException) {
            return
        }
        try {
            out.write(bytes)
            file.finishWrite(out)
        } catch (e: IOException) {
            file.failWrite(out)
        }
    }

    fun clear() {
        file.delete()
    }
}

/** Display-only preferences that are not part of the match state. */
class UiPrefs(context: Context) {
    private val prefs = context.getSharedPreferences("ui", Context.MODE_PRIVATE)
    var courtLayout: app.pickles.ui.CourtLayout
        get() = prefs.getString("court_layout", null)
            ?.let { v -> app.pickles.ui.CourtLayout.entries.firstOrNull { it.name == v } }
            ?: app.pickles.ui.CourtLayout.LANDSCAPE
        set(v) = prefs.edit().putString("court_layout", v.name).apply()

    var flipView: Boolean
        get() = prefs.getBoolean("flip_view", false)
        set(v) = prefs.edit().putBoolean("flip_view", v).apply()
    var keepScreenOn: Boolean
        get() = prefs.getBoolean("keep_screen_on", true)
        set(v) = prefs.edit().putBoolean("keep_screen_on", v).apply()
}
