package xyz.babyplatipus.ptunnel.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import xyz.babyplatipus.ptunnel.ui.MainViewModel

@Composable
fun PaymentDialog(
    state: MainViewModel.PaymentState,
    onToggle: (String) -> Unit,
    onPay: (String) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                when {
                    state.done -> "Оплата получена"
                    state.waiting -> "Ждём подтверждения"
                    else -> "Продление на 30 дней"
                }
            )
        },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                when {
                    state.done ->
                        Text("Минуты зачислены. Баланс обновится в течение минуты.")

                    state.waiting -> {
                        Text("Завершите оплату в браузере и вернитесь сюда.", fontSize = 14.sp)
                        Spacer(Modifier.height(12.dp))
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }

                    else -> {
                        Text("Снимите галочки с тех, что продлевать не нужно:", fontSize = 13.sp)
                        Spacer(Modifier.height(8.dp))

                        state.options.forEach { t ->
                            val price = if (t.type.contains("ARMOR")) 700 else 350
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onToggle(t.id) }
                                    .padding(vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = t.id in state.selected,
                                    onCheckedChange = { onToggle(t.id) }
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(t.type, fontSize = 13.sp)
                                    Text(
                                        t.id.take(16) + if (t.id.length > 16) "…" else "",
                                        fontSize = 10.sp,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                                    )
                                }
                                Text("$price ₽", fontSize = 13.sp)
                            }
                        }

                        Spacer(Modifier.height(12.dp))
                        HorizontalDivider()
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Итого: ${state.amount} ₽",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold
                        )

                        Spacer(Modifier.height(16.dp))
                        Button(
                            onClick = { onPay("SBP") },
                            enabled = state.selected.isNotEmpty(),
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("СБП") }
                        Spacer(Modifier.height(6.dp))
                        OutlinedButton(
                            onClick = { onPay("CARD") },
                            enabled = state.selected.isNotEmpty(),
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Банковская карта") }
                        Spacer(Modifier.height(6.dp))
                        OutlinedButton(
                            onClick = { onPay("crypto") },
                            enabled = state.selected.isNotEmpty(),
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Криптовалюта") }
                    }
                }

                state.error?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(it, fontSize = 12.sp, color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(if (state.done) "Закрыть" else "Отмена")
            }
        }
    )
}