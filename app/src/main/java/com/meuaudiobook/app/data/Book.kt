package com.meuaudiobook.app.data

/** Um capítulo/faixa de um audiolivro. */
data class Chapter(
    val title: String,
    val uri: String,
    val durationMs: Long = 0L
)

/** Um audiolivro: capítulos em ordem, capa gerada a partir da cor. */
data class Book(
    val id: String,
    val title: String,
    val author: String,
    val description: String,
    val color: Int,
    val chapters: List<Chapter>,
    val builtIn: Boolean = false
) {
    val initial: String
        get() = title.trim().firstOrNull()?.uppercase() ?: "?"
}
