package com.meuaudiobook.app

import android.content.ComponentName
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import com.meuaudiobook.app.data.Book
import com.meuaudiobook.app.data.BookProgress
import com.meuaudiobook.app.data.Library
import com.meuaudiobook.app.data.PlaybackSettings
import com.meuaudiobook.app.data.ProgressStore
import com.meuaudiobook.app.playback.PlaybackService
import com.meuaudiobook.app.util.CoverLoader
import java.util.Locale

class PlayerActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_BOOK_ID = "book_id"
    }

    private lateinit var cover: TextView
    private lateinit var title: TextView
    private lateinit var author: TextView
    private lateinit var chapter: TextView
    private lateinit var percent: TextView
    private lateinit var seek: SeekBar
    private lateinit var timeCurrent: TextView
    private lateinit var timeTotal: TextView
    private lateinit var btnPlay: ImageButton
    private lateinit var btnReplay: ImageButton
    private lateinit var btnForward: ImageButton
    private lateinit var btnLock: ImageButton
    private lateinit var speedButton: Button
    private lateinit var skipButton: Button
    private lateinit var sleepInfo: TextView

    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var controller: MediaController? = null
    private lateinit var book: Book

    private val handler = Handler(Looper.getMainLooper())
    private var sleepEndAt = 0L
    private var isTracking = false

    // Cadeado de reprodução
    private var locked = false
    private var lockOverlay: View? = null
    private var unlockRunnable: Runnable? = null

    // Controle da sugestão do próximo livro
    private var completionHandled = false
    private var pendingCompletion = false

    private var coverBookId: String? = null

    // Evita reiniciar um livro concluído apenas por causa de uma rotação de tela.
    private var recreated = false

    private val ticker = object : Runnable {
        override fun run() {
            updateProgress()
            updateSleep()
            handler.postDelayed(this, 500)
        }
    }

    private val playerListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) = render()

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) = render()

        override fun onPlaybackStateChanged(playbackState: Int) {
            render()
            if (playbackState == Player.STATE_ENDED) onBookEnded()
        }
    }

    /** O botão voltar também fica bloqueado enquanto o cadeado estiver ativo. */
    private val backCallback = object : OnBackPressedCallback(true) {
        override fun handleOnBackPressed() {
            if (!locked) {
                isEnabled = false
                onBackPressedDispatcher.onBackPressed()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_player)
        recreated = savedInstanceState != null

        cover = findViewById(R.id.cover)
        title = findViewById(R.id.title)
        author = findViewById(R.id.author)
        chapter = findViewById(R.id.chapter)
        percent = findViewById(R.id.percent)
        seek = findViewById(R.id.seek)
        timeCurrent = findViewById(R.id.timeCurrent)
        timeTotal = findViewById(R.id.timeTotal)
        btnPlay = findViewById(R.id.btnPlay)
        btnReplay = findViewById(R.id.btnReplay)
        btnForward = findViewById(R.id.btnForward)
        btnLock = findViewById(R.id.btnLock)
        speedButton = findViewById(R.id.btnSpeed)
        skipButton = findViewById(R.id.btnSkip)
        sleepInfo = findViewById(R.id.sleepInfo)

        onBackPressedDispatcher.addCallback(this, backCallback)

        findViewById<ImageButton>(R.id.btnBack).setOnClickListener { finish() }

        btnPlay.setOnClickListener { togglePlay() }
        btnReplay.setOnClickListener { seekBy(-PlaybackSettings.skipMs(this)) }
        btnForward.setOnClickListener { seekBy(PlaybackSettings.skipMs(this)) }
        skipButton.setOnClickListener { showSkipDialog() }

        // Toque longo nos botões de salto também abre a configuração.
        btnReplay.setOnLongClickListener {
            showSkipDialog()
            true
        }
        btnForward.setOnLongClickListener {
            showSkipDialog()
            true
        }

        findViewById<ImageButton>(R.id.btnPrev).setOnClickListener {
            val c = controller ?: return@setOnClickListener
            c.seekToPreviousMediaItem()
        }
        findViewById<ImageButton>(R.id.btnNext).setOnClickListener {
            val c = controller ?: return@setOnClickListener
            c.seekToNextMediaItem()
        }

        btnLock.setOnClickListener { setLocked(!locked) }

        speedButton.setOnClickListener { showSpeedDialog() }
        findViewById<TextView>(R.id.btnSleep).setOnClickListener { showSleepDialog() }
        findViewById<TextView>(R.id.btnChapters).setOnClickListener { showChaptersDialog() }

        seek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(bar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) timeCurrent.text = formatTime(progress.toLong())
            }

            override fun onStartTrackingTouch(bar: SeekBar?) {
                isTracking = true
            }

            override fun onStopTrackingTouch(bar: SeekBar?) {
                isTracking = false
                controller?.seekTo(seek.progress.toLong())
            }
        })

        updateSkipLabels()
        setLocked(PlaybackSettings.isLocked(this))
    }

    override fun onStart() {
        super.onStart()
        if (controllerFuture == null) connect()
    }

    override fun onResume() {
        super.onResume()
        maybeShowCompletion()
    }

    override fun onStop() {
        saveProgress()
        handler.removeCallbacks(ticker)
        controllerFuture?.let { MediaController.releaseFuture(it) }
        controllerFuture = null
        controller = null
        super.onStop()
    }

    override fun onDestroy() {
        cancelUnlockCountdown()
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    private fun connect() {
        val token = SessionToken(this, ComponentName(this, PlaybackService::class.java))
        val future = MediaController.Builder(this, token).buildAsync()
        controllerFuture = future
        future.addListener({
            runOnUiThread { onControllerReady() }
        }, MoreExecutors.directExecutor())
    }

    private fun onControllerReady() {
        val c = try {
            controllerFuture?.get()
        } catch (_: Throwable) {
            null
        } ?: return

        controller = c
        c.addListener(playerListener)

        val requestedId = intent.getStringExtra(EXTRA_BOOK_ID)
        val currentId = c.currentMediaItem?.mediaId?.substringBefore('|')
        val targetId = requestedId ?: currentId ?: ProgressStore.lastBook(this)
        book = Library.find(this, targetId) ?: run {
            finish()
            return
        }

        val shouldStart = c.currentMediaItem == null ||
            (requestedId != null && currentId != book.id) ||
            // Tocar de novo um livro que já terminou recomeça do início.
            (requestedId != null && c.playbackState == Player.STATE_ENDED && !recreated)
        if (shouldStart) {
            startBook(c)
        } else if (c.playbackState == Player.STATE_ENDED) {
            // A atividade foi reaberta depois que o livro terminou.
            completionHandled = true
            pendingCompletion = true
            setLocked(false)
        }

        render()
        updatePercent(c)
        handler.removeCallbacks(ticker)
        handler.post(ticker)
        maybeShowCompletion()
    }

    private fun startBook(c: MediaController) {
        // Antes de trocar de livro, guarda a posição exata do que estava tocando.
        val currentId = c.currentMediaItem?.mediaId?.substringBefore('|')
        if (currentId != null && currentId != book.id) {
            val currentDuration = c.duration
            if (currentDuration > 0L) {
                ProgressStore.saveDuration(
                    this,
                    currentId,
                    c.currentMediaItemIndex,
                    currentDuration
                )
            }
            ProgressStore.save(this, currentId, c.currentMediaItemIndex, c.currentPosition)
        }

        val finished = ProgressStore.isFinished(this, book.id)
        val chapterIndex = if (finished) 0 else ProgressStore.chapter(this, book.id)
            .coerceIn(0, book.chapters.lastIndex)
        val position = if (finished) 0L else ProgressStore.position(this, book.id)

        if (finished) {
            // Ao reabrir um livro concluído, começa de novo — mas mantém as
            // durações já conhecidas para a porcentagem.
            ProgressStore.reset(this, book.id)
            Toast.makeText(this, R.string.resume_finished, Toast.LENGTH_SHORT).show()
        }

        val items = book.chapters.mapIndexed { index, item ->
            MediaItem.Builder()
                .setMediaId("${book.id}|$index")
                .setUri(item.uri)
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle(item.title)
                        .setArtist(book.author)
                        .setAlbumTitle(book.title)
                        .build()
                )
                .build()
        }

        completionHandled = false
        pendingCompletion = false
        coverBookId = null

        c.setMediaItems(items, chapterIndex, position)
        c.prepare()
        c.play()
    }

    private fun render() {
        val c = controller ?: return
        if (!::book.isInitialized) return

        title.text = book.title
        author.text = book.author
        showCoverArtwork(book)

        val index = c.currentMediaItemIndex.coerceAtLeast(0)
        chapter.text = getString(R.string.chapter_of, index + 1, book.chapters.size)

        btnPlay.setImageResource(
            if (c.isPlaying) android.R.drawable.ic_media_pause
            else android.R.drawable.ic_media_play
        )
        btnPlay.contentDescription = getString(if (c.isPlaying) R.string.pause else R.string.play)

        speedButton.text = formatSpeed(c.playbackParameters.speed)
        updateProgress()
    }

    private fun updateProgress() {
        val c = controller ?: return
        val duration = c.duration.coerceAtLeast(0L)
        if (!isTracking && duration > 0L) {
            seek.max = duration.toInt()
            seek.progress = c.currentPosition.toInt()
            timeCurrent.text = formatTime(c.currentPosition)
            timeTotal.text = formatTime(duration)
        }
        updatePercent(c)
    }

    /** Mostra "45% ouvido" usando o total de todos os capítulos. */
    private fun updatePercent(c: MediaController) {
        if (!::book.isInitialized) return
        val value = BookProgress.percent(
            context = this,
            book = book,
            chapterIndex = c.currentMediaItemIndex,
            positionMs = c.currentPosition,
            currentDurationMs = c.duration.coerceAtLeast(0L)
        )
        percent.text = getString(R.string.percent_listened, value)
    }

    private fun togglePlay() {
        val c = controller ?: return
        when {
            c.isPlaying -> c.pause()
            c.playbackState == Player.STATE_ENDED -> {
                ProgressStore.setFinished(this, book.id, false)
                ProgressStore.reset(this, book.id)
                completionHandled = false
                pendingCompletion = false
                c.seekTo(0, 0L)
                c.play()
            }
            else -> c.play()
        }
    }

    private fun seekBy(deltaMs: Long) {
        val c = controller ?: return
        val duration = c.duration.coerceAtLeast(0L)
        val target = (c.currentPosition + deltaMs)
            .coerceIn(0L, if (duration > 0) duration else Long.MAX_VALUE)
        c.seekTo(target)
        updatePercent(c)
    }

    // ---------------------------------------------------------------------
    // Cadeado de reprodução
    // ---------------------------------------------------------------------

    private fun setLocked(value: Boolean) {
        locked = value
        PlaybackSettings.setLocked(this, value)
        if (value) backCallback.isEnabled = true

        btnLock.setImageResource(if (value) R.drawable.ic_lock else R.drawable.ic_lock_open)
        btnLock.contentDescription = getString(if (value) R.string.unlock else R.string.lock)

        if (value) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            showLockOverlay()
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            hideLockOverlay()
        }
    }

    /** Camada transparente que captura todos os toques enquanto bloqueado. */
    private fun showLockOverlay() {
        if (lockOverlay != null) return

        val root = findViewById<FrameLayout>(android.R.id.content)
        val overlay = FrameLayout(this).apply {
            setBackgroundColor(getColor(R.color.lock_overlay))
            isClickable = true
            isFocusable = true
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        }

        val hint = TextView(this).apply {
            text = getString(R.string.lock_hint)
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            textSize = 16f
            setLineSpacing(0f, 1.4f)
            setPadding(dp(32), dp(32), dp(32), dp(32))
        }
        overlay.addView(
            hint,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        )

        // Segurar por 3 segundos em qualquer lugar desbloqueia.
        overlay.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> startUnlockCountdown()
                MotionEvent.ACTION_UP,
                MotionEvent.ACTION_CANCEL -> cancelUnlockCountdown()
            }
            true
        }

        root.addView(overlay)
        lockOverlay = overlay
    }

    private fun hideLockOverlay() {
        cancelUnlockCountdown()
        val overlay = lockOverlay ?: return
        (overlay.parent as? FrameLayout)?.removeView(overlay)
        lockOverlay = null
    }

    private fun startUnlockCountdown() {
        cancelUnlockCountdown()
        val runnable = Runnable {
            setLocked(false)
            Toast.makeText(this, R.string.unlocked, Toast.LENGTH_SHORT).show()
        }
        unlockRunnable = runnable
        handler.postDelayed(runnable, 3_000L)
    }

    private fun cancelUnlockCountdown() {
        unlockRunnable?.let { handler.removeCallbacks(it) }
        unlockRunnable = null
    }

    // ---------------------------------------------------------------------
    // Fim do audiolivro e sugestão do próximo
    // ---------------------------------------------------------------------

    private fun onBookEnded() {
        if (completionHandled || !::book.isInitialized) return
        completionHandled = true
        pendingCompletion = true
        // Libera o cadeado para que o usuário veja a sugestão.
        setLocked(false)
        maybeShowCompletion()
    }

    private fun maybeShowCompletion() {
        if (!pendingCompletion || isFinishing || isDestroyed) return
        if (!lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
            handler.postDelayed({ maybeShowCompletion() }, 500)
            return
        }

        pendingCompletion = false
        showCompletionDialog()
    }

    private fun showCompletionDialog() {
        val next = nextBook()
        val message = if (next != null) {
            getString(R.string.finished_message, book.title)
        } else {
            getString(R.string.finished_message_last, book.title)
        }

        val builder = MaterialAlertDialogBuilder(this)
            .setTitle(R.string.finished_title)
            .setMessage(message)
            .setNegativeButton(R.string.not_now, null)

        if (next != null) {
            builder.setPositiveButton(R.string.listen_next) { _, _ ->
                playBook(next)
            }
        } else {
            builder.setPositiveButton(android.R.string.ok, null)
        }
        builder.show()
    }

    /** Próximo da fila: primeiro um livro ainda não ouvido, na ordem da lista. */
    private fun nextBook(): Book? {
        val all = Library.all(this)
        if (all.size <= 1) return null

        val start = all.indexOfFirst { it.id == book.id }
        val ordered = if (start >= 0) {
            all.drop(start + 1) + all.take(start)
        } else {
            all
        }

        return ordered.firstOrNull {
            it.id != book.id && !ProgressStore.isFinished(this, it.id)
        } ?: ordered.firstOrNull { it.id != book.id }
    }

    private fun playBook(next: Book) {
        saveProgress()
        val c = controller ?: return
        book = next
        coverBookId = null
        completionHandled = false
        pendingCompletion = false
        startBook(c)
        render()
    }

    // ---------------------------------------------------------------------
    // Diálogos e utilitários
    // ---------------------------------------------------------------------

    private fun showChaptersDialog() {
        val titles = book.chapters.mapIndexed { index, item ->
            "${index + 1}. ${item.title}"
        }.toTypedArray()

        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.chapters)
            .setItems(titles) { _, which ->
                controller?.seekTo(which, 0)
                controller?.play()
            }
            .show()
    }

    private fun showSpeedDialog() {
        val speeds = floatArrayOf(0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f)
        val labels = speeds.map { formatSpeed(it) }.toTypedArray()
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.speed)
            .setItems(labels) { _, which ->
                controller?.setPlaybackSpeed(speeds[which])
                speedButton.text = labels[which]
            }
            .show()
    }

    /** Intervalo configurável dos botões ⏪/⏩ (também vale para a notificação). */
    private fun showSkipDialog() {
        val options = PlaybackSettings.skipOptions
        val labels = options.map { getString(R.string.skip_seconds_option, it) }.toTypedArray()
        val checked = options.indexOf(PlaybackSettings.skipSeconds(this)).coerceAtLeast(0)

        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.skip_interval)
            .setSingleChoiceItems(labels, checked) { dialog, which ->
                PlaybackSettings.setSkipSeconds(this, options[which])
                updateSkipLabels()
                dialog.dismiss()
            }
            .show()
    }

    private fun updateSkipLabels() {
        val seconds = PlaybackSettings.skipSeconds(this)
        skipButton.text = getString(R.string.skip_seconds_short, seconds)
        btnReplay.contentDescription = getString(R.string.back_seconds, seconds)
        btnForward.contentDescription = getString(R.string.forward_seconds, seconds)
    }

    private fun showSleepDialog() {
        val minutes = intArrayOf(15, 30, 45, 60, 90)
        val labels = minutes.map { getString(R.string.sleep_minutes, it) }.toMutableList()
        labels.add(getString(R.string.sleep_off))

        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.sleep_timer)
            .setItems(labels.toTypedArray()) { _, which ->
                sleepEndAt = if (which < minutes.size) {
                    System.currentTimeMillis() + minutes[which] * 60_000L
                } else {
                    0L
                }
                updateSleep()
            }
            .show()
    }

    private fun updateSleep() {
        if (sleepEndAt <= 0L) {
            sleepInfo.visibility = View.GONE
            return
        }
        val remaining = sleepEndAt - System.currentTimeMillis()
        if (remaining <= 0L) {
            sleepEndAt = 0L
            sleepInfo.visibility = View.GONE
            controller?.pause()
            return
        }
        sleepInfo.visibility = View.VISIBLE
        sleepInfo.text = getString(R.string.sleep_remaining, formatTime(remaining))
    }

    private fun saveProgress() {
        val c = controller ?: return
        val bookId = c.currentMediaItem?.mediaId?.substringBefore('|') ?: return

        val duration = c.duration
        if (duration > 0L) {
            ProgressStore.saveDuration(this, bookId, c.currentMediaItemIndex, duration)
        }
        ProgressStore.save(this, bookId, c.currentMediaItemIndex, c.currentPosition)
    }

    private fun showCoverArtwork(currentBook: Book) {
        if (coverBookId == currentBook.id) return
        coverBookId = currentBook.id

        cover.tag = currentBook.id
        cover.text = currentBook.initial
        cover.background = coverDrawable(currentBook.color)

        if (currentBook.coverUri.isNullOrBlank()) return

        CoverLoader.load(this, currentBook, dp(190)) { bitmap ->
            if (bitmap != null && cover.tag == currentBook.id) {
                cover.text = ""
                cover.background = CoverLoader.roundedDrawable(this, bitmap, 22f)
            }
        }
    }

    private fun coverDrawable(color: Int): GradientDrawable {
        val darker = Color.rgb(
            (Color.red(color) * 0.62f).toInt(),
            (Color.green(color) * 0.62f).toInt(),
            (Color.blue(color) * 0.62f).toInt()
        )
        val density = resources.displayMetrics.density
        return GradientDrawable(
            GradientDrawable.Orientation.TL_BR,
            intArrayOf(color, darker)
        ).apply {
            cornerRadius = 22f * density
        }
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()

    private fun formatSpeed(speed: Float): String {
        val value = String.format(Locale.getDefault(), "%.2f", speed)
        return value.trimEnd('0').trimEnd('.') + "x"
    }

    private fun formatTime(ms: Long): String {
        val total = (ms / 1000).coerceAtLeast(0)
        val hours = total / 3600
        val minutes = (total % 3600) / 60
        val seconds = total % 60
        return if (hours > 0) {
            String.format(Locale.getDefault(), "%d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
        }
    }
}
