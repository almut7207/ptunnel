package xyz.babyplatipus.ptunnel.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Первый запуск: номер нужен, чтобы аккаунт можно было подтвердить в Telegram. */
@Composable
fun PhoneScreen(onSubmit: (String) -> Unit) {
    var raw by remember { mutableStateOf("+7") }
    val digits = raw.filter { it.isDigit() }
    val normalized = if (digits.startsWith("8")) "7" + digits.drop(1) else digits
    val valid = normalized.length in 10..15

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            "Put in a Tunnel",
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.height(12.dp))
        Text(
            "Номер нужен только для того, чтобы вы могли подтвердить аккаунт " +
                    "в Telegram и продлевать доступ.",
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.65f)
        )
        Spacer(Modifier.height(28.dp))

        OutlinedTextField(
            value = raw,
            onValueChange = { raw = it },
            label = { Text("Номер телефона") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(20.dp))

        Button(
            onClick = { onSubmit(normalized) },
            enabled = valid,
            modifier = Modifier.fillMaxWidth()
        ) { Text("Продолжить") }
    }
}