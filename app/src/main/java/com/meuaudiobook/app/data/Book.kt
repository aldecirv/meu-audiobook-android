package com.meuaudiobook.app.data

/** Um capítulo/faixa de um audiolivro. */
data class Chapter(
    val title: String,
    val uri: String,
    val durationMs: Long = 0L
)

/**
 * Um audiolivro: capítulos em ordem, capa gerada a partir da cor.
 * [coverUri] é opcional e pode apontar para uma imagem embutida no arquivo
 * (caminho local) ou para uma URL de capa (http/https).
 */
data class Book(
    val id: String,
    val title: String,
    val author: String,
    val description: String,
    val color: Int,
    val chapters: List<Chapter>,
    val builtIn: Boolean = false,
    val coverUri: String? = null
) {
    val initial: String
        get() = title.trim().firstOrNull()?.uppercase() ?: "?"
}
