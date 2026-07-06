/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.fenix.compose.tabgroupsuggestions

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Card
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import org.mozilla.fenix.R
import org.mozilla.fenix.components.tabgroupsuggestions.TabGroupSuggestion
import org.mozilla.fenix.compose.annotation.LightDarkPreview
import org.mozilla.fenix.compose.button.PrimaryButton
import org.mozilla.fenix.theme.FirefoxTheme

/**
 * Card shown on the home screen recommending that the user group a set of their currently open
 * tabs together.
 *
 * The card renders one of three states, chosen by the caller rather than inferred internally, so
 * that `org.mozilla.fenix.home.tabgroupsuggestions.TabGroupSuggestionsViewHolder` stays the single
 * source of truth for how store state maps to UI state:
 * - [isLoading]: a refresh is in progress and no previous suggestion is available yet.
 * - [suggestion] is `null`: no suggestion is currently available; renders
 * [TabGroupSuggestionEmptyState].
 * - Otherwise: renders [tabPreviews] as a list of [TabGroupSuggestionRow]s with an action button.
 *
 * @param suggestion The [TabGroupSuggestion] to display, or `null` if none is available.
 * @param tabPreviews UI-friendly previews of the tabs [suggestion] refers to, in the same order as
 * [TabGroupSuggestion.tabIds]. Ignored when [suggestion] is `null`.
 * @param isLoading Whether a suggestion refresh is currently in progress.
 * @param onSuggestionClick Invoked when the user taps the card's action button to accept
 * [suggestion].
 * @param onDismiss Invoked when the user taps the dismiss button to reject [suggestion].
 * @param modifier [Modifier] used to be applied to the layout of the card.
 */
@Suppress("LongMethod")
@Composable
fun TabGroupSuggestionCard(
    suggestion: TabGroupSuggestion?,
    tabPreviews: List<SuggestedTabPreview>,
    isLoading: Boolean,
    onSuggestionClick: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        shape = RoundedCornerShape(8.dp),
        backgroundColor = FirefoxTheme.colors.layer2,
    ) {
        Column(
            modifier = Modifier
                .padding(all = 16.dp)
                .fillMaxWidth(),
        ) {
            TabGroupSuggestionCardHeader(
                onDismiss = onDismiss,
            )

            when {
                isLoading -> TabGroupSuggestionLoadingState()
                suggestion == null -> TabGroupSuggestionEmptyState()
                else -> TabGroupSuggestionCardContent(
                    suggestion = suggestion,
                    tabPreviews = tabPreviews,
                    onSuggestionClick = onSuggestionClick,
                )
            }
        }
    }
}

@Composable
private fun TabGroupSuggestionCardHeader(
    onDismiss: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.tab_group_suggestions_card_title),
            modifier = Modifier.weight(1f),
            color = FirefoxTheme.colors.textPrimary,
            overflow = TextOverflow.Ellipsis,
            maxLines = 1,
            style = FirefoxTheme.typography.headline7,
        )

        TabGroupSuggestionDismissButton(
            onClick = onDismiss,
        )
    }
}

@Composable
private fun TabGroupSuggestionCardContent(
    suggestion: TabGroupSuggestion,
    tabPreviews: List<SuggestedTabPreview>,
    onSuggestionClick: () -> Unit,
) {
    Text(
        text = stringResource(
            R.string.tab_group_suggestions_card_subtitle,
            suggestion.tabCount,
            suggestion.suggestedName,
        ),
        modifier = Modifier.padding(top = 4.dp, bottom = 8.dp),
        color = FirefoxTheme.colors.textSecondary,
        style = FirefoxTheme.typography.body2,
    )

    tabPreviews.take(MAX_TABS_SHOWN).forEach { preview ->
        TabGroupSuggestionRow(preview = preview, onClick = {})
    }

    val remaining = tabPreviews.size - MAX_TABS_SHOWN
    if (remaining > 0) {
        Text(
            text = stringResource(R.string.tab_group_suggestions_card_more_tabs, remaining),
            color = FirefoxTheme.colors.textSecondary,
            style = FirefoxTheme.typography.caption,
        )
    }

    Spacer(modifier = Modifier.height(16.dp))

    PrimaryButton(
        text = stringResource(R.string.tab_group_suggestions_card_action),
        onClick = onSuggestionClick,
    )
}

@Composable
private fun TabGroupSuggestionLoadingState() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 24.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CircularProgressIndicator(
            modifier = Modifier.padding(end = 8.dp),
            color = FirefoxTheme.colors.actionPrimary,
        )

        Text(
            text = stringResource(R.string.tab_group_suggestions_loading_state),
            color = FirefoxTheme.colors.textSecondary,
            style = FirefoxTheme.typography.body2,
        )
    }
}

private const val MAX_TABS_SHOWN = 4

@Composable
@LightDarkPreview
private fun TabGroupSuggestionCardLoadingPreview() {
    FirefoxTheme {
        Column(modifier = Modifier.background(FirefoxTheme.colors.layer1)) {
            TabGroupSuggestionCard(
                suggestion = null,
                tabPreviews = emptyList(),
                isLoading = true,
                onSuggestionClick = {},
                onDismiss = {},
            )
        }
    }
}

@Composable
@LightDarkPreview
private fun TabGroupSuggestionCardEmptyPreview() {
    FirefoxTheme {
        Column(modifier = Modifier.background(FirefoxTheme.colors.layer1)) {
            TabGroupSuggestionCard(
                suggestion = null,
                tabPreviews = emptyList(),
                isLoading = false,
                onSuggestionClick = {},
                onDismiss = {},
            )
        }
    }
}

@Composable
@Preview(name = "With suggestion")
private fun TabGroupSuggestionCardWithSuggestionPreview() {
    val suggestion = TabGroupSuggestion(
        id = "1-2",
        tabIds = listOf("1", "2"),
        suggestedName = "mozilla.org",
        score = 0.82f,
        signals = emptyList(),
        createdAt = 0L,
    )

    FirefoxTheme {
        Column(modifier = Modifier.background(FirefoxTheme.colors.layer1)) {
            TabGroupSuggestionCard(
                suggestion = suggestion,
                tabPreviews = listOf(
                    SuggestedTabPreview(tabId = "1", title = "Mozilla", url = "https://mozilla.org"),
                    SuggestedTabPreview(tabId = "2", title = "Firefox", url = "https://mozilla.org/firefox"),
                ),
                isLoading = false,
                onSuggestionClick = {},
                onDismiss = {},
            )
        }
    }
}
