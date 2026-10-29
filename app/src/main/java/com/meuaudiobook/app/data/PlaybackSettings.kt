package com.meuaudiobook.app.data

import android.content.Context

/**
 * Preferências locais do player: intervalo dos botões de avançar/retroceder
 * e estado do cadeado de reprodução.
 *
 * Tudo é gravado em SharedPreferences, portanto funciona offline.
 */
object PlaybackSettings {

    private const val FILE = "playback_settings"
    private const val KEY_SKIP_SECONDS = "skip_seconds"
    private const val KEY_LOCKED = "player_locked"

    /** Opções oferecidas na tela de ajuste do intervalo. */
    val skipOptions = intArrayOf(10, 15, 30, 45, 60)

    fun skipSeconds(context: Context): Int =
        prefs(context).getInt(KEY_SKIP_SECONDS, 15).coerceIn(5, 120)

    fun skipMs(context: Context): Long = skipSeconds(context) * 1000L

    fun setSkipSeconds(context: Context, seconds: Int) {
        prefs(context).edit()
            .putInt(KEY_SKIP_SECONDS, seconds.coerceIn(5, 120))
            .apply()
    }

    fun isLocked(context: Context): Boolean =
        prefs(context).getBoolean(KEY_LOCKED, false)

    fun setLocked(context: Context, locked: Boolean) {
        prefs(context).edit().putBoolean(KEY_LOCKED, locked).apply()
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
}
