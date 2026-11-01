package eu.kanade.tachiyomi.util.waifu2x

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import android.util.JsonReader
import android.util.JsonToken
import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okio.Buffer
import okio.BufferedSink
import okio.BufferedSource
import org.json.JSONArray
import org.json.JSONObject
import tachiyomi.decoder.ImageDecoder
import java.io.ByteArrayInputStream
import java.io.InputStreamReader
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * Sends images to a remote upscaler server for GPU-accelerated processing.
 *
 * When the remote upscaler is enabled in settings, this replaces the local NCNN
 * pipeline. The server decides which model to use — no local model configuration
 * is needed on the device.
 *
 * Batches ask for NDJSON so each page can be saved/shown the moment the server finishes it
 * (see [streamBatch]); servers without streaming answer with one JSON document instead.
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
    private val octetStreamMediaType = "application/octet-stream".toMediaType()

    /** Media type of streamed batch responses, sent as `Accept` to opt into them. */
    private const val NDJSON = "application/x-ndjson"

    /**
     * Send a bitmap to the remote upscaler server and receive the upscaled bitmap.
     *
     * Re-encodes [input] as PNG on the device first, which is slow and usually larger than the
     * source file — prefer the [ByteArray] overload whenever the original encoded bytes exist.
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

            // Encode bitmap to PNG bytes. An okio Buffer grows in 8 KB segments instead of
            // doubling a byte[] like ByteArrayOutputStream (which peaks at ~3× the PNG size).
            val encoded = Buffer()
            input.compress(Bitmap.CompressFormat.PNG, 100, encoded.outputStream())
            val encodedSize = encoded.size

            // Buffer.copy() shares segments, so retries re-send the same bytes without a
            // byte[] copy of the PNG.
            val body = object : RequestBody() {
                override fun contentType(): MediaType = pngMediaType
                override fun contentLength(): Long = encodedSize
                override fun writeTo(sink: BufferedSink) {
                    sink.writeAll(encoded.copy())
                }
            }
            upload(body, encodedSize, host, port, onStatus, ::decodeBitmap)
        } catch (e: Exception) {
            Log.e(TAG, "Remote upscaler failed: ${e.message}", e)
            onStatus("Remote enhancement failed")
            null
        }
    }

    /**
     * Send an image's original encoded bytes (JPEG/PNG/WebP/…) to the remote upscaler server and
     * receive the upscaled bitmap. The server decodes by content, so formats it reads are sent
     * unchanged; the rest (e.g. AVIF on servers that don't list it) are converted to PNG first
     * (see [serverDecodableBytes]).
     *
     * @param input Encoded image bytes, exactly as read from the source or downloaded file
     * @return Upscaled bitmap, or null on any failure
     */
    suspend fun process(
        input: ByteArray,
        host: String,
        port: Int,
        onStatus: suspend (String) -> Unit = {},
    ): Bitmap? = uploadBytes(input, host, port, onStatus, ::decodeBitmap)

    /**
     * Like [process] with original bytes, but returns the server's encoded output (PNG/WebP)
     * untouched, so callers can cache and show it without a decode/re-encode round trip on the
     * device (see [ImageEnhancementCache.displayableBytes]).
     */
    suspend fun processRaw(
        input: ByteArray,
        host: String,
        port: Int,
        onStatus: suspend (String) -> Unit = {},
    ): ByteArray? = uploadBytes(input, host, port, onStatus, ::readNonEmpty)

    private suspend fun <T : Any> uploadBytes(
        input: ByteArray,
        host: String,
        port: Int,
        onStatus: suspend (String) -> Unit,
        read: (BufferedSource) -> T?,
    ): T? = withContext(Dispatchers.IO) {
        if (host.isBlank()) {
            Log.w(TAG, "Remote upscaler host is blank — skipping")
            return@withContext null
        }
        if (input.isEmpty()) return@withContext null
        val bytes = serverDecodableBytes(input, host, port) ?: return@withContext null

        onStatus("Connecting to $host:$port…")
        upload(bytes.toRequestBody(octetStreamMediaType), bytes.size.toLong(), host, port, onStatus, read)
    }

    /** `POST /upscale` with [body] and [read] the answer; shared by [process] and [processRaw]. */
    private suspend fun <T : Any> upload(
        body: RequestBody,
        size: Long,
        host: String,
        port: Int,
        onStatus: suspend (String) -> Unit,
        read: (BufferedSource) -> T?,
    ): T? {
        try {
            onStatus("Uploading image (${size / 1024} KB)…")

            val request = Request.Builder()
                .url("http://$host:$port/upscale")
                .post(body)
                .build()

            return client.newCall(request).execute().use { response ->
                onStatus("Processing on server…")

                if (!response.isSuccessful) {
                    Log.e(TAG, "Remote upscaler returned HTTP ${response.code}")
                    onStatus("Server error: HTTP ${response.code}")
                    return null
                }

                onStatus("Downloading result…")

                // Read straight from the socket — a bitmap decode keeps no byte[] copy of the image
                val result = read(response.body.source())
                if (result != null) onStatus("Enhancement complete")
                result
            }
        } catch (e: ConnectException) {
            Log.e(TAG, "Cannot connect to remote upscaler at $host:$port — is the server running?")
            onStatus("Connection failed — is the server running?")
            return null
        } catch (e: SocketTimeoutException) {
            Log.e(TAG, "Remote upscaler timed out for $host:$port")
            onStatus("Server timed out")
            return null
        } catch (e: Exception) {
            Log.e(TAG, "Remote upscaler failed: ${e.message}", e)
            onStatus("Remote enhancement failed")
            return null
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
    ): Bitmap? = uploadUrl(imageUrl, host, port, sourceHeaders, onStatus, ::decodeBitmap)

    /** Like [processUrl], but returns the server's encoded output untouched (see [processRaw]). */
    suspend fun processUrlRaw(
        imageUrl: String,
        host: String,
        port: Int,
        sourceHeaders: Map<String, String> = emptyMap(),
        onStatus: suspend (String) -> Unit = {},
    ): ByteArray? = uploadUrl(imageUrl, host, port, sourceHeaders, onStatus, ::readNonEmpty)

    private suspend fun <T : Any> uploadUrl(
        imageUrl: String,
        host: String,
        port: Int,
        sourceHeaders: Map<String, String>,
        onStatus: suspend (String) -> Unit,
        read: (BufferedSource) -> T?,
    ): T? = withContext(Dispatchers.IO) {
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
                val result = read(response.body.source())
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
     * Collects every result in memory — use [streamBatch] to handle each one as it lands.
     *
     * @param images Raw encoded image bytes (PNG/JPEG) — sent base64-encoded
     */
    suspend fun processBatch(
        images: List<ByteArray>,
        host: String,
        port: Int,
        onStatus: suspend (String) -> Unit = {},
    ): List<ByteArray?> {
        val out = arrayOfNulls<ByteArray>(images.size)
        streamBatch(images, host, port, onStatus) { index, image -> out[index] = image }
        return out.toList()
    }

    /**
     * Ask the server to download several images by URL and upscale them in one request.
     *
     * Corresponds to `POST /upscale/batch/url` (JSON `{urls:[…]}`). The server downloads
     * in parallel. The returned list is aligned to the input order; failures are null.
     * Collects every result in memory — use [streamBatchUrl] to handle each one as it lands.
     */
    suspend fun processBatchUrl(
        urls: List<String>,
        host: String,
        port: Int,
        sourceHeaders: Map<String, String> = emptyMap(),
        onStatus: suspend (String) -> Unit = {},
    ): List<ByteArray?> {
        val out = arrayOfNulls<ByteArray>(urls.size)
        streamBatchUrl(urls, host, port, sourceHeaders, onStatus) { index, image -> out[index] = image }
        return out.toList()
    }

    /**
     * Streaming form of [processBatch]: [onResult] runs for each image as soon as the server
     * finishes it, in completion order, with the request index and the server's output bytes
     * (null when that item failed).
     *
     * [onResult] is called exactly once per index — indices the server never answered (HTTP
     * error, dropped connection, unparseable body) are reported as null at the end — unless
     * the calling coroutine is cancelled. Cancelling closes the connection, which makes the
     * server drop the images it hasn't finished.
     */
    suspend fun streamBatch(
        images: List<ByteArray>,
        host: String,
        port: Int,
        onStatus: suspend (String) -> Unit = {},
        onResult: suspend (index: Int, image: ByteArray?) -> Unit,
    ) {
        if (host.isBlank() || images.isEmpty()) {
            images.indices.forEach { onResult(it, null) }
            return
        }
        onStatus("Uploading batch of ${images.size} image(s)…")
        // An item that can't be converted is sent as-is: the server fails just that index.
        val uploads = images.map { serverDecodableBytes(it, host, port) ?: it }
        runBatch("/upscale/batch", BatchImagesBody(uploads), uploads.size, host, port, onStatus, onResult)
    }

    /** Streaming form of [processBatchUrl]; same [onResult] contract as [streamBatch]. */
    suspend fun streamBatchUrl(
        urls: List<String>,
        host: String,
        port: Int,
        sourceHeaders: Map<String, String> = emptyMap(),
        onStatus: suspend (String) -> Unit = {},
        onResult: suspend (index: Int, image: ByteArray?) -> Unit,
    ) {
        if (host.isBlank() || urls.isEmpty()) {
            urls.indices.forEach { onResult(it, null) }
            return
        }
        onStatus("Requesting server-side download of ${urls.size} image(s)…")
        val arr = JSONArray()
        urls.forEach { arr.put(it) }
        val payload = JSONObject().put("urls", arr).also { obj ->
            if (sourceHeaders.isNotEmpty()) obj.put("headers", JSONObject(sourceHeaders))
        }.toString()
        runBatch("/upscale/batch/url", payload.toRequestBody(jsonMediaType), urls.size, host, port, onStatus, onResult)
    }

    /**
     * POST a batch asking for NDJSON (`Accept: application/x-ndjson`) and feed each result to
     * [onResult] as it is parsed. Servers that predate streaming ignore the header and answer
     * with the single `{"batch_id":…,"results":[…]}` document, which [readBatchResults] reads
     * just the same — only all at once.
     */
    private suspend fun runBatch(
        route: String,
        body: RequestBody,
        count: Int,
        host: String,
        port: Int,
        onStatus: suspend (String) -> Unit,
        onResult: suspend (index: Int, image: ByteArray?) -> Unit,
    ) = withContext(Dispatchers.IO) {
        val delivered = BooleanArray(count)
        val deliver: suspend (Int, ByteArray?) -> Unit = { index, image ->
            if (index in 0 until count && !delivered[index]) {
                delivered[index] = true
                onResult(index, image)
            }
        }
        try {
            val request = Request.Builder()
                .url("http://$host:$port$route")
                .header("Accept", NDJSON)
                .post(body)
                .build()

            batchClient.newCall(request).executeCancellable { response ->
                if (!response.isSuccessful) {
                    Log.e(TAG, "Remote $route returned HTTP ${response.code}")
                    onStatus("Server error: HTTP ${response.code}")
                    return@executeCancellable
                }
                val streamed = response.header("Content-Type")?.startsWith(NDJSON) == true
                onStatus(if (streamed) "Receiving batch results…" else "Received batch result…")
                readBatchResults(response.body.source(), deliver)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Cancelling the call surfaces here as an IOException; that's not a server failure.
            ensureActive()
            Log.e(TAG, "Remote $route failed: ${e.message}", e)
            onStatus("Remote batch enhancement failed")
        }
        for (index in 0 until count) {
            if (!delivered[index]) onResult(index, null)
        }
    }

    /**
     * Run a blocking OkHttp call so that cancelling the coroutine cancels the call. OkHttp's
     * `execute()` ignores coroutine cancellation on its own; cancelling the call closes the
     * socket mid-read, which is also how the server learns to stop work nobody will read.
     */
    private suspend fun Call.executeCancellable(block: suspend (Response) -> Unit) = coroutineScope {
        val call = this@executeCancellable
        val watcher = launch(start = CoroutineStart.UNDISPATCHED) {
            try {
                awaitCancellation()
            } finally {
                call.cancel()
            }
        }
        try {
            call.execute().use { block(it) }
        } finally {
            watcher.cancel()
        }
    }

    /**
     * Parse a batch response body and hand each item to [deliver] as soon as it is read.
     * Bytes are the server's output PNG/WebP — written directly to cache without re-encoding.
     *
     * Accepts both shapes the server answers with:
     * - NDJSON: `{"batch_id":N,"count":n}` then one `{"index":i,"success":…}` object per line;
     * - legacy: `{"batch_id":N,"results":[{"index":i,"success":bool,"image":"b64"|null,"error":"…"}]}`.
     *
     * Pull-parsed (lenient, so consecutive NDJSON lines read as consecutive top-level values)
     * so only one item's base64 string is ever in memory at a time.
     */
    private suspend fun readBatchResults(source: BufferedSource, deliver: suspend (Int, ByteArray?) -> Unit) {
        JsonReader(InputStreamReader(source.inputStream(), Charsets.UTF_8)).use { reader ->
            reader.isLenient = true
            while (reader.peek() != JsonToken.END_DOCUMENT) {
                readBatchObject(reader, null, deliver)
            }
        }
    }

    /**
     * Reads one object, which is either a result item (`{index,success,image,error}`, field
     * order not assumed) or an envelope (the NDJSON header line, or the legacy document whose
     * `results` items are read recursively). [position] is the item's place in a legacy
     * `results` array, used when an item carries no `index`.
     */
    private suspend fun readBatchObject(
        reader: JsonReader,
        position: Int?,
        deliver: suspend (Int, ByteArray?) -> Unit,
    ) {
        var index = position
        var success = false
        var image: ByteArray? = null
        var error: String? = null

        reader.beginObject()
        while (reader.hasNext()) {
            when (reader.nextName()) {
                "results" -> if (reader.peek() == JsonToken.BEGIN_ARRAY) {
                    reader.beginArray()
                    var itemPosition = 0
                    while (reader.hasNext()) {
                        readBatchObject(reader, itemPosition++, deliver)
                    }
                    reader.endArray()
                } else {
                    reader.skipValue()
                }
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

        // Envelopes (no index, not inside `results`) carry nothing to deliver.
        if (index == null) return
        if (success && image != null) {
            deliver(index, image)
        } else {
            Log.w(TAG, "Batch item $index failed: ${error.orEmpty()}")
            deliver(index, null)
        }
    }

    /**
     * Input formats Miko-Kagura's decoder reads when its `/status` doesn't say (older servers):
     * the Rust `image` crate as built there, which can't read AVIF, HEIF or JXL.
     */
    internal val DEFAULT_INPUT_FORMATS = setOf("jpeg", "png", "gif", "webp", "bmp")

    /**
     * `input_formats` from each server's `/status`, keyed by `host:port` and refreshed by every
     * [checkStatus]. A server whose status lacks the field maps to [DEFAULT_INPUT_FORMATS].
     */
    private val inputFormats = ConcurrentHashMap<String, Set<String>>()

    /**
     * [bytes] unchanged when the server can decode them, otherwise the image re-encoded as PNG,
     * or null when it can't be decoded on the device either.
     *
     * Sources do serve formats some servers can't read (AVIF, HEIF, JXL…); the reader shows
     * them through [ImageDecoder], so they are decoded here the same way and uploaded as PNG.
     * What the server reads comes from its `/status` (see [inputFormats]), fetched once here
     * when only it can decide.
     */
    private suspend fun serverDecodableBytes(bytes: ByteArray, host: String, port: Int): ByteArray? {
        val format = sniffFormat(bytes)
        if (format in DEFAULT_INPUT_FORMATS) return bytes
        if (format != null) {
            val key = "$host:$port"
            if (!inputFormats.containsKey(key)) {
                // Unreachable now: assume the default list until a later checkStatus succeeds.
                checkStatus(host, port) ?: inputFormats.putIfAbsent(key, DEFAULT_INPUT_FORMATS)
            }
            if (format in inputFormats[key].orEmpty()) return bytes
        }

        val bitmap = try {
            ImageDecoder.newInstance(ByteArrayInputStream(bytes))?.decode()
                ?: BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        } catch (e: Exception) {
            Log.w(TAG, "Could not decode ${bytes.size}-byte image for upload: ${e.message}")
            null
        } ?: return null
        return try {
            val encoded = Buffer()
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, encoded.outputStream())
            encoded.readByteArray()
        } finally {
            bitmap.recycle()
        }
    }

    /** Whether [bytes] sniff as one of the server's [formats] (see [serverDecodableBytes]). */
    internal fun isServerDecodable(bytes: ByteArray, formats: Set<String> = DEFAULT_INPUT_FORMATS): Boolean =
        sniffFormat(bytes)?.let { it in formats } == true

    /**
     * The image format of [bytes] by magic number, named as in the server's `input_formats`
     * ("jpeg", "png", "gif", "webp", "bmp", "tiff", "avif", "heif", "jxl"), or null if unknown.
     */
    internal fun sniffFormat(bytes: ByteArray): String? {
        fun at(offset: Int, vararg magic: Int) =
            bytes.size >= offset + magic.size && magic.indices.all { bytes[offset + it].toInt() and 0xFF == magic[it] }
        fun ascii(offset: Int, text: String) = at(offset, *IntArray(text.length) { text[it].code })
        return when {
            at(0, 0xFF, 0xD8, 0xFF) -> "jpeg"
            at(0, 0x89, 'P'.code, 'N'.code, 'G'.code) -> "png"
            ascii(0, "GIF8") -> "gif" // GIF87a / GIF89a
            ascii(0, "RIFF") && ascii(8, "WEBP") -> "webp"
            ascii(0, "BM") -> "bmp"
            ascii(0, "II*\u0000") || ascii(0, "MM\u0000*") -> "tiff"
            at(0, 0xFF, 0x0A) || at(0, 0, 0, 0, 0x0C, 'J'.code, 'X'.code, 'L'.code, ' '.code) -> "jxl"
            ascii(4, "ftyp") -> isoBmffImageFormat(bytes)
            else -> null
        }
    }

    /**
     * "avif" or "heif" for an ISO-BMFF `ftyp` box, from its major and compatible brands (AVIF
     * files often carry the generic "mif1" major brand and list "avif" as compatible).
     */
    private fun isoBmffImageFormat(bytes: ByteArray): String? {
        val boxSize = ((bytes[0].toInt() and 0xFF) shl 24) or ((bytes[1].toInt() and 0xFF) shl 16) or
            ((bytes[2].toInt() and 0xFF) shl 8) or (bytes[3].toInt() and 0xFF)
        val end = minOf(if (boxSize >= 16) boxSize else 16, bytes.size)
        // Major brand at 8, minor version at 12, compatible brands from 16.
        val brands = (listOf(8) + (16 until end - 3 step 4))
            .filter { it + 4 <= bytes.size }
            .map { String(bytes, it, 4, Charsets.US_ASCII) }
        return when {
            brands.any { it == "avif" || it == "avis" } -> "avif"
            brands.any { it in HEIF_BRANDS } -> "heif"
            else -> null
        }
    }

    private val HEIF_BRANDS = setOf("heic", "heix", "heim", "heis", "hevc", "hevx", "mif1", "msf1")

    /** The whole response body, or null when the server sent nothing. */
    private fun readNonEmpty(source: BufferedSource): ByteArray? = source.readByteArray().takeIf { it.isNotEmpty() }

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
                parseStatusJson(response.body.string()).also { status ->
                    inputFormats["$host:$port"] = parseInputFormats(status["input_formats"]) ?: DEFAULT_INPUT_FORMATS
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Remote upscaler status check failed: ${e.message}")
            null
        }
    }

    /** `input_formats` from /status as lowercase names, or null when absent or malformed. */
    internal fun parseInputFormats(value: Any?): Set<String>? {
        val list = value as? List<*> ?: return null
        return list.mapNotNull { (it as? String)?.takeIf(String::isNotBlank)?.lowercase() }
            .toSet()
            .takeIf { it.isNotEmpty() }
    }

    /** Parse the flat /status JSON object into a plain map (JSONObject.NULL becomes null, arrays become lists). */
    private fun parseStatusJson(json: String): Map<String, Any?> {
        return try {
            val obj = JSONObject(json)
            buildMap {
                obj.keys().forEach { key ->
                    put(
                        key,
                        when (val value = obj.get(key)) {
                            JSONObject.NULL -> null
                            is JSONArray -> List(value.length()) { value.opt(it).takeIf { v -> v != JSONObject.NULL } }
                            else -> value
                        },
                    )
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse /status response: ${e.message}")
            emptyMap()
        }
    }
}
