package com.meuaudiobook.app.data

import android.content.Context

/**
 * Calcula quanto de um audiolivro já foi ouvido, em porcentagem.
 *
 * A conta usa a posição salva e a duração de cada capítulo. As durações
 * declaradas no catálogo são usadas como ponto de partida; quando o player
 * descobre a duração real, [ProgressStore] passa a guardá-la.
 */
object BookProgress {

    fun isFinished(context: Context, book: Book): Boolean =
        ProgressStore.isFinished(context, book.id)

    /**
     * Porcentagem de 0 a 100.
     *
     * [chapterIndex], [positionMs] e [currentDurationMs] permitem que a tela
     * do player use a posição ao vivo; na biblioteca, os valores salvos são
     * usados por padrão.
     */
    fun percent(
        context: Context,
        book: Book,
        chapterIndex: Int = ProgressStore.chapter(context, book.id),
        positionMs: Long = ProgressStore.position(context, book.id),
        currentDurationMs: Long = 0L
    ): Int {
        if (book.chapters.isEmpty()) return 0
        if (ProgressStore.isFinished(context, book.id)) return 100

        val index = chapterIndex.coerceIn(0, book.chapters.lastIndex)
        val position = positionMs.coerceAtLeast(0L)

        var total = 0L
        var listened = 0L

        book.chapters.forEachIndexed { i, chapter ->
            // Prioriza a duração real; se ainda não existe, usa a do catálogo.
            var duration = ProgressStore.duration(context, book.id, i)
            if (duration <= 0L) duration = chapter.durationMs
            if (duration <= 0L && i == index) {
                duration = maxOf(currentDurationMs, position)
            }
            // Capítulos ainda sem duração conhecida não entram no total.
            if (duration <= 0L) return@forEachIndexed

            total += duration
            listened += when {
                i < index -> duration
                i == index -> position.coerceAtMost(duration)
                else -> 0L
            }
        }

        if (total <= 0L) return 0
        val value = ((listened * 100L) / total).toInt().coerceIn(0, 100)
        // Só mostra 100% quando o serviço marcar o livro como concluído.
        return if (value >= 100) 99 else value
    }
}
