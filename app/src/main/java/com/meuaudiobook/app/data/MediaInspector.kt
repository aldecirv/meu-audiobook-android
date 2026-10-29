package com.meuaudiobook.app.data

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import java.io.File

/**
 * Lê metadados dos arquivos escolhidos pelo usuário:
 * - duração de cada faixa (para a porcentagem do audiolivro);
 * - capa embutida (quando o arquivo tem arte no metadata).
 *
 * Funciona com MP3, M4A, OGG, OPUS, FLAC e WAV, desde que o Android consiga
 * ler o metadata do arquivo.
 */
object MediaInspector {

    data class Inspection(
        val durations: List<Long>,
        val coverPath: String?
    )

    fun inspect(context: Context, uris: List<Uri>): Inspection {
        val coversDir = File(context.filesDir, "covers").apply { mkdirs() }
        val durations = ArrayList<Long>(uris.size)
        var coverPath: String? = null

        uris.forEach { uri ->
            val retriever = MediaMetadataRetriever()
            var duration = 0L
            try {
                retriever.setDataSource(context, uri)

                duration = retriever
                    .extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                    ?.toLongOrNull()
                    ?.coerceAtLeast(0L)
                    ?: 0L

                if (coverPath == null) {
                    try {
                        val artwork = retriever.embeddedPicture
                        if (artwork != null && artwork.isNotEmpty()) {
                            val file = File(coversDir, "cover_${System.currentTimeMillis()}.img")
                            file.writeBytes(artwork)
                            coverPath = file.absolutePath
                        }
                    } catch (_: Throwable) {
                        // Arquivo sem capa legível: mantém a capa colorida.
                    }
                }
            } catch (_: Throwable) {
                // Arquivo sem metadata legível: segue sem duração/capa.
            } finally {
                try {
                    retriever.release()
                } catch (_: Throwable) {
                    // Ignora falhas ao liberar o retriever.
                }
            }
            durations.add(duration)
        }

        return Inspection(durations = durations, coverPath = coverPath)
    }
}
