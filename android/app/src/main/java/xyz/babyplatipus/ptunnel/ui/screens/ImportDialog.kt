package xyz.babyplatipus.ptunnel.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import xyz.babyplatipus.ptunnel.ui.MainViewModel

@Composable
fun ImportDialog(
    state: MainViewModel.ImportState,
    onRevoke: () -> Unit,
    onKeep: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onKeep,
        title = {
            Text(when {
                state.running -> "Ищем конфиги…"
                state.error != null -> "Не получилось"
                else -> "Импорт завершён"
            })
        },
        text = {
            Column {
                when {
                    state.running -> LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    state.error != null -> Text(state.error, fontSize = 13.sp)
                    state.result != null -> {
                        val r = state.result
                        Text(
                            "Просмотрено файлов: ${r.scanned}\n" +
                                    "Импортировано туннелей: ${r.imported}",
                            fontSize = 14.sp
                        )
                        if (r.imported == 0 && r.scanned > 0) {
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "Ни один конфиг не совпал с вашими туннелями. " +
                                        "Возможно, файлы лежат в другой папке — " +
                                        "попробуйте «Загрузки».",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                            )
                        }
                        Spacer(Modifier.height(12.dp))
                        Text(
                            "Отозвать доступ к папке? Приложению он больше не нужен.",
                            fontSize = 13.sp
                        )
                    }
                }
            }
        },
        confirmButton = {
            if (!state.running) {
                TextButton(onClick = onRevoke) { Text("Отозвать доступ") }
            }
        },
        dismissButton = {
            if (!state.running) {
                TextButton(onClick = onKeep) { Text("Оставить") }
            }
        }
    )
}