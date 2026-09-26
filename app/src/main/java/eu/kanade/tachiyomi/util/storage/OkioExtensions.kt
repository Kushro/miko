package eu.kanade.tachiyomi.util.storage

import okio.Buffer
import okio.BufferedSource
import okio.buffer
import okio.sink
import java.io.File
import java.io.OutputStream

// MIKO -->
/** Size of each copied chunk when the copy has to report progress to a throttle. */
private const val THROTTLED_COPY_CHUNK_SIZE = 8L * 1024
// MIKO <--

/**
 * Saves the given source to a file and closes it. Directories will be created if needed.
 *
 * @param file the file where the source is copied.
 */
fun BufferedSource.saveTo(file: File) {
    try {
        // Create parent dirs if needed
        file.parentFile?.mkdirs()

        // Copy to destination
        saveTo(file.outputStream())
    } catch (e: Exception) {
        close()
        file.delete()
        throw e
    }
}

/**
 * Saves the given source to an output stream and closes both resources.
 *
 * @param stream the stream where the source is copied.
 */
fun BufferedSource.saveTo(stream: OutputStream) {
    use { input ->
        stream.sink().buffer().use {
            it.writeAll(input)
            it.flush()
        }
    }
}

// MIKO -->
/**
 * Saves the given source to an output stream and closes both resources, copying it in small chunks and
 * reporting each of them to [onChunk] (which may suspend, e.g. to wait on a bandwidth throttle).
 *
 * @param stream the stream where the source is copied.
 * @param onChunk called with the size in bytes of every copied chunk.
 */
suspend fun BufferedSource.saveTo(stream: OutputStream, onChunk: suspend (Long) -> Unit) {
    use { input ->
        stream.sink().buffer().use { output ->
            val buffer = Buffer()
            while (true) {
                val read = input.read(buffer, THROTTLED_COPY_CHUNK_SIZE)
                if (read == -1L) break
                output.write(buffer, read)
                onChunk(read)
            }
            output.flush()
        }
    }
}
// MIKO <--
