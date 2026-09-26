package eu.kanade.domain.recommendation

import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode

/**
 * MIKO — C14. The settings object is the only thing persisted for the suggestion systems, so the
 * invariants [RecommendationSettings.normalized] promises (full order, non-empty enabled subset,
 * tolerant JSON) are what protect the engine and the dialog from a bad or old value.
 */
@Execution(ExecutionMode.CONCURRENT)
class RecommendationSettingsTest {

    @Test
    fun `default is single Osusume with every provider ordered and only Osusume enabled`() {
        val s = RecommendationSettings.default
        s.mode shouldBe RecommendationMode.SINGLE
        s.single shouldBe RecommendationProviderId.OSUSUME
        s.order shouldContainExactly RecommendationProviderId.entries.toList()
        s.enabled shouldBe setOf(RecommendationProviderId.OSUSUME)
        s.activeProviders shouldContainExactly listOf(RecommendationProviderId.OSUSUME)
        s.zokuhenThreshold shouldBe RecommendationSettings.DEFAULT_ZOKUHEN_THRESHOLD
    }

    @Test
    fun `multi mode runs the enabled providers in priority order`() {
        val s = RecommendationSettings(
            mode = RecommendationMode.MULTI,
            order = listOf(
                RecommendationProviderId.UWASA,
                RecommendationProviderId.TRACKER,
                RecommendationProviderId.OSUSUME,
                RecommendationProviderId.TAGU_OSEKKAI,
            ),
            enabled = setOf(RecommendationProviderId.OSUSUME, RecommendationProviderId.UWASA),
        )
        s.activeProviders shouldContainExactly listOf(RecommendationProviderId.UWASA, RecommendationProviderId.OSUSUME)
    }

    @Test
    fun `json round trip keeps mode, single, order and enabled`() {
        val s = RecommendationSettings(
            mode = RecommendationMode.MULTI,
            single = RecommendationProviderId.TRACKER,
            order = listOf(
                RecommendationProviderId.TAGU_OSEKKAI,
                RecommendationProviderId.UWASA,
                RecommendationProviderId.ZOKUHEN,
                RecommendationProviderId.OSUSUME,
                RecommendationProviderId.TRACKER,
            ),
            enabled = setOf(RecommendationProviderId.TAGU_OSEKKAI, RecommendationProviderId.TRACKER),
            zokuhenThreshold = 75,
        )
        RecommendationSettings.fromJson(s.toJson()) shouldBe s
    }

    @Test
    fun `zokuhen threshold is clamped to its range and old json without it gets the default`() {
        RecommendationSettings(zokuhenThreshold = 30).normalized().zokuhenThreshold shouldBe 50
        RecommendationSettings(zokuhenThreshold = 150).normalized().zokuhenThreshold shouldBe 100
        // A value written before C19's slider existed has no zokuhenThreshold field at all.
        RecommendationSettings.fromJson(
            """{"mode":"SINGLE","single":"osusume","order":["osusume"],"enabled":["osusume"]}""",
        ).zokuhenThreshold shouldBe RecommendationSettings.DEFAULT_ZOKUHEN_THRESHOLD
    }

    @Test
    fun `normalized completes a partial order and drops duplicates`() {
        val s = RecommendationSettings(
            order = listOf(RecommendationProviderId.TRACKER, RecommendationProviderId.TRACKER),
            enabled = setOf(RecommendationProviderId.TRACKER),
        ).normalized()
        s.order shouldContainExactly listOf(
            RecommendationProviderId.TRACKER,
            RecommendationProviderId.OSUSUME,
            RecommendationProviderId.UWASA,
            RecommendationProviderId.ZOKUHEN,
            RecommendationProviderId.TAGU_OSEKKAI,
        )
        s.enabled shouldBe setOf(RecommendationProviderId.TRACKER)
    }

    @Test
    fun `empty enabled set falls back to the default so multi mode never runs nothing`() {
        val s = RecommendationSettings(mode = RecommendationMode.MULTI, enabled = emptySet()).normalized()
        s.enabled shouldBe RecommendationSettings.DEFAULT_ENABLED
        s.activeProviders shouldContainExactly listOf(RecommendationProviderId.OSUSUME)
    }

    @Test
    fun `garbage, blank and unknown-key json fall back to the default`() {
        RecommendationSettings.fromJson(null) shouldBe RecommendationSettings.default
        RecommendationSettings.fromJson("") shouldBe RecommendationSettings.default
        RecommendationSettings.fromJson("{not json") shouldBe RecommendationSettings.default
        RecommendationSettings.fromJson("""{"mode":"SINGLE","single":"osusume","future":1}""") shouldBe
            RecommendationSettings.default
    }

    @Test
    fun `a provider this build does not know is trimmed, not the whole config`() {
        val raw = """{"mode":"MULTI","single":"uwasa","order":["tracker","future_mode","uwasa"],"enabled":["tracker","future_mode"]}"""
        val s = RecommendationSettings.fromJson(raw)
        s.mode shouldBe RecommendationMode.MULTI
        s.single shouldBe RecommendationProviderId.UWASA
        s.order shouldContainExactly listOf(
            RecommendationProviderId.TRACKER,
            RecommendationProviderId.UWASA,
            RecommendationProviderId.OSUSUME,
            RecommendationProviderId.ZOKUHEN,
            RecommendationProviderId.TAGU_OSEKKAI,
        )
        s.enabled shouldBe setOf(RecommendationProviderId.TRACKER)
    }

    @Test
    fun `json uses the stable provider keys, not enum names`() {
        RecommendationSettings.default.toJson() shouldBe
            """{"mode":"SINGLE","single":"osusume","order":["osusume","uwasa","zokuhen","tagu_osekkai","tracker"],"enabled":["osusume"],"zokuhenThreshold":60}"""
    }
}
