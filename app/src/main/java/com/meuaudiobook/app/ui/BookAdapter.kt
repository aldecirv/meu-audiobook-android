package com.meuaudiobook.app.ui

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView
import com.meuaudiobook.app.R
import com.meuaudiobook.app.data.Book
import com.meuaudiobook.app.data.PlaybackState
import com.meuaudiobook.app.util.CoverLoader

class BookAdapter(
    private val subtitleProvider: (Book) -> String,
    private val onClick: (Book) -> Unit,
    private val onLongClick: (Book) -> Unit,
    private val progressProvider: (Book) -> Int = { 0 },
    private val playingProvider: () -> String? = { PlaybackState.bookId },
    private val finishedProvider: (Book) -> Boolean = { false }
) : RecyclerView.Adapter<BookAdapter.BookViewHolder>() {

    private var items: List<Book> = emptyList()

    fun submit(books: List<Book>) {
        items = books
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BookViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_book, parent, false)
        return BookViewHolder(view)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: BookViewHolder, position: Int) {
        holder.bind(items[position])
    }

    inner class BookViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val card: MaterialCardView = view as MaterialCardView
        private val cover: TextView = view.findViewById(R.id.cover)
        private val title: TextView = view.findViewById(R.id.title)
        private val author: TextView = view.findViewById(R.id.author)
        private val progress: TextView = view.findViewById(R.id.progress)
        private val playIcon: ImageView = view.findViewById(R.id.playIcon)
        private val playingBand: View = view.findViewById(R.id.playingBand)
        private val playingProgress: ProgressBar = view.findViewById(R.id.playingProgress)

        fun bind(book: Book) {
            val context = itemView.context
            val isCurrent = playingProvider() == book.id
            val heard = finishedProvider(book)
            // Livro atual e ainda não concluído continua destacado mesmo pausado.
            val isActive = isCurrent && !heard
            val isPlaying = isCurrent && PlaybackState.isPlaying && !heard
            val percent = progressProvider(book).coerceIn(0, 100)

            title.text = book.title
            author.text = book.author

            // Texto existente + porcentagem + marca de ouvido.
            progress.text = buildString {
                append(subtitleProvider(book))
                append(" · ")
                append(context.getString(R.string.percent_listened, percent))
                if (heard) {
                    append(" · ")
                    append(context.getString(R.string.heard))
                }
            }
            progress.setTextColor(
                context.getColor(
                    when {
                        heard -> R.color.heard
                        isActive -> R.color.playing_red
                        else -> R.color.accent
                    }
                )
            )

            // Destaque vermelho do item que está tocando.
            card.setCardBackgroundColor(
                context.getColor(if (isActive) R.color.playing_bg else R.color.surface)
            )
            card.strokeColor = context.getColor(
                if (isActive) R.color.playing_red else R.color.divider
            )
            card.strokeWidth = dp(if (isActive) 2 else 1)
            playingBand.visibility = if (isActive) View.VISIBLE else View.GONE

            // Barra vermelha de progresso na base do cartão.
            playingProgress.visibility = if (isActive) View.VISIBLE else View.GONE
            playingProgress.progress = percent

            playIcon.setImageResource(
                if (isPlaying) android.R.drawable.ic_media_pause
                else android.R.drawable.ic_media_play
            )
            playIcon.setColorFilter(
                context.getColor(if (isActive) R.color.playing_red else R.color.accent)
            )
            playIcon.contentDescription = context.getString(
                if (isPlaying) R.string.pause else R.string.play
            )

            showCover(book)

            itemView.setOnClickListener {
                onClick(book)
            }
            itemView.setOnLongClickListener {
                onLongClick(book)
                true
            }
        }

        /** Mostra a capa quando existir; senão mantém a capa colorida com a inicial. */
        private fun showCover(book: Book) {
            cover.tag = book.id
            cover.text = book.initial
            cover.background = coverDrawable(book.color)

            if (book.coverUri.isNullOrBlank()) return

            val context = itemView.context
            CoverLoader.load(context, book, dp(56)) { bitmap ->
                // Evita aplicar a capa de um livro antigo em uma view reciclada.
                if (bitmap != null && cover.tag == book.id) {
                    cover.text = ""
                    cover.background = CoverLoader.roundedDrawable(context, bitmap, 14f)
                }
            }
        }

        private fun coverDrawable(color: Int): GradientDrawable {
            val darker = Color.rgb(
                (Color.red(color) * 0.72f).toInt(),
                (Color.green(color) * 0.72f).toInt(),
                (Color.blue(color) * 0.72f).toInt()
            )
            val density = itemView.resources.displayMetrics.density
            return GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(color, darker)
            ).apply {
                cornerRadius = 14f * density
            }
        }

        private fun dp(value: Int): Int =
            (value * itemView.resources.displayMetrics.density).toInt()
    }
}
