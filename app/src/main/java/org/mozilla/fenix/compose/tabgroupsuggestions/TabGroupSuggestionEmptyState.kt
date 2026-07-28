/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.fenix.compose.tabgroupsuggestions

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.mozilla.fenix.R
import org.mozilla.fenix.compose.annotation.LightDarkPreview
import org.mozilla.fenix.theme.FirefoxTheme

/**
 * State shown inside [org.mozilla.fenix.compose.tabgroupsuggestions.TabGroupSuggestionCard] when
 * no tab group suggestion is currently available, e.g. because the user doesn't have enough open
 * tabs yet, or none of the open tabs are similar enough to suggest grouping.
 *
 * @param modifier [Modifier] used to be applied to the layout.
 */
@Composable
fun TabGroupSuggestionEmptyState(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_folder_new),
            contentDescription = null,
            tint = FirefoxTheme.colors.iconSecondary,
        )

        Text(
            text = stringResource(R.string.tab_group_suggestions_empty_state_title),
            modifier = Modifier.padding(top = 8.dp),
            color = FirefoxTheme.colors.textSecondary,
            style = FirefoxTheme.typography.body2,
        )
    }
}

@Composable
@LightDarkPreview
private fun TabGroupSuggestionEmptyStatePreview() {
    FirefoxTheme {
        Column(
            modifier = Modifier.background(FirefoxTheme.colors.layer2),
        ) {
            TabGroupSuggestionEmptyState()
        }
    }
}

@Composable
@LightDarkPreview
private fun TabGroupSuggestionEmptyStateCompactWidthPreview() {
    FirefoxTheme {
        Column(
            modifier = Modifier
                .background(FirefoxTheme.colors.layer2)
                .width(COMPACT_PREVIEW_WIDTH),
        ) {
            TabGroupSuggestionEmptyState()
        }
    }
}

private val COMPACT_PREVIEW_WIDTH = 200.dp
