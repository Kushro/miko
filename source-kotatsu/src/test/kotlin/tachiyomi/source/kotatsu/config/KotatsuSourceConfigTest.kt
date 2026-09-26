package tachiyomi.source.kotatsu.config

import android.content.SharedPreferences
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import org.koitharu.kotatsu.parsers.config.ConfigKey

/**
 * Runs on a hand written [SharedPreferences] fake so no Robolectric runtime is needed: the class
 * under test only ever talks to the interface, never to the Android implementation behind it.
 */
@Execution(ExecutionMode.CONCURRENT)
class KotatsuSourceConfigTest {

    private fun config(
        prefs: SharedPreferences = FakeSharedPreferences(),
        defaultUserAgent: String = HOST_USER_AGENT,
    ) = KotatsuSourceConfig(prefs) { defaultUserAgent }

    @Test
    fun `falls back to the parser default when nothing is stored`() {
        val config = config()

        config[ConfigKey.Domain("example.org", "mirror.example.org")] shouldBe "example.org"
        config[ConfigKey.ShowSuspiciousContent(true)] shouldBe true
        config[ConfigKey.SplitByTranslations(false)] shouldBe false
        config[ConfigKey.DisableUpdateChecking()] shouldBe false
        config[ConfigKey.InterceptCloudflare()] shouldBe false
        config[ConfigKey.PreferredImageServer(mapOf("a" to "A"), null)] shouldBe null
    }

    @Test
    fun `reads stored values under the raw config keys`() {
        val prefs = FakeSharedPreferences()
        prefs.values["domain"] = "mirror.example.org"
        prefs.values["show_suspicious"] = true
        prefs.values["split_translations"] = true
        prefs.values["img_server"] = "server-b"
        prefs.values["disable_updates"] = true
        prefs.values["intercept_cloudflare"] = true
        val config = config(prefs)

        config[ConfigKey.Domain("example.org")] shouldBe "mirror.example.org"
        config[ConfigKey.ShowSuspiciousContent(false)] shouldBe true
        config[ConfigKey.SplitByTranslations(false)] shouldBe true
        config[ConfigKey.PreferredImageServer(mapOf("a" to "A"), "server-a")] shouldBe "server-b"
        config[ConfigKey.DisableUpdateChecking()] shouldBe true
        config[ConfigKey.InterceptCloudflare()] shouldBe true
    }

    @Test
    fun `blank stored values do not shadow the default`() {
        val prefs = FakeSharedPreferences()
        prefs.values["domain"] = "   "
        prefs.values["user_agent"] = ""
        prefs.values["img_server"] = ""
        val config = config(prefs)

        config[ConfigKey.Domain("example.org")] shouldBe "example.org"
        config[ConfigKey.UserAgent("Parser/1.0")] shouldBe "Parser/1.0"
        config[ConfigKey.PreferredImageServer(mapOf("a" to "A"), "server-a")] shouldBe null
    }

    @Test
    fun `user agent falls back to the host agent only when the parser has none`() {
        val config = config()

        config[ConfigKey.UserAgent("Parser/1.0")] shouldBe "Parser/1.0"
        config[ConfigKey.UserAgent("")] shouldBe HOST_USER_AGENT
        config[ConfigKey.UserAgent("   ")] shouldBe HOST_USER_AGENT
    }

    @Test
    fun `stored user agent wins over both defaults`() {
        val prefs = FakeSharedPreferences()
        prefs.values["user_agent"] = "  Stored/2.0  "
        val config = config(prefs)

        config[ConfigKey.UserAgent("Parser/1.0")] shouldBe "Stored/2.0"
    }

