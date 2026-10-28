package com.meuaudiobook.app.ui

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.meuaudiobook.app.R
import com.meuaudiobook.app.data.Book

class BookAdapter(
    private val subtitleProvider: (Book) -> String,
    private val onClick: (Book) -> Unit,
    private val onLongClick: (Book) -> Unit
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
        private val cover: TextView = view.findViewById(R.id.cover)
        private val title: TextView = view.findViewById(R.id.title)
        private val author: TextView = view.findViewById(R.id.author)
        private val progress: TextView = view.findViewById(R.id.progress)
        private val playIcon: ImageView = view.findViewById(R.id.playIcon)

        fun bind(book: Book) {
            title.text = book.title
            author.text = book.author
            progress.text = subtitleProvider(book)
            cover.text = book.initial
            cover.background = coverDrawable(book.color)
            playIcon.contentDescription = itemView.context.getString(R.string.play)

            itemView.setOnClickListener {
                onClick(book)
            }
            itemView.setOnLongClickListener {
                onLongClick(book)
                true
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
    }
}
