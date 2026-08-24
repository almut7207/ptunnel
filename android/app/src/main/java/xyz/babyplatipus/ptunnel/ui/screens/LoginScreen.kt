package xyz.babyplatipus.ptunnel.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun LoginScreen(
    waiting: Boolean,
    error: String?,
    onLogin: () -> Unit,
    onUsePhone: () -> Unit
) {
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
            "Вход через Telegram — одно нажатие. Ни номер, ни почта не нужны.",
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.65f)
        )
        Spacer(Modifier.height(28.dp))

        if (waiting) {
            CircularProgressIndicator()
            Spacer(Modifier.height(16.dp))
            Text(
                "Ждём подтверждения в Telegram…",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
            )
            Spacer(Modifier.height(16.dp))
            TextButton(onClick = onLogin) { Text("Открыть бота ещё раз") }
        } else {
            Button(onClick = onLogin, modifier = Modifier.fillMaxWidth()) {
                Text("Войти через Telegram")
            }
            Spacer(Modifier.height(16.dp))
            Text(
                "Telegram недоступен?",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
            )
            Spacer(Modifier.height(4.dp))
            TextButton(onClick = onUsePhone) {
                Text("Создать туннель по номеру телефона")
            }
        }

        error?.let {
            Spacer(Modifier.height(16.dp))
            Text(it, fontSize = 13.sp, color = MaterialTheme.colorScheme.error)
        }
    }
}