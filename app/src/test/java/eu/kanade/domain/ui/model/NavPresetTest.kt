package eu.kanade.domain.ui.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * MIKO — unit tests for the navigation bar presets (see `NavPreset.kt`).
 */
class NavPresetTest {

    private val switchCombinations = listOf(
        true to true,
        true to false,
        false to true,
        false to false,
    )

    @Test
    fun `every preset keeps Library, Browse, Favorites and More, with More last`() {
        NavPreset.entries.forEach { preset ->
            switchCombinations.forEach { (showUpdates, showHistory) ->
                val tabs = resolveNavTabs(preset, showUpdates, showHistory)

                assertTrue(NavTabId.LIBRARY in tabs, "$preset lost Library")
                assertTrue(NavTabId.BROWSE in tabs, "$preset lost Browse")
                // C18: Favorites is on every preset, right before More.
                assertTrue(NavTabId.FAVORITES in tabs, "$preset lost Favorites")
                assertEquals(NavTabId.MORE, tabs.last(), "$preset does not end with More")
                assertEquals(NavTabId.FAVORITES, tabs[tabs.size - 2], "$preset moved Favorites away from More")
                assertEquals(1, tabs.count { it == NavTabId.MORE }, "$preset repeats More")
            }
        }
    }

    @Test
    fun `no preset ever repeats a tab`() {
        NavPreset.entries.forEach { preset ->
            switchCombinations.forEach { (showUpdates, showHistory) ->
                val tabs = resolveNavTabs(preset, showUpdates, showHistory)

                assertEquals(tabs.distinct(), tabs, "$preset repeats a tab")
            }
        }
    }

    @Test
    fun `non-custom presets ignore the SY switches`() {
        NavPreset.entries
            .filterNot { it == NavPreset.CUSTOM }
            .forEach { preset ->
                switchCombinations.forEach { (showUpdates, showHistory) ->
                    assertSame(
                        preset.tabs,
                        resolveNavTabs(preset, showUpdates, showHistory),
                        "$preset reacted to the switches",
                    )
                }
            }
    }

    @Test
    fun `mihon preset is the upstream order plus Favorites`() {
        assertEquals(
            listOf(
                NavTabId.LIBRARY,
                NavTabId.UPDATES,
                NavTabId.HISTORY,
                NavTabId.BROWSE,
                NavTabId.FAVORITES,
                NavTabId.MORE,
            ),
            resolveNavTabs(NavPreset.MIHON, showUpdates = false, showHistory = false),
        )
    }

    @Test
    fun `kotatsu preset starts on History`() {
        val tabs = resolveNavTabs(NavPreset.KOTATSU, showUpdates = false, showHistory = false)

        assertEquals(NavTabId.HISTORY, tabs.first())
        assertEquals(
            listOf(
                NavTabId.HISTORY,
                NavTabId.LIBRARY,
                NavTabId.BROWSE,
                NavTabId.UPDATES,
                NavTabId.FAVORITES,
                NavTabId.MORE,
            ),
            tabs,
        )
    }

    @Test
    fun `compact preset drops Updates and History whatever the switches say`() {
        switchCombinations.forEach { (showUpdates, showHistory) ->
            assertEquals(
                listOf(NavTabId.LIBRARY, NavTabId.BROWSE, NavTabId.FAVORITES, NavTabId.MORE),
                resolveNavTabs(NavPreset.COMPACT, showUpdates, showHistory),
            )
        }
    }

    @Test
    fun `custom preset honours both switches`() {
        assertEquals(
            listOf(
                NavTabId.LIBRARY,
                NavTabId.UPDATES,
                NavTabId.HISTORY,
                NavTabId.BROWSE,
                NavTabId.FAVORITES,
                NavTabId.MORE,
            ),
            resolveNavTabs(NavPreset.CUSTOM, showUpdates = true, showHistory = true),
        )
        assertEquals(
            listOf(NavTabId.LIBRARY, NavTabId.HISTORY, NavTabId.BROWSE, NavTabId.FAVORITES, NavTabId.MORE),
            resolveNavTabs(NavPreset.CUSTOM, showUpdates = false, showHistory = true),
        )
        assertEquals(
            listOf(NavTabId.LIBRARY, NavTabId.UPDATES, NavTabId.BROWSE, NavTabId.FAVORITES, NavTabId.MORE),
            resolveNavTabs(NavPreset.CUSTOM, showUpdates = true, showHistory = false),
        )
        assertEquals(
            listOf(NavTabId.LIBRARY, NavTabId.BROWSE, NavTabId.FAVORITES, NavTabId.MORE),
            resolveNavTabs(NavPreset.CUSTOM, showUpdates = false, showHistory = false),
        )
    }

    @Test
    fun `custom preset keeps the Mihon order`() {
        assertEquals(NavPreset.MIHON.tabs, NavPreset.CUSTOM.tabs)
    }
}
