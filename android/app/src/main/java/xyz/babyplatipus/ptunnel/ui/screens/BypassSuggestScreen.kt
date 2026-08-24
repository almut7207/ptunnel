package xyz.babyplatipus.ptunnel.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import xyz.babyplatipus.ptunnel.data.model.AppEntry

/**
 * Показывается один раз после первого подключения.
 * Предлагает пустить российские сервисы мимо туннеля —
 * им обычно не нужен зарубежный IP, а некоторые на нём ломаются.
 */
@Composable
fun BypassSuggestScreen(
    apps: List<AppEntry>,
    onConfirm: (Set<String>) -> Unit,
    onSkip: () -> Unit
) {
    val selected = remember { mutableStateMapOf<String, Boolean>() }
    LaunchedEffect(apps) { apps.forEach { selected[it.packageName] = true } }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(24.dp)
    ) {
        Spacer(Modifier.height(24.dp))
        Text(
            "Российские сервисы",
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "Эти приложения можно пустить мимо туннеля — так они работают " +
                    "быстрее и реже требуют подтверждений. Снимите галочки с тех, " +
                    "которые всё же должны идти через туннель.",
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
        )
        Spacer(Modifier.height(16.dp))

        LazyColumn(modifier = Modifier.weight(1f)) {
            items(apps) { app ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            selected[app.packageName] = selected[app.packageName] != true
                        }
                        .padding(vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = selected[app.packageName] == true,
                        onCheckedChange = { selected[app.packageName] = it }
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(app.label, fontSize = 15.sp)
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        Button(
            onClick = {
                onConfirm(selected.filterValues { it }.keys.toSet())
            },
            modifier = Modifier.fillMaxWidth()
        ) { Text("Применить") }
        Spacer(Modifier.height(8.dp))
        TextButton(onClick = onSkip, modifier = Modifier.fillMaxWidth()) {
            Text("Пропустить")
        }
    }
}