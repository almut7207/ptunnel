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
                    "У вашего аккаунта уже есть туннели. Чтобы подключаться к ним " +
                            "из приложения, нужна ссылка на конфигурацию — она есть " +
                            "в переписке с ботом.\n\n" +
                            "Откройте бота, найдите сообщение с конфигом, скопируйте " +
                            "ссылку и вернитесь — приложение подхватит её из буфера.",
                    fontSize = 14.sp
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onAccept) { Text("Вставить ссылку") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Позже") }
        }
    )
}