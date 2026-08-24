package xyz.babyplatipus.ptunnel.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import xyz.babyplatipus.ptunnel.ui.MainViewModel

@Composable
fun PaymentDialog(
    state: MainViewModel.PaymentState,
    onPay: (String) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (state.done) "Оплата получена" else "Продление на 30 дней") },
        text = {
            Column {
                when {
                    state.done -> Text("Минуты зачислены. Баланс обновится в течение минуты.")
                    state.waiting -> {
                        Text("Ждём подтверждения оплаты…", fontSize = 14.sp)
                        Spacer(Modifier.height(12.dp))
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }
                    else -> Text(state.tunnel.type, fontSize = 14.sp)
                }
                state.error?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(it, fontSize = 12.sp, color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            if (!state.done && !state.waiting) {
                Column {
                    Button(
                        onClick = { onPay("SBP") },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("СБП") }
                    Spacer(Modifier.height(6.dp))
                    OutlinedButton(
                        onClick = { onPay("CARD") },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Банковская карта") }
                    Spacer(Modifier.height(6.dp))
                    OutlinedButton(
                        onClick = { onPay("crypto") },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Криптовалюта") }
                }
            } else {
                TextButton(onClick = onDismiss) { Text("Закрыть") }
            }
        },
        dismissButton = {
            if (!state.done && !state.waiting) {
                TextButton(onClick = onDismiss) { Text("Отмена") }
            }
        }
    )
}