package io.tafdev.prdok.ui.settings

import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.core.os.LocaleListCompat
import io.tafdev.prdok.R
import java.util.Locale

/**
 * The languages the app ships strings for, plus "follow the phone". Keep in step with
 * res/xml/locales_config.xml, which is the same list for the system's own picker.
 */
enum class AppLanguage(val tag: String?) {
    SYSTEM(null),
    ENGLISH("en"),
    CZECH("cs");

    companion object {
        /** What the app is set to now. An empty list from AppCompat means none was picked. */
        fun current(): AppLanguage {
            val picked = AppCompatDelegate.getApplicationLocales()[0]?.language ?: return SYSTEM
            return entries.firstOrNull { it.tag == picked } ?: SYSTEM
        }
    }
}

/**
 * Switches the app's language. The activity is recreated straight after, so every string on
 * screen comes back in the new language; AppCompat also remembers the choice across launches.
 */
fun AppLanguage.makeCurrent() {
    val locales = tag?.let { LocaleListCompat.forLanguageTags(it) } ?: LocaleListCompat.getEmptyLocaleList()
    AppCompatDelegate.setApplicationLocales(locales)
}

/** Each language is named in itself ("Čeština", "English"), so it is findable whatever the app currently shows. */
@Composable
private fun AppLanguage.label(): String = when (val tag = tag) {
    null -> stringResource(R.string.settings_language_system)
    else -> Locale.forLanguageTag(tag).let { locale ->
        locale.getDisplayLanguage(locale).replaceFirstChar { it.titlecase(locale) }
    }
}

/** The in-app language picker, for Android versions whose system settings have none. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LanguageSheet(
    selected: AppLanguage,
    onSelect: (AppLanguage) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Text(
            text = stringResource(R.string.settings_language),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 24.dp),
        )
        // selectableGroup + Role.RadioButton: a screen reader announces one group of options
        // with "1 of 3" positions, rather than three unrelated buttons.
        Column(
            Modifier
                .padding(vertical = 16.dp)
                .selectableGroup(),
        ) {
            AppLanguage.entries.forEach { language ->
                ListItem(
                    headlineContent = { Text(language.label()) },
                    leadingContent = { RadioButton(selected = language == selected, onClick = null) },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    modifier = Modifier
                        .fillMaxWidth()
                        .selectable(
                            selected = language == selected,
                            role = Role.RadioButton,
                            onClick = { onSelect(language) },
                        )
                        .padding(horizontal = 8.dp),
                )
            }
        }
    }
}
