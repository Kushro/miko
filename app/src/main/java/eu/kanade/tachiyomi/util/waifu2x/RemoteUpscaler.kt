package eu.kanade.tachiyomi.util.waifu2x

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import android.util.JsonReader
import android.util.JsonToken
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okio.Buffer
import okio.BufferedSink
import okio.BufferedSource
import org.json.JSONArray
import org.json.JSONObject
import java.io.InputStreamReader
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.util.concurrent.TimeUnit

/**
 * Sends images to a remote upscaler server for GPU-accelerated processing.
 *
 * When the remote upscaler is enabled in settings, this replaces the local NCNN
 * pipeline. The server decides which model to use — no local model configuration
 * is needed on the device.
 *
 * Memory note: batch payloads/responses are tens of MB of base64 (the JSON for a
 * 4-page batch of upscaled PNGs was 39 MB in the field). Everything here streams —
 * the request body is base64-encoded straight into the socket and the response is
 * parsed with a pull [JsonReader] — so peak heap is one image, not the whole batch
 * ×3-4 copies. Materialising the JSON as a String was enough to OOM a 512 MB heap.
 */
object RemoteUpscaler {

    private const val TAG = "RemoteUpscaler"

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .readTimeout(300, TimeUnit.SECONDS)
        .build()

    // Batches are processed sequentially server-side, so a whole batch can take much
    // longer than a single image. Give batch requests a generous read/write timeout.
    private val batchClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .writeTimeout(120, TimeUnit.SECONDS)
        .readTimeout(600, TimeUnit.SECONDS)
        .build()

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()
    private val pngMediaType = "image/png".toMediaType()

    /**
     * Send a bitmap to the remote upscaler server and receive the upscaled bitmap.
     *
     * @param input Original decoded bitmap (ARGB_8888)
     * @param host Server IP address (e.g. "192.168.1.42")
     * @param port Server port (e.g. 8282)
     * @param onStatus Optional callback invoked with human-readable progress messages
     * @return Upscaled bitmap, or null on any failure
     */
    suspend fun process(
        input: Bitmap,
        host: String,
        port: Int,
        onStatus: suspend (String) -> Unit = {},
    ): Bitmap? = withContext(Dispatchers.IO) {
        if (host.isBlank()) {
            Log.w(TAG, "Remote upscaler host is blank — skipping")
            return@withContext null
        }

        try {
            onStatus("Connecting to $host:$port…")

            // 1. Encode bitmap to PNG bytes. An okio Buffer grows in 8 KB segments instead of
            //    doubling a byte[] like ByteArrayOutputStream (which peaks at ~3× the PNG size).
            val encoded = Buffer()
            input.compress(Bitmap.CompressFormat.PNG, 100, encoded.outputStream())
            val encodedSize = encoded.size

            onStatus("Uploading image (${encodedSize / 1024} KB)…")

            // 2. POST to remote server. Buffer.copy() shares segments, so retries re-send the
            //    same bytes without a byte[] copy of the PNG.
            val body = object : RequestBody() {
                override fun contentType(): MediaType = pngMediaType
                override fun contentLength(): Long = encodedSize
                override fun writeTo(sink: BufferedSink) {
                    sink.writeAll(encoded.copy())
                }
            }
            val request = Request.Builder()
                .url("http://$host:$port/upscale")
                .post(body)
                .build()

            client.newCall(request).execute().use { response ->
                onStatus("Processing on server…")

                if (!response.isSuccessful) {
                    Log.e(TAG, "Remote upscaler returned HTTP ${response.code}")
                    onStatus("Server error: HTTP ${response.code}")
                    return@withContext null
                }

                onStatus("Downloading result…")

                // 3. Decode response as bitmap straight from the socket — no byte[] copy of the PNG
                val result = decodeBitmap(response.body.source())
                if (result != null) onStatus("Enhancement complete")
                result
            }
        } catch (e: ConnectException) {
            Log.e(TAG, "Cannot connect to remote upscaler at $host:$port — is the server running?")
            onStatus("Connection failed — is the server running?")
            null
        } catch (e: SocketTimeoutException) {
            Log.e(TAG, "Remote upscaler timed out for $host:$port")
            onStatus("Server timed out")
            null
        } catch (e: Exception) {
            Log.e(TAG, "Remote upscaler failed: ${e.message}", e)
            onStatus("Remote enhancement failed")
            null
        }
    }

