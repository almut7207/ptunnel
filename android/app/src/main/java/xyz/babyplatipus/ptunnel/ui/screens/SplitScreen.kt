package xyz.babyplatipus.ptunnel.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import xyz.babyplatipus.ptunnel.data.model.AppEntry

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SplitScreen(
    apps: List<AppEntry>,
    onToggle: (String, Boolean) -> Unit,
    dirty: Boolean,
    onApply: () -> Unit,
    onBack: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Раздельные туннели") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Назад")
                    }
                }
            )
        }
    ) { pad ->
        Column(Modifier.padding(pad)) {

            if (dirty) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
                        .padding(12.dp)
                ) {
                    Text(
                        "Изменения вступят в силу после переподключения",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = onApply) {
                        Text("Переподключить")
                    }
                }
            }

            Text(
                "Отмеченные приложения пойдут мимо VPN — напрямую через вашего " +
                        "провайдера. Удобно для банков и российских сервисов.",
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
                            Text(
                                app.packageName,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                            )
                        },
                        trailingContent = {
                            var checked by remember(app.packageName) {
                                mutableStateOf(app.excluded)
                            }
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