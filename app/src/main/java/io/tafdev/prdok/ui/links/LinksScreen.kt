package io.tafdev.prdok.ui.links

import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.tafdev.prdok.R
import io.tafdev.prdok.ui.common.TabTitle
import io.tafdev.prdok.ui.theme.PrdokForAndroidTheme

/** What the Links tab offers. Which URL each one opens is MainScreen's business, not this screen's. */
enum class PortalLink(@StringRes val label: Int) {
    EMPLOYEE_WEB(R.string.link_employee_web),
    CONTACTS(R.string.link_contacts),
    FORUM(R.string.link_forum),
}

/** A monospace title and one underlined link per portal page, as on iOS. */
@Composable
fun LinksScreen(onOpen: (PortalLink) -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .windowInsetsPadding(ScaffoldDefaults.contentWindowInsets)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(32.dp),
    ) {
        TabTitle(stringResource(R.string.links_title), modifier = Modifier.padding(bottom = 16.dp))
        PortalLink.entries.forEach { link ->
            Text(
                text = stringResource(link.label),
                style = MaterialTheme.typography.titleLarge,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.SemiBold,
                textDecoration = TextDecoration.Underline,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(role = Role.Button) { onOpen(link) }
                    // Inside the clickable, so the padding grows the touch target too.
                    .padding(vertical = 8.dp),
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun LinksScreenPreview() {
    PrdokForAndroidTheme {
        Surface { LinksScreen(onOpen = {}) }
    }
}
