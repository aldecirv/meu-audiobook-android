package com.meuaudiobook.app.playback

import android.app.PendingIntent
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.CommandButton
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.meuaudiobook.app.PlayerActivity
import com.meuaudiobook.app.R
import com.meuaudiobook.app.data.PlaybackSettings
import com.meuaudiobook.app.data.PlaybackState
import com.meuaudiobook.app.data.ProgressStore

/**
 * Serviço de reprodução: mantém o áudio tocando em segundo plano e publica
 * a notificação com controles (Media3 cuida da notificação e do MediaSession).
 *
 * Além do básico, ele:
 * - salva a posição a cada 5 segundos (retomada exata);
 * - marca o livro como "Ouvido" ao chegar ao fim;
 * - adiciona botões de retroceder/avançar na notificação, respeitando o
 *   intervalo configurado pelo usuário;
 * - aceita OPUS/OGG (o ExoPlayer já traz o extrator e usa o decodificador
 *   Opus do próprio Android).
 */
class PlaybackService : MediaSessionService() {

    companion object {
        const val ACTION_SKIP_BACK = "com.meuaudiobook.app.action.SKIP_BACK"
        const val ACTION_SKIP_FORWARD = "com.meuaudiobook.app.action.SKIP_FORWARD"
        private const val SAVE_INTERVAL_MS = 5_000L
    }

    private var mediaSession: MediaSession? = null
    private val handler = Handler(Looper.getMainLooper())

    /** Salvamento periódico enquanto o áudio estiver tocando. */
    private val saveTicker = object : Runnable {
        override fun run() {
            val player = mediaSession?.player
            if (player != null) {
                if (player.isPlaying) saveProgress()
                publishState()
            }
            handler.postDelayed(this, SAVE_INTERVAL_MS)
        }
    }

