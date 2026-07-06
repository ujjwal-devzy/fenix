/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.fenix.compose.tabgroupsuggestions

import android.content.res.Configuration
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import mozilla.components.browser.icons.compose.Loader
import mozilla.components.browser.icons.compose.Placeholder
import mozilla.components.browser.icons.compose.WithIcon
import org.mozilla.fenix.components.components
import org.mozilla.fenix.compose.annotation.LightDarkPreview
import org.mozilla.fenix.compose.inComposePreview
import org.mozilla.fenix.theme.FirefoxTheme

/**
 * A lightweight, UI-friendly projection of an open tab included in a
 * `org.mozilla.fenix.components.tabgroupsuggestions.TabGroupSuggestion`.
 *
 * Kept deliberately separate from `mozilla.components.browser.state.state.TabSessionState` so
 * that the compose layer under this package never has to depend on browser engine state types,
 * matching how `org.mozilla.fenix.home.recentbookmarks.RecentBookmark` and
 * `org.mozilla.fenix.home.recenttabs.RecentTab` are modeled elsewhere in this codebase.
 *
 * @property tabId The id of the tab this preview was derived from.
 * @property title The tab's page title, falling back to its url when blank.
 * @property url The tab's url, used both for display and favicon lookup.
 */
data class SuggestedTabPreview(
    val tabId: String,
    val title: String,
    val url: String,
)

/**
 * A single row within [org.mozilla.fenix.compose.tabgroupsuggestions.TabGroupSuggestionCard],
 * showing the favicon, title and url of one tab included in a suggestion.
 *
 * @param preview The [SuggestedTabPreview] to display.
 * @param onClick Invoked when the user taps this row.
 * @param modifier [Modifier] used to be applied to the layout.
 */
@Composable
fun TabGroupSuggestionRow(
    preview: SuggestedTabPreview,
    onClick: (SuggestedTabPreview) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onClick(preview) }
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TabGroupSuggestionFavicon(url = preview.url)

        Spacer(modifier = Modifier.width(12.dp))

        Box(modifier = Modifier.fillMaxWidth()) {
            TabGroupSuggestionRowText(preview = preview)
        }
    }
}

@Composable
private fun TabGroupSuggestionFavicon(url: String) {
    Box(
        modifier = Modifier
            .size(FAVICON_SIZE)
            .clip(RoundedCornerShape(4.dp)),
    ) {
        if (inComposePreview) {
            Box(modifier = Modifier.background(color = FirefoxTheme.colors.layer3))
        } else {
            components.core.icons.Loader(url) {
                Placeholder {
                    Box(modifier = Modifier.background(color = FirefoxTheme.colors.layer3))
                }

                WithIcon { icon ->
                    Image(
                        painter = icon.painter,
                        contentDescription = null,
                        modifier = Modifier.size(FAVICON_SIZE),
                    )
                }
            }
        }
    }
}

@Composable
private fun RowScope.TabGroupSuggestionRowText(preview: SuggestedTabPreview) {
    Box {
        Text(
            text = preview.title.ifBlank { preview.url },
            color = FirefoxTheme.colors.textPrimary,
            style = FirefoxTheme.typography.body2,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private val FAVICON_SIZE = 24.dp

@Composable
@LightDarkPreview
private fun TabGroupSuggestionRowPreview() {
    FirefoxTheme {
        Box(modifier = Modifier.background(FirefoxTheme.colors.layer1)) {
            TabGroupSuggestionRow(
                preview = SuggestedTabPreview(
                    tabId = "1",
                    title = "Example Domain",
                    url = "https://example.com",
                ),
                onClick = {},
            )
        }
    }
}

@Composable
@Preview(name = "No title", uiMode = Configuration.UI_MODE_NIGHT_NO)
private fun TabGroupSuggestionRowNoTitlePreview() {
    FirefoxTheme {
        Box(modifier = Modifier.background(FirefoxTheme.colors.layer1)) {
            TabGroupSuggestionRow(
                preview = SuggestedTabPreview(
                    tabId = "2",
                    title = "",
                    url = "https://mozilla.org",
                ),
                onClick = {},
            )
        }
    }
}

@Composable
@Preview(name = "Long title", uiMode = Configuration.UI_MODE_NIGHT_YES)
private fun TabGroupSuggestionRowLongTitlePreview() {
    FirefoxTheme {
        Box(modifier = Modifier.background(FirefoxTheme.colors.layer1)) {
            TabGroupSuggestionRow(
                preview = SuggestedTabPreview(
                    tabId = "3",
                    title = "A very long page title that should be truncated with an ellipsis instead of wrapping",
                    url = "https://example.com/some/very/long/path",
                ),
                onClick = {},
            )
        }
    }
}
