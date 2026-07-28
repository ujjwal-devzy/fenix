/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.fenix.compose.tabgroupsuggestions

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import org.mozilla.fenix.R
import org.mozilla.fenix.compose.annotation.LightDarkPreview
import org.mozilla.fenix.theme.FirefoxTheme

/**
 * The dismiss ("x") button shown in the corner of
 * [org.mozilla.fenix.compose.tabgroupsuggestions.TabGroupSuggestionCard].
 *
 * Split out into its own composable, rather than inlined into the card, both to keep the card's
 * layout code focused and because the tabs tray binding surfaces the same affordance in a denser
 * layout that reuses this composable directly.
 *
 * @param onClick Invoked when the user taps the button.
 * @param modifier [Modifier] used to be applied to the layout.
 * @param tint [Color] used to tint the button's icon.
 */
@Composable
fun TabGroupSuggestionDismissButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = FirefoxTheme.colors.iconSecondary,
) {
    IconButton(
        onClick = onClick,
        modifier = modifier.size(ICON_BUTTON_SIZE),
    ) {
        Icon(
            painter = painterResource(R.drawable.mozac_ic_close_20),
            contentDescription = stringResource(R.string.tab_group_suggestions_dismiss_content_description),
            tint = tint,
        )
    }
}

private val ICON_BUTTON_SIZE = 32.dp

@Composable
@LightDarkPreview
private fun TabGroupSuggestionDismissButtonPreview() {
    FirefoxTheme {
        Box(
            modifier = Modifier
                .background(FirefoxTheme.colors.layer2)
                .padding(8.dp),
        ) {
            TabGroupSuggestionDismissButton(onClick = {})
        }
    }
}

@Composable
@Preview(name = "Custom tint")
private fun TabGroupSuggestionDismissButtonCustomTintPreview() {
    FirefoxTheme {
        Box(
            modifier = Modifier
                .background(FirefoxTheme.colors.layer2)
                .padding(8.dp),
        ) {
            TabGroupSuggestionDismissButton(onClick = {}, tint = FirefoxTheme.colors.iconPrimary)
        }
    }
}
