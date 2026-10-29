package com.meuaudiobook.app.data

import android.content.Context

/**
 * Guarda o capítulo e a posição de cada livro em SharedPreferences.
 *
 * Além da posição, também mantém (de forma aditiva):
 * - a duração real de cada capítulo, quando o player a descobre;
 * - a marca de "Ouvido" (livro concluído);
 * - o instante do último salvamento, útil para retomada.
 *
 * Nada disso depende de internet: tudo fica no armazenamento local.
 */
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
            .putLong("u_$bookId", System.currentTimeMillis())
            .apply()
    }

    /** Duração real já conhecida de um capítulo (0 quando ainda não medida). */
    fun duration(context: Context, bookId: String, chapter: Int): Long =
        prefs(context).getLong("d_${bookId}_$chapter", 0L)

    fun saveDuration(context: Context, bookId: String, chapter: Int, durationMs: Long) {
        if (durationMs <= 0L) return
        prefs(context).edit().putLong("d_${bookId}_$chapter", durationMs).apply()
    }

    /** Indica se o audiolivro já foi ouvido até o fim. */
    fun isFinished(context: Context, bookId: String): Boolean =
        prefs(context).getBoolean("f_$bookId", false)

    fun setFinished(context: Context, bookId: String, finished: Boolean) {
        prefs(context).edit().putBoolean("f_$bookId", finished).apply()
    }

    /** Reinicia a posição de um livro, mantendo as durações já descobertas. */
    fun reset(context: Context, bookId: String) {
        prefs(context).edit()
            .putInt("c_$bookId", 0)
            .putLong("p_$bookId", 0L)
            .putBoolean("f_$bookId", false)
            .putLong("u_$bookId", System.currentTimeMillis())
            .apply()
    }

    fun lastBook(context: Context): String? =
        prefs(context).getString(KEY_LAST, null)

    private fun prefs(context: Context) =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
}
