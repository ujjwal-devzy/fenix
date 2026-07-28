/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.fenix.settings

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SwitchPreference
import kotlinx.coroutines.launch
import org.mozilla.fenix.R
import org.mozilla.fenix.components.tabgroupsuggestions.store.TabGroupSuggestionAction
import org.mozilla.fenix.ext.requireComponents
import org.mozilla.fenix.ext.settings
import org.mozilla.fenix.ext.showToolbar

/**
 * Lets the user turn tab group suggestions on or off, and reset any suggestions they have
 * previously dismissed.
 */
class TabGroupSuggestionSettingsFragment : PreferenceFragmentCompat() {

    private lateinit var enabledPreference: SwitchPreference
    private lateinit var resetDismissedPreference: Preference

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.tab_group_suggestions_preferences, rootKey)
    }

    override fun onResume() {
        super.onResume()
        showToolbar(getString(R.string.preferences_tab_group_suggestions))

        setupPreferences()
    }

    private fun setupPreferences() {
        enabledPreference = requirePreference<SwitchPreference>(R.string.pref_key_tab_group_suggestions_enabled).also {
            it.isChecked = requireContext().settings().tabGroupSuggestionsEnabled
            it.onPreferenceChangeListener = Preference.OnPreferenceChangeListener { _, newValue ->
                onEnabledChanged(newValue as Boolean)
                true
            }
        }

        resetDismissedPreference = requirePreference<Preference>(R.string.pref_key_tab_group_suggestions_reset_dismissed).also {
            it.onPreferenceClickListener = Preference.OnPreferenceClickListener {
                onResetDismissedClicked()
                true
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        resetDismissedPreference.isEnabled = requireContext().settings().tabGroupSuggestionsEnabled
    }

    private fun onEnabledChanged(enabled: Boolean) {
        requireContext().settings().tabGroupSuggestionsEnabled = enabled
        resetDismissedPreference.isEnabled = enabled
        requireComponents.tabGroupSuggestionStore.dispatch(TabGroupSuggestionAction.OptOutToggled(enabled))
    }

    private fun onResetDismissedClicked() {
        lifecycleScope.launch {
            requireComponents.tabGroupSuggestionRepository.clearDismissed()

            Toast.makeText(
                requireContext(),
                R.string.tab_group_suggestions_settings_reset_dismissed_confirmation,
                Toast.LENGTH_SHORT,
            ).show()
        }
    }
}
