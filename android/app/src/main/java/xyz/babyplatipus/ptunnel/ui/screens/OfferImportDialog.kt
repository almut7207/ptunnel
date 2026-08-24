package xyz.babyplatipus.ptunnel.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.sp

@Composable
fun OfferImportDialog(
    onAccept: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Нашли ваши туннели") },
        text = {
            Column {
                Text(
                    "У этого номера уже есть туннели. Чтобы подключаться к ним " +
                            "из приложения, нужен файл конфигурации — он лежит на " +
                            "вашем устройстве, обычно в папке загрузок Telegram.\n\n" +
                            "Мы откроем выбор папки, найдём подходящие файлы и " +
                            "импортируем только ваши. Доступ к папке можно будет " +
                            "сразу отозвать.",
                    fontSize = 14.sp
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onAccept) { Text("Найти конфиги") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Позже") }
        }
    )
}