    /**
     * Ask the server to download a single image by URL and return the upscaled bitmap.
     *
     * Corresponds to `POST /upscale/url`. The server fetches the image itself (using a
     * generic browser User-Agent), so this offloads the download from the device. Returns
     * null on any failure — callers should fall back to [process] with the decoded bitmap
     * when this happens (e.g. the source is behind Cloudflare or needs auth headers).
     *
     * @param imageUrl Remote source image URL the server should download
     */
    suspend fun processUrl(
        imageUrl: String,
        host: String,
        port: Int,
        sourceHeaders: Map<String, String> = emptyMap(),
        onStatus: suspend (String) -> Unit = {},
    ): Bitmap? = withContext(Dispatchers.IO) {
        if (host.isBlank() || imageUrl.isBlank()) return@withContext null

        try {
            onStatus("Requesting server-side download…")
            val payload = JSONObject().put("url", imageUrl).also { obj ->
                if (sourceHeaders.isNotEmpty()) obj.put("headers", JSONObject(sourceHeaders))
            }.toString()
            val request = Request.Builder()
                .url("http://$host:$port/upscale/url")
                .post(payload.toRequestBody(jsonMediaType))
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.e(TAG, "Remote /upscale/url returned HTTP ${response.code}")
                    onStatus("Server error: HTTP ${response.code}")
                    return@withContext null
                }

                onStatus("Downloading result…")
                val result = decodeBitmap(response.body.source())
                if (result != null) onStatus("Enhancement complete")
                result
            }
        } catch (e: ConnectException) {
            Log.e(TAG, "Cannot connect to remote upscaler at $host:$port — is the server running?")
            onStatus("Connection failed — is the server running?")
            null
        } catch (e: SocketTimeoutException) {
            Log.e(TAG, "Remote upscaler timed out for $host:$port")
            onStatus("Server timed out")
            null
        } catch (e: Exception) {
            Log.e(TAG, "Remote /upscale/url failed: ${e.message}", e)
            onStatus("Remote enhancement failed")
            null
        }
    }

    /**
     * Send several images in a single request and receive the upscaled bitmaps.
     *
     * Corresponds to `POST /upscale/batch` (JSON `{images:[base64…]}`). The returned list
     * is aligned to the input order; entries the server failed to upscale are null.
     *
     * @param images Raw encoded image bytes (PNG/JPEG) — sent base64-encoded
     */
    suspend fun processBatch(
        images: List<ByteArray>,
        host: String,
        port: Int,
        onStatus: suspend (String) -> Unit = {},
    ): List<ByteArray?> = withContext(Dispatchers.IO) {
        if (host.isBlank() || images.isEmpty()) return@withContext emptyList()

        try {
            onStatus("Uploading batch of ${images.size} image(s)…")
            val request = Request.Builder()
                .url("http://$host:$port/upscale/batch")
                .post(BatchImagesBody(images))
                .build()

            batchClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.e(TAG, "Remote /upscale/batch returned HTTP ${response.code}")
                    onStatus("Server error: HTTP ${response.code}")
                    return@withContext List(images.size) { null }
                }
                onStatus("Received batch result…")
                parseBatchResponse(response.body.source(), images.size)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Remote /upscale/batch failed: ${e.message}", e)
            onStatus("Remote batch enhancement failed")
            List(images.size) { null }
        }
    }

    /**
     * Ask the server to download several images by URL and upscale them in one request.
     *
     * Corresponds to `POST /upscale/batch/url` (JSON `{urls:[…]}`). The server downloads
     * in parallel. The returned list is aligned to the input order; failures are null.
     */
    suspend fun processBatchUrl(
        urls: List<String>,
        host: String,
        port: Int,
        sourceHeaders: Map<String, String> = emptyMap(),
        onStatus: suspend (String) -> Unit = {},
    ): List<ByteArray?> = withContext(Dispatchers.IO) {
        if (host.isBlank() || urls.isEmpty()) return@withContext emptyList()

        try {
            onStatus("Requesting server-side download of ${urls.size} image(s)…")
            val arr = JSONArray()
            urls.forEach { arr.put(it) }
            val payload = JSONObject().put("urls", arr).also { obj ->
                if (sourceHeaders.isNotEmpty()) obj.put("headers", JSONObject(sourceHeaders))
            }.toString()
            val request = Request.Builder()
                .url("http://$host:$port/upscale/batch/url")
                .post(payload.toRequestBody(jsonMediaType))
                .build()

            batchClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.e(TAG, "Remote /upscale/batch/url returned HTTP ${response.code}")
                    onStatus("Server error: HTTP ${response.code}")
                    return@withContext List(urls.size) { null }
                }
                onStatus("Received batch result…")
                parseBatchResponse(response.body.source(), urls.size)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Remote /upscale/batch/url failed: ${e.message}", e)
            onStatus("Remote batch enhancement failed")
            List(urls.size) { null }
        }
    }

    /**
     * Decode a PNG/WebP response body into an ARGB_8888 bitmap directly from the stream.
     * Bitmap pixels live in native memory, so this keeps the Java heap out of the picture
     * except for the decoder's own read buffer.
     */
    private fun decodeBitmap(source: BufferedSource): Bitmap? {
        val options = BitmapFactory.Options().apply {
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        return source.inputStream().use { BitmapFactory.decodeStream(it, null, options) }
    }

    /**
     * `{"images":["<b64>", …]}` streamed straight into the socket. Base64 is emitted in
     * chunks whose length is a multiple of 3 so no padding appears mid-stream, and the exact
     * body length is announced up front (4·⌈n/3⌉ per image, all ASCII) so the server gets a
     * plain Content-Length request — no chunked transfer encoding, no in-memory payload.
     */
    private class BatchImagesBody(private val images: List<ByteArray>) : RequestBody() {
        override fun contentType(): MediaType = jsonMediaType

        override fun contentLength(): Long {
            var length = (PREFIX.length + SUFFIX.length).toLong()
            images.forEachIndexed { i, bytes ->
                if (i > 0) length++ // separating comma
                length += 2 + base64Length(bytes.size) // surrounding quotes
            }
            return length
        }

        override fun writeTo(sink: BufferedSink) {
            sink.writeUtf8(PREFIX)
            images.forEachIndexed { i, bytes ->
                if (i > 0) sink.writeUtf8(",")
                sink.writeUtf8("\"")
                var offset = 0
                while (offset < bytes.size) {
                    val len = minOf(ENCODE_CHUNK, bytes.size - offset)
                    sink.writeUtf8(Base64.encodeToString(bytes, offset, len, Base64.NO_WRAP))
                    offset += len
                }
                sink.writeUtf8("\"")
            }
            sink.writeUtf8(SUFFIX)
        }

        private fun base64Length(byteCount: Int): Long = 4L * ((byteCount + 2) / 3)

        private companion object {
            const val PREFIX = "{\"images\":["
            const val SUFFIX = "]}"

            /** Multiple of 3 so every chunk but the last encodes without padding. */
            const val ENCODE_CHUNK = 3 * 16 * 1024
        }
    }

    /**
     * Parse a batch response body into raw byte arrays aligned to the request order.
     * Bytes are the server's output PNG — written directly to cache without re-encoding.
     * Shape: {"batch_id":N,"results":[{"index":i,"success":bool,"image":"b64"|null,"error":"…"}]}.
     *
     * Pull-parsed so only one item's base64 string is ever in memory at a time.
     */
    private fun parseBatchResponse(source: BufferedSource, expectedSize: Int): List<ByteArray?> {
        val out = arrayOfNulls<ByteArray>(expectedSize)
        try {
            JsonReader(InputStreamReader(source.inputStream(), Charsets.UTF_8)).use { reader ->
                reader.beginObject()
                while (reader.hasNext()) {
                    if (reader.nextName() == "results" && reader.peek() == JsonToken.BEGIN_ARRAY) {
                        reader.beginArray()
                        var position = 0
                        while (reader.hasNext()) {
                            readBatchItem(reader, position++, expectedSize, out)
                        }
                        reader.endArray()
                    } else {
                        reader.skipValue()
                    }
                }
                reader.endObject()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse batch response: ${e.message}", e)
        }
        return out.toList()
    }

    /** Reads one `{index,success,image,error}` object; the field order is not assumed. */
    private fun readBatchItem(reader: JsonReader, position: Int, expectedSize: Int, out: Array<ByteArray?>) {
        var index = position
        var success = false
        var image: ByteArray? = null
        var error: String? = null

        reader.beginObject()
        while (reader.hasNext()) {
            when (reader.nextName()) {
                "index" -> if (reader.peek() == JsonToken.NULL) reader.nextNull() else index = reader.nextInt()
                "success" -> if (reader.peek() == JsonToken.NULL) reader.nextNull() else success = reader.nextBoolean()
                "image" -> if (reader.peek() == JsonToken.NULL) {
                    reader.nextNull()
                } else {
                    val b64 = reader.nextString()
                    if (b64.isNotEmpty()) image = Base64.decode(b64, Base64.DEFAULT)
                }
                "error" -> if (reader.peek() == JsonToken.NULL) reader.nextNull() else error = reader.nextString()
                else -> reader.skipValue()
            }
        }
        reader.endObject()

        if (index !in 0 until expectedSize) return
        if (success) {
            if (image != null) out[index] = image
        } else {
            Log.w(TAG, "Batch item $index failed: ${error.orEmpty()}")
        }
    }

    /**
     * Check if the remote upscaler server is reachable.
     *
     * Callers decide readiness from the returned map: an explicit `upscaler_ready == false`
     * means the server is up but can't upscale yet (missing binaries/model).
     *
     * @return Status response map, or null if unreachable
     */
    suspend fun checkStatus(host: String, port: Int): Map<String, Any?>? = withContext(Dispatchers.IO) {
        if (host.isBlank()) return@withContext null

        try {
            val request = Request.Builder()
                .url("http://$host:$port/status")
                .get()
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                parseStatusJson(response.body.string())
            }
        } catch (e: Exception) {
            Log.w(TAG, "Remote upscaler status check failed: ${e.message}")
            null
        }
    }

    /** Parse the flat /status JSON object into a plain map (JSONObject.NULL becomes null). */
    private fun parseStatusJson(json: String): Map<String, Any?> {
        return try {
            val obj = JSONObject(json)
            buildMap {
                obj.keys().forEach { key ->
                    put(key, obj.get(key).takeIf { it != JSONObject.NULL })
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse /status response: ${e.message}")
            emptyMap()
        }
    }
}
