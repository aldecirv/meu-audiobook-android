package com.meuaudiobook.app

import android.content.ComponentName
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.ImageButton
import android.widget.SeekBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import com.meuaudiobook.app.data.Book
import com.meuaudiobook.app.data.Library
import com.meuaudiobook.app.data.ProgressStore
import com.meuaudiobook.app.playback.PlaybackService
import java.util.Locale

class PlayerActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_BOOK_ID = "book_id"
    }

    private lateinit var cover: TextView
    private lateinit var title: TextView
    private lateinit var author: TextView
    private lateinit var chapter: TextView
    private lateinit var seek: SeekBar
    private lateinit var timeCurrent: TextView
    private lateinit var timeTotal: TextView
    private lateinit var btnPlay: ImageButton
    private lateinit var speedButton: android.widget.Button
    private lateinit var sleepInfo: TextView

    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var controller: MediaController? = null
    private lateinit var book: Book

    private val handler = Handler(Looper.getMainLooper())
    private var sleepEndAt = 0L
    private var isTracking = false

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

        override fun onPlaybackStateChanged(playbackState: Int) = render()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_player)

        cover = findViewById(R.id.cover)
        title = findViewById(R.id.title)
        author = findViewById(R.id.author)
        chapter = findViewById(R.id.chapter)
        seek = findViewById(R.id.seek)
        timeCurrent = findViewById(R.id.timeCurrent)
        timeTotal = findViewById(R.id.timeTotal)
        btnPlay = findViewById(R.id.btnPlay)
        speedButton = findViewById(R.id.btnSpeed)
        sleepInfo = findViewById(R.id.sleepInfo)

        findViewById<ImageButton>(R.id.btnBack).setOnClickListener { finish() }

        btnPlay.setOnClickListener { togglePlay() }
        findViewById<ImageButton>(R.id.btnReplay).setOnClickListener { seekBy(-15_000) }
        findViewById<ImageButton>(R.id.btnForward).setOnClickListener { seekBy(15_000) }
        findViewById<ImageButton>(R.id.btnPrev).setOnClickListener {
            val c = controller ?: return@setOnClickListener
            c.seekToPreviousMediaItem()
        }
        findViewById<ImageButton>(R.id.btnNext).setOnClickListener {
            val c = controller ?: return@setOnClickListener
            c.seekToNextMediaItem()
        }

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
    }

    override fun onStart() {
        super.onStart()
        if (controllerFuture == null) connect()
    }

    override fun onStop() {
        saveProgress()
        handler.removeCallbacks(ticker)
        controllerFuture?.let { MediaController.releaseFuture(it) }
        controllerFuture = null
        controller = null
        super.onStop()
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

        if (c.currentMediaItem == null || (requestedId != null && currentId != book.id)) {
            startBook(c)
        }

        render()
        handler.removeCallbacks(ticker)
        handler.post(ticker)
    }

    private fun startBook(c: MediaController) {
        val chapterIndex = ProgressStore.chapter(this, book.id)
            .coerceIn(0, book.chapters.lastIndex)
        val position = ProgressStore.position(this, book.id)

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

        c.setMediaItems(items, chapterIndex, position)
        c.prepare()
        c.play()
    }

    private fun render() {
        val c = controller ?: return
        if (!::book.isInitialized) return

        title.text = book.title
        author.text = book.author
        cover.text = book.initial
        cover.background = coverDrawable(book.color)

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
        if (!isTracking && duration > 0) {
            seek.max = duration.toInt()
            seek.progress = c.currentPosition.toInt()
            timeCurrent.text = formatTime(c.currentPosition)
            timeTotal.text = formatTime(duration)
        }
    }

    private fun togglePlay() {
        val c = controller ?: return
        when {
            c.isPlaying -> c.pause()
            c.playbackState == Player.STATE_ENDED -> {
                c.seekTo(0)
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
    }

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
            sleepInfo.visibility = android.view.View.GONE
            return
        }
        val remaining = sleepEndAt - System.currentTimeMillis()
        if (remaining <= 0L) {
            sleepEndAt = 0L
            sleepInfo.visibility = android.view.View.GONE
            controller?.pause()
            return
        }
        sleepInfo.visibility = android.view.View.VISIBLE
        sleepInfo.text = getString(R.string.sleep_remaining, formatTime(remaining))
    }

    private fun saveProgress() {
        val c = controller ?: return
        val bookId = c.currentMediaItem?.mediaId?.substringBefore('|') ?: return
        ProgressStore.save(this, bookId, c.currentMediaItemIndex, c.currentPosition)
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
