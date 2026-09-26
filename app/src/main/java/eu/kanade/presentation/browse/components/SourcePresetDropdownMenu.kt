package eu.kanade.presentation.browse.components

import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
import eu.kanade.presentation.components.DefaultDropdownMenuOffset
import eu.kanade.presentation.components.DropdownMenu
import eu.kanade.presentation.components.RadioMenuItem
import kotlinx.collections.immutable.ImmutableList
import tachiyomi.i18n.miko.MKMR
import tachiyomi.presentation.core.i18n.stringResource

// MIKO -->
/**
 * Toolbar dropdown used to pick the active source preset.
 *
 * Presets are the source categories of [eu.kanade.domain.source.interactor.GetSourceCategories];
 * "All sources" clears the selection, and tapping the preset that is already active toggles it off
 * (same behaviour as the Kotatsu presets popup).
 */
@Composable
fun SourcePresetDropdownMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    presets: ImmutableList<String>,
    activePreset: String,
    onSelectPreset: (String) -> Unit,
    onClickManagePresets: () -> Unit,
    modifier: Modifier = Modifier,
    offset: DpOffset = DefaultDropdownMenuOffset,
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        offset = offset,
    ) {
        RadioMenuItem(
            text = { Text(text = stringResource(MKMR.strings.source_preset_all)) },
            isChecked = activePreset.isEmpty(),
            onClick = {
                onDismissRequest()
                if (activePreset.isNotEmpty()) onSelectPreset("")
            },
        )
        presets.forEach { preset ->
            RadioMenuItem(
                text = {
                    Text(
                        text = preset,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                isChecked = preset == activePreset,
                onClick = {
                    onDismissRequest()
                    onSelectPreset(preset)
                },
            )
        }
        HorizontalDivider()
        DropdownMenuItem(
            text = { Text(text = stringResource(MKMR.strings.source_preset_manage)) },
            onClick = {
                onDismissRequest()
                onClickManagePresets()
            },
        )
    }
}
// MIKO <--