    override fun onCreate() {
        super.onCreate()

        // Fallback de decodificador: ajuda em aparelhos que não têm um codec
        // específico (inclui arquivos OPUS reproduzidos via codec do sistema).
        val renderersFactory = DefaultRenderersFactory(this)
            .setEnableDecoderFallback(true)

        val skipMs = PlaybackSettings.skipMs(this)
        val player = ExoPlayer.Builder(this, renderersFactory)
            .setSeekBackIncrementMs(skipMs)
            .setSeekForwardIncrementMs(skipMs)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_SPEECH)
                    .build(),
                /* handleAudioFocus = */ true
            )
            .setHandleAudioBecomingNoisy(true)
            .build()

        val openPlayer = PendingIntent.getActivity(
            this,
            0,
            Intent(this, PlayerActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        mediaSession = MediaSession.Builder(this, player)
            .setSessionActivity(openPlayer)
            .setCallback(SessionCallback())
            .setMediaButtonPreferences(buildSkipButtons())
            .build()

        player.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (!isPlaying) saveProgress()
                publishState()
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                saveProgress()
                publishState()
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED) {
                    saveProgress()
                    markAsFinished()
                }
                publishState()
            }

            override fun onPositionDiscontinuity(
                oldPosition: Player.PositionInfo,
                newPosition: Player.PositionInfo,
                reason: Int
            ) {
                publishState()
            }
        })

        handler.post(saveTicker)
    }

    override fun onGetSession(
        controllerInfo: MediaSession.ControllerInfo
    ): MediaSession? = mediaSession

    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = mediaSession?.player
        if (player == null || !player.playWhenReady || player.mediaItemCount == 0) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        saveProgress()
        PlaybackState.clear()
        PlaybackSettings.setLocked(this, false)
        handler.removeCallbacks(saveTicker)
        mediaSession?.run {
            player.release()
            release()
        }
        mediaSession = null
        super.onDestroy()
    }

    /**
     * Callback da sessão: libera os comandos personalizados e trata os toques
     * nos botões da notificação.
     */
    private inner class SessionCallback : MediaSession.Callback {

        override fun onConnect(
            session: MediaSession,
            controller: MediaSession.ControllerInfo
        ): MediaSession.ConnectionResult {
            // Permite que os controles (notificação, Bluetooth, app) enviem
            // os comandos de avançar/retroceder.
            val commands = MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS
                .buildUpon()
                .add(SessionCommand(ACTION_SKIP_BACK, Bundle.EMPTY))
                .add(SessionCommand(ACTION_SKIP_FORWARD, Bundle.EMPTY))
                .build()

            return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                .setAvailableSessionCommands(commands)
                .build()
        }

        override fun onCustomCommand(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            customCommand: SessionCommand,
            args: Bundle
        ): ListenableFuture<SessionResult> {
            return when (customCommand.customAction) {
                ACTION_SKIP_BACK -> {
                    seekBy(-PlaybackSettings.skipMs(this@PlaybackService))
                    Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
                }
                ACTION_SKIP_FORWARD -> {
                    seekBy(PlaybackSettings.skipMs(this@PlaybackService))
                    Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
                }
                else -> Futures.immediateFuture(
                    SessionResult(SessionResult.RESULT_ERROR_NOT_SUPPORTED)
                )
            }
        }
    }

    /** Botões ⏪/⏩ exibidos na notificação e no controle de mídia. */
    private fun buildSkipButtons(): List<CommandButton> = listOf(
        CommandButton.Builder(CommandButton.ICON_UNDEFINED)
            .setCustomIconResId(R.drawable.ic_replay)
            .setDisplayName(getString(R.string.notification_rewind))
            .setSessionCommand(SessionCommand(ACTION_SKIP_BACK, Bundle.EMPTY))
            .build(),
        CommandButton.Builder(CommandButton.ICON_UNDEFINED)
            .setCustomIconResId(R.drawable.ic_forward)
            .setDisplayName(getString(R.string.notification_forward))
            .setSessionCommand(SessionCommand(ACTION_SKIP_FORWARD, Bundle.EMPTY))
            .build()
    )

    private fun seekBy(deltaMs: Long) {
        val player = mediaSession?.player ?: return
        val duration = player.duration.coerceAtLeast(0L)
        val target = (player.currentPosition + deltaMs)
            .coerceIn(0L, if (duration > 0L) duration else Long.MAX_VALUE)
        player.seekTo(target)
        publishState()
    }

    /** Marca o audiolivro como ouvido e guarda a posição final. */
    private fun markAsFinished() {
        val player = mediaSession?.player ?: return
        val bookId = player.currentMediaItem?.mediaId?.substringBefore('|', "") ?: return
        if (bookId.isBlank()) return

        ProgressStore.setFinished(this, bookId, true)
        ProgressStore.save(
            this,
            bookId,
            player.currentMediaItemIndex.coerceAtLeast(0),
            player.duration.coerceAtLeast(player.currentPosition)
        )
    }

    private fun saveProgress() {
        val player = mediaSession?.player ?: return
        val mediaId = player.currentMediaItem?.mediaId ?: return
        val bookId = mediaId.substringBefore('|', "")
        if (bookId.isBlank()) return

        val duration = player.duration
        if (duration > 0L) {
            ProgressStore.saveDuration(
                this,
                bookId,
                player.currentMediaItemIndex.coerceAtLeast(0),
                duration
            )
        }
        ProgressStore.save(
            this,
            bookId,
            player.currentMediaItemIndex.coerceAtLeast(0),
            player.currentPosition
        )
    }

    /** Mantém a biblioteca informada sobre o item que está tocando. */
    private fun publishState() {
        val player = mediaSession?.player ?: return
        val bookId = player.currentMediaItem?.mediaId?.substringBefore('|', "")
        PlaybackState.update(
            bookId = bookId,
            chapterIndex = player.currentMediaItemIndex.coerceAtLeast(0),
            positionMs = player.currentPosition.coerceAtLeast(0L),
            isPlaying = player.isPlaying
        )
    }
}