    @Test
    fun `writes land on the raw config keys and read back`() {
        val prefs = FakeSharedPreferences()
        val config = config(prefs)

        config[ConfigKey.Domain("example.org")] = " mirror.example.org "
        config[ConfigKey.UserAgent("Parser/1.0")] = " Written/3.0 "
        config[ConfigKey.ShowSuspiciousContent(false)] = true
        config[ConfigKey.SplitByTranslations(false)] = true
        config[ConfigKey.PreferredImageServer(mapOf("a" to "A"), null)] = "server-c"
        config[ConfigKey.InterceptCloudflare()] = true
        config[ConfigKey.DisableUpdateChecking()] = true

        prefs.values["domain"] shouldBe "mirror.example.org"
        prefs.values["user_agent"] shouldBe "Written/3.0"
        prefs.values["show_suspicious"] shouldBe true
        prefs.values["split_translations"] shouldBe true
        prefs.values["img_server"] shouldBe "server-c"
        prefs.values["intercept_cloudflare"] shouldBe true
        prefs.values["disable_updates"] shouldBe true

        config[ConfigKey.Domain("example.org")] shouldBe "mirror.example.org"
        config[ConfigKey.UserAgent("Parser/1.0")] shouldBe "Written/3.0"
    }

    @Test
    fun `a null image server is stored as empty and reads back as null`() {
        val prefs = FakeSharedPreferences()
        val config = config(prefs)

        config[ConfigKey.PreferredImageServer(mapOf("a" to "A"), "server-a")] = null

        prefs.values["img_server"] shouldBe ""
        config[ConfigKey.PreferredImageServer(mapOf("a" to "A"), "server-a")] shouldBe null
    }

    private companion object {
        const val HOST_USER_AGENT = "Miko/1.0"
    }
}

private class FakeSharedPreferences : SharedPreferences {

    val values = LinkedHashMap<String, Any?>()

    override fun getAll(): MutableMap<String, *> = LinkedHashMap(values)

    override fun getString(key: String, defValue: String?): String? = values[key] as? String ?: defValue

    @Suppress("UNCHECKED_CAST")
    override fun getStringSet(key: String, defValues: MutableSet<String>?): MutableSet<String>? =
        values[key] as? MutableSet<String> ?: defValues

    override fun getInt(key: String, defValue: Int): Int = values[key] as? Int ?: defValue

    override fun getLong(key: String, defValue: Long): Long = values[key] as? Long ?: defValue

    override fun getFloat(key: String, defValue: Float): Float = values[key] as? Float ?: defValue

    override fun getBoolean(key: String, defValue: Boolean): Boolean = values[key] as? Boolean ?: defValue

    override fun contains(key: String): Boolean = values.containsKey(key)

    override fun edit(): SharedPreferences.Editor = FakeEditor(values)

    override fun registerOnSharedPreferenceChangeListener(
        listener: SharedPreferences.OnSharedPreferenceChangeListener,
    ) = Unit

    override fun unregisterOnSharedPreferenceChangeListener(
        listener: SharedPreferences.OnSharedPreferenceChangeListener,
    ) = Unit
}

private class FakeEditor(private val target: MutableMap<String, Any?>) : SharedPreferences.Editor {

    private val pending = LinkedHashMap<String, Any?>()
    private val removed = LinkedHashSet<String>()
    private var cleared = false

    override fun putString(key: String, value: String?): SharedPreferences.Editor {
        pending[key] = value
        return this
    }

    override fun putStringSet(key: String, values: MutableSet<String>?): SharedPreferences.Editor {
        pending[key] = values
        return this
    }

    override fun putInt(key: String, value: Int): SharedPreferences.Editor {
        pending[key] = value
        return this
    }

    override fun putLong(key: String, value: Long): SharedPreferences.Editor {
        pending[key] = value
        return this
    }

    override fun putFloat(key: String, value: Float): SharedPreferences.Editor {
        pending[key] = value
        return this
    }

    override fun putBoolean(key: String, value: Boolean): SharedPreferences.Editor {
        pending[key] = value
        return this
    }

    override fun remove(key: String): SharedPreferences.Editor {
        removed += key
        return this
    }

    override fun clear(): SharedPreferences.Editor {
        cleared = true
        return this
    }

    override fun commit(): Boolean {
        if (cleared) target.clear()
        removed.forEach(target::remove)
        target.putAll(pending)
        pending.clear()
        removed.clear()
        cleared = false
        return true
    }

    override fun apply() {
        commit()
    }
}
