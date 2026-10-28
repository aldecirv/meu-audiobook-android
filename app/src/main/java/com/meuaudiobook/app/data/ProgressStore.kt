package com.meuaudiobook.app.data

import android.content.Context

/** Guarda o capítulo e a posição de cada livro em SharedPreferences. */
object ProgressStore {

    private const val FILE = "progress"
    private const val KEY_LAST = "last_book"

    fun chapter(context: Context, bookId: String): Int =
        prefs(context).getInt("c_$bookId", 0)

    fun position(context: Context, bookId: String): Long =
        prefs(context).getLong("p_$bookId", 0L)

    fun save(context: Context, bookId: String, chapter: Int, position: Long) {
        prefs(context).edit()
            .putInt("c_$bookId", chapter)
            .putLong("p_$bookId", position.coerceAtLeast(0L))
            .putString(KEY_LAST, bookId)
            .apply()
    }

    fun lastBook(context: Context): String? =
        prefs(context).getString(KEY_LAST, null)

    private fun prefs(context: Context) =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
}
