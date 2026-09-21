package io.tafdev.prdok.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItemColors
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.tafdev.prdok.ui.theme.backgroundSecondary

/**
 * A group of rows on one rounded card, with an optional monospace heading above it. The card is
 * the palette's second background, so it stands off the screen's own.
 */
@Composable
fun GroupedSection(
    title: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (title != null) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .semantics { heading() },
            )
        }
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.backgroundSecondary,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(content = content)
        }
    }
}

/** A hairline between rows, inset to the same 16 dp as the row's text on both sides. */
@Composable
fun GroupedSectionDivider() {
    HorizontalDivider(Modifier.padding(horizontal = 16.dp))
}

/** Rows draw no background of their own; the section's rounded card behind them shows through. */
val GroupedRowColors: ListItemColors
    @Composable get() = ListItemDefaults.colors(containerColor = Color.Transparent)
