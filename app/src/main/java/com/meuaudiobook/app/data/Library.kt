package com.meuaudiobook.app.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * Junta o catálogo embutido com os audiolivros que o usuário adicionou
 * pelo seletor de arquivos do sistema (SAF).
 */
object Library {

    private const val FILE = "library"
    private const val KEY_BOOKS = "user_books"

    private val palette = intArrayOf(
        0xFF2F6D6A.toInt(),
        0xFF6B4E9B.toInt(),
        0xFF8C5A2B.toInt(),
        0xFF355C7D.toInt(),
        0xFF7A3E5C.toInt(),
        0xFF3E6B34.toInt()
    )

    fun all(context: Context): List<Book> = SampleBooks.books + userBooks(context)

    fun find(context: Context, id: String?): Book? =
        if (id == null) null else all(context).firstOrNull { it.id == id }

    fun addLocalBook(context: Context, title: String, author: String, uris: List<String>): Book {
        val id = "local-" + System.currentTimeMillis()
        val book = Book(
            id = id,
            title = title.ifBlank { "Audiolivro" },
            author = author,
            description = "",
            color = palette[(id.hashCode() and Int.MAX_VALUE) % palette.size],
            chapters = uris.mapIndexed { index, uri ->
                Chapter("Faixa ${index + 1}", uri)
            },
            builtIn = false
        )
        val current = userBooks(context).toMutableList()
        current.add(book)
        save(context, current)
        return book
    }

    fun remove(context: Context, id: String) {
        save(context, userBooks(context).filterNot { it.id == id })
    }

    private fun userBooks(context: Context): List<Book> {
        val raw = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            .getString(KEY_BOOKS, null) ?: return emptyList()
        return try {
            val array = JSONArray(raw)
            (0 until array.length()).mapNotNull { index ->
                val obj = array.optJSONObject(index) ?: return@mapNotNull null
                val chaptersArray = obj.optJSONArray("chapters") ?: JSONArray()
                val chapters = (0 until chaptersArray.length()).mapNotNull { i ->
                    val c = chaptersArray.optJSONObject(i) ?: return@mapNotNull null
                    Chapter(c.optString("title"), c.optString("uri"), c.optLong("duration"))
                }
                if (chapters.isEmpty()) return@mapNotNull null
                Book(
                    id = obj.optString("id"),
                    title = obj.optString("title"),
                    author = obj.optString("author"),
                    description = obj.optString("description"),
                    color = obj.optInt("color"),
                    chapters = chapters,
                    builtIn = false
                )
            }
        } catch (_: Throwable) {
            emptyList()
        }
    }

    private fun save(context: Context, books: List<Book>) {
        val array = JSONArray()
        books.forEach { book ->
            val chapters = JSONArray()
            book.chapters.forEach { chapter ->
                chapters.put(
                    JSONObject()
                        .put("title", chapter.title)
                        .put("uri", chapter.uri)
                        .put("duration", chapter.durationMs)
                )
            }
            array.put(
                JSONObject()
                    .put("id", book.id)
                    .put("title", book.title)
                    .put("author", book.author)
                    .put("description", book.description)
                    .put("color", book.color)
                    .put("chapters", chapters)
            )
        }
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_BOOKS, array.toString())
            .apply()
    }
}
