package com.lmelp.mobile.ui.about

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.lmelp.mobile.BuildConfig

@Composable
fun AboutScreen(modifier: Modifier = Modifier) {
    AboutContent(
        gitCommit = BuildConfig.GIT_COMMIT,
        buildDate = BuildConfig.BUILD_DATE,
        modifier = modifier
    )
}

@Composable
fun AboutContent(
    gitCommit: String,
    buildDate: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Le Masque et la Plume",
            style = MaterialTheme.typography.headlineMedium
        )
        Text(
            text = "v. $gitCommit ($buildDate)",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp)
        )
    }
}
