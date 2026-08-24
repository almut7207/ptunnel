package xyz.babyplatipus.ptunnel.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import xyz.babyplatipus.ptunnel.data.model.AppEntry

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SplitScreen(
    apps: List<AppEntry>,
    onToggle: (String, Boolean) -> Unit,
    onBack: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Раздельный трафик") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, "Назад") }
                }
            )
        }
    ) { pad ->
        Column(Modifier.padding(pad)) {
            Text(
                "Отмеченные приложения пойдут МИМО VPN (напрямую). " +
                "Остальной трафик — через туннель.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                modifier = Modifier.padding(16.dp)
            )
            LazyColumn {
                items(apps, key = { it.packageName }) { app ->
                    ListItem(
                        headlineContent = {
                            Text(app.label, fontWeight = FontWeight.Medium)
                        },
                        supportingContent = {
                            Text(app.packageName,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                        },
                        trailingContent = {
                            var checked by remember(app.packageName) { mutableStateOf(app.excluded) }
                            Switch(
                                checked = checked,
                                onCheckedChange = {
                                    checked = it
                                    onToggle(app.packageName, it)
                                }
                            )
                        }
                    )
                    HorizontalDivider()
                }
            }
        }
    }
}
