package com.meuaudiobook.app.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.LruCache
import androidx.core.graphics.drawable.RoundedBitmapDrawableFactory
import com.meuaudiobook.app.data.Book
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.concurrent.Executors

/**
 * Carrega capas de forma assíncrona, com cache em memória e em disco.
 *
 * A capa pode vir de:
 * - uma imagem embutida no arquivo de áudio (caminho local salvo pelo app);
 * - uma URL (as capas dos exemplos do LibriVox).
 *
 * Quando não existe capa (ou não há internet), quem chamou mantém a capa
 * gerada a partir da cor do livro.
 */
object CoverLoader {

    private val cache = object : LruCache<String, Bitmap>(12 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }

    private val executor = Executors.newFixedThreadPool(2)
    private val mainHandler = Handler(Looper.getMainLooper())

    fun load(
        context: Context,
        book: Book,
        targetSizePx: Int,
        onLoaded: (Bitmap?) -> Unit
    ) {
        val source = book.coverUri
        if (source.isNullOrBlank()) {
            deliver(null, onLoaded)
            return
        }

        // Separa em faixas de tamanho para não usar uma miniatura borrada no player.
        val bucket = if (targetSizePx <= 220) 220 else 700
        val key = "$source@$bucket"
        cache.get(key)?.let {
            deliver(it, onLoaded)
            return
        }

        val appContext = context.applicationContext
        executor.execute {
            val bitmap = decode(appContext, source, bucket)
            if (bitmap != null) cache.put(key, bitmap)
            deliver(bitmap, onLoaded)
        }
    }

    /** Converte a capa em um drawable quadrado com cantos arredondados. */
    fun roundedDrawable(context: Context, bitmap: Bitmap, cornerDp: Float): Drawable {
        val drawable = RoundedBitmapDrawableFactory.create(context.resources, bitmap)
        drawable.cornerRadius = cornerDp * context.resources.displayMetrics.density
        drawable.isCircular = false
        drawable.setAntiAlias(true)
        return drawable
    }

    private fun deliver(bitmap: Bitmap?, callback: (Bitmap?) -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            callback(bitmap)
        } else {
            mainHandler.post { callback(bitmap) }
        }
    }

    private fun decode(context: Context, source: String, targetSizePx: Int): Bitmap? {
        val bytes = try {
            when {
                source.startsWith("http://") || source.startsWith("https://") ->
                    remoteFile(context, source)?.readBytes()
                source.startsWith("content://") ->
                    context.contentResolver.openInputStream(Uri.parse(source))?.use { it.readBytes() }
                source.startsWith("file://") ->
                    Uri.parse(source).path?.let { File(it).readBytes() }
                else -> File(source).readBytes()
            }
        } catch (_: Throwable) {
            null
        } ?: return null

        return decodeBytes(bytes, targetSizePx)
    }

    private fun decodeBytes(bytes: ByteArray, targetSizePx: Int): Bitmap? {
        return try {
            // Primeiro mede a imagem para reduzir a memória usada.
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

            var sample = 1
            while (bounds.outWidth / sample > targetSizePx * 2 ||
                bounds.outHeight / sample > targetSizePx * 2
            ) {
                sample *= 2
            }

            val bitmap = BitmapFactory.decodeByteArray(
                bytes,
                0,
                bytes.size,
                BitmapFactory.Options().apply { inSampleSize = sample }
            ) ?: return null

            centerCropSquare(bitmap)
        } catch (_: Throwable) {
            null
        }
    }

    /** Recorta o centro da imagem para deixar a capa quadrada, como um álbum. */
    private fun centerCropSquare(bitmap: Bitmap): Bitmap {
        val side = minOf(bitmap.width, bitmap.height)
        if (side <= 0) return bitmap
        val x = (bitmap.width - side) / 2
        val y = (bitmap.height - side) / 2
        return try {
            Bitmap.createBitmap(bitmap, x, y, side, side)
        } catch (_: Throwable) {
            bitmap
        }
    }

    private fun remoteFile(context: Context, source: String): File? {
        val dir = File(context.filesDir, "covers").apply { mkdirs() }
        val file = File(dir, "remote_${hash(source)}.img")
        if (file.exists() && file.length() > 0L) return file

        return try {
            val connection = URL(source).openConnection() as HttpURLConnection
            connection.connectTimeout = 8_000
            connection.readTimeout = 12_000
            connection.instanceFollowRedirects = true
            connection.setRequestProperty("User-Agent", "MeuAudiobook/1.0")
            if (connection.responseCode !in 200..299) {
                connection.disconnect()
                return null
            }
            val temp = File(dir, "temp_${System.currentTimeMillis()}.img")
            connection.inputStream.use { input ->
                temp.outputStream().use { output -> input.copyTo(output) }
            }
            connection.disconnect()
            if (temp.length() <= 0L) {
                temp.delete()
                return null
            }
            if (!temp.renameTo(file)) {
                temp.copyTo(file, overwrite = true)
                temp.delete()
            }
            file
        } catch (_: Throwable) {
            null
        }
    }

    private fun hash(value: String): String {
        val digest = MessageDigest.getInstance("SHA-1")
        return digest.digest(value.toByteArray()).joinToString("") { "%02x".format(it) }
    }
}
