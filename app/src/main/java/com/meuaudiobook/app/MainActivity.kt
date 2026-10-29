package com.meuaudiobook.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
import com.meuaudiobook.app.data.Book
import com.meuaudiobook.app.data.BookProgress
import com.meuaudiobook.app.data.Library
import com.meuaudiobook.app.data.MediaInspector
import com.meuaudiobook.app.data.PlaybackState
import com.meuaudiobook.app.data.ProgressStore
import com.meuaudiobook.app.ui.BookAdapter

class MainActivity : AppCompatActivity() {

    private lateinit var recycler: RecyclerView
    private lateinit var adapter: BookAdapter
    private var books: List<Book> = emptyList()

    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    private val pickAudio = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode != RESULT_OK) return@registerForActivityResult
        val data = result.data ?: return@registerForActivityResult

        val uris = mutableListOf<Uri>()
        data.clipData?.let { clip ->
            for (index in 0 until clip.itemCount) {
                clip.getItemAt(index)?.uri?.let { uris.add(it) }
            }
        } ?: data.data?.let { uris.add(it) }

        if (uris.isEmpty()) {
            Toast.makeText(this, R.string.no_audio_selected, Toast.LENGTH_SHORT).show()
            return@registerForActivityResult
        }

        uris.forEach { uri ->
            try {
                contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: SecurityException) {
                // Alguns provedores não permitem persistir; segue em frente.
            }
        }

        val title = displayName(uris.first())
            ?.substringBeforeLast('.', "")
            ?.ifBlank { getString(R.string.local_files) }
            ?: getString(R.string.local_files)

        val selectedUris = uris.toList()
        Toast.makeText(this, R.string.adding_book, Toast.LENGTH_SHORT).show()

        // Lê duração e capa embutida sem travar a interface.
        Thread {
            val inspection = MediaInspector.inspect(this, selectedUris)
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                Library.addLocalBook(
                    this,
                    title = title,
                    author = getString(R.string.local_files),
                    uris = selectedUris.map { it.toString() },
                    durations = inspection.durations,
                    coverUri = inspection.coverPath
                )
                Toast.makeText(this, R.string.added_book, Toast.LENGTH_SHORT).show()
                refresh()
            }
        }.start()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        recycler = findViewById(R.id.recycler)
        recycler.layoutManager = LinearLayoutManager(this)

        adapter = BookAdapter(
            subtitleProvider = { book -> subtitleFor(book) },
            onClick = { book -> openPlayer(book) },
            onLongClick = { book -> confirmRemove(book) },
            progressProvider = { book -> BookProgress.percent(this, book) },
            playingProvider = { PlaybackState.bookId },
            finishedProvider = { book -> ProgressStore.isFinished(this, book.id) }
        )
        recycler.adapter = adapter

        findViewById<ExtendedFloatingActionButton>(R.id.btnAdd).setOnClickListener {
            pickAudio.launch(
                Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = "audio/*"
                    // Inclui explicitamente OPUS/OGG, que alguns gerenciadores
                    // de arquivos não listam apenas com "audio/*".
                    putExtra(
                        Intent.EXTRA_MIME_TYPES,
                        arrayOf(
                            "audio/*",
                            "audio/mpeg",
                            "audio/mp4",
                            "audio/x-m4a",
                            "audio/ogg",
                            "audio/opus",
                            "audio/x-opus+ogg",
                            "application/ogg",
                            "audio/flac",
                            "audio/wav"
                        )
                    )
                    putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
                }
            )
        }

        askNotificationPermission()
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun refresh() {
        books = Library.all(this)
        adapter.submit(books)
    }

    private fun subtitleFor(book: Book): String {
        val chapter = ProgressStore.chapter(this, book.id)
            .coerceIn(0, book.chapters.lastIndex)
        return getString(R.string.chapter_of, chapter + 1, book.chapters.size)
    }

    private fun openPlayer(book: Book) {
        startActivity(
            Intent(this, PlayerActivity::class.java)
                .putExtra(PlayerActivity.EXTRA_BOOK_ID, book.id)
        )
    }

    private fun confirmRemove(book: Book) {
        if (book.builtIn) return
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.remove_book)
            .setMessage(getString(R.string.remove_confirm, book.title))
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.remove) { _, _ ->
                Library.remove(this, book.id)
                refresh()
            }
            .show()
    }

    private fun displayName(uri: Uri): String? = try {
        contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            }
    } catch (_: Throwable) {
        null
    }

    private fun askNotificationPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val granted = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
}
