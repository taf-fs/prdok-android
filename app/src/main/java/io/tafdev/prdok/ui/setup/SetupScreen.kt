package io.tafdev.prdok.ui.setup

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.tafdev.prdok.R

/**
 * First-run landing screen.
 */
@Composable
fun SetupScreen(
    onConnect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
            .padding(top = 128.dp, bottom = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(R.string.setup_welcome),
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(
            text = stringResource(R.string.setup_app_name),
            style = MaterialTheme.typography.headlineLarge,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.SemiBold,
        )

        Spacer(Modifier.weight(1f))

        Image(
            painter = painterResource(R.drawable.cp_logo),
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier.size(150.dp),
        )

        Spacer(Modifier.weight(1f))

        Text(
            text = stringResource(R.string.setup_access_instructions),
            style = MaterialTheme.typography.bodySmall,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        Button(
            onClick = onConnect,
            shape = RoundedCornerShape(16.dp),
            contentPadding = PaddingValues(vertical = 8.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = stringResource(R.string.setup_connect),
                fontWeight = FontWeight.Bold,
            )
        }
        // TODO: "Connect using QR code" joins here later on
    }
}
