package com.meuaudiobook.app.data

/**
 * Estado vivo da reprodução compartilhado entre o serviço e as telas.
 *
 * Fica apenas em memória: o progresso permanente continua em [ProgressStore].
 * Assim a biblioteca consegue destacar em vermelho o livro que está tocando
 * sem precisar abrir uma conexão com o serviço.
 */
object PlaybackState {

    @Volatile
    var bookId: String? = null

    @Volatile
    var chapterIndex: Int = 0

    @Volatile
    var positionMs: Long = 0L

    @Volatile
    var isPlaying: Boolean = false

    fun update(bookId: String?, chapterIndex: Int, positionMs: Long, isPlaying: Boolean) {
        this.bookId = bookId
        this.chapterIndex = chapterIndex
        this.positionMs = positionMs
        this.isPlaying = isPlaying
    }

    fun clear() {
        update(null, 0, 0L, false)
    }
}
