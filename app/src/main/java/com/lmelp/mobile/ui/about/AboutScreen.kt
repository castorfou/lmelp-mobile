package com.lmelp.mobile.ui.about

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.lmelp.mobile.BuildConfig

data class ChangelogEntry(val hash: String, val message: String, val date: String)

fun parseChangelog(raw: String): List<ChangelogEntry> {
    if (raw.isBlank()) return emptyList()
    return raw.split("\n")
        .filter { it.isNotBlank() }
        .mapNotNull { line ->
            val parts = line.split("|")
            if (parts.size >= 3) ChangelogEntry(parts[0], parts[1], parts[2])
            else null
        }
}

@Composable
fun AboutScreen(modifier: Modifier = Modifier) {
    AboutContent(
        gitCommit = BuildConfig.GIT_COMMIT,
        buildDate = BuildConfig.BUILD_DATE,
        changelog = BuildConfig.CHANGELOG,
        modifier = modifier
    )
}

@Composable
fun AboutContent(
    gitCommit: String,
    buildDate: String,
    changelog: String,
    modifier: Modifier = Modifier
) {
    val entries = remember(changelog) { parseChangelog(changelog) }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Le Masque et la Plume",
                    style = MaterialTheme.typography.headlineMedium
                )
                Text(
                    text = "v. $gitCommit ($buildDate)",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        if (entries.isNotEmpty()) {
            item {
                Text(
                    text = "Historique",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }
            items(entries) { entry ->
                ChangelogRow(entry)
                HorizontalDivider()
            }
        }
    }
}

@Composable
private fun ChangelogRow(entry: ChangelogEntry) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = entry.hash,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.alignByBaseline()
        )
        Text(
            text = entry.message,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier
                .weight(1f)
                .alignByBaseline()
        )
        Text(
            text = entry.date,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.alignByBaseline()
        )
    }
}
