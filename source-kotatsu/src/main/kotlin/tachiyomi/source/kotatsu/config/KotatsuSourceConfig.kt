// Ported from Kotatsu-Redo (GPL-3.0): core/prefs/SourceSettings.kt
package tachiyomi.source.kotatsu.config

import android.content.SharedPreferences
import androidx.core.content.edit
import org.koitharu.kotatsu.parsers.config.ConfigKey
import org.koitharu.kotatsu.parsers.config.MangaSourceConfig

/**
 * [MangaSourceConfig] backed by the very same `SharedPreferences` file Mihon uses for per-source
 * settings (`"source_<id>"`, see `ConfigurableSource.preferenceKey`), with the raw [ConfigKey.key]
 * strings as preference keys. That way the settings screen built by the adapter and the parser
 * itself read and write the same values with no synchronisation layer in between.
 *
 * @param defaultUserAgent fallback used when neither the stored value nor the parser's own default
 * carries a user agent; the host's user agent is resolved lazily because it can change at runtime.
 */
class KotatsuSourceConfig(
    private val prefs: SharedPreferences,
    private val defaultUserAgent: () -> String,
) : MangaSourceConfig {

    @Suppress("UNCHECKED_CAST")
    override fun <T> get(key: ConfigKey<T>): T = when (key) {
        is ConfigKey.Domain -> prefs.getString(key.key, null)
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?: key.defaultValue

        is ConfigKey.UserAgent -> prefs.getString(key.key, null)
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?: key.defaultValue.takeIf { it.isNotBlank() }
            ?: defaultUserAgent()

        is ConfigKey.ShowSuspiciousContent -> prefs.getBoolean(key.key, key.defaultValue)
        is ConfigKey.SplitByTranslations -> prefs.getBoolean(key.key, key.defaultValue)
        is ConfigKey.DisableUpdateChecking -> prefs.getBoolean(key.key, key.defaultValue)
        is ConfigKey.InterceptCloudflare -> prefs.getBoolean(key.key, key.defaultValue)

        is ConfigKey.PreferredImageServer -> prefs.getString(key.key, key.defaultValue)
            ?.takeIf { it.isNotEmpty() }
    } as T

    operator fun <T> set(key: ConfigKey<T>, value: T) = prefs.edit {
        when (key) {
            is ConfigKey.Domain -> putString(key.key, (value as String?)?.trim())
            is ConfigKey.UserAgent -> putString(key.key, (value as String?)?.trim())
            is ConfigKey.ShowSuspiciousContent -> putBoolean(key.key, value as Boolean)
            is ConfigKey.SplitByTranslations -> putBoolean(key.key, value as Boolean)
            is ConfigKey.DisableUpdateChecking -> putBoolean(key.key, value as Boolean)
            is ConfigKey.InterceptCloudflare -> putBoolean(key.key, value as Boolean)
            is ConfigKey.PreferredImageServer -> putString(key.key, value as String? ?: "")
        }
    }
}
