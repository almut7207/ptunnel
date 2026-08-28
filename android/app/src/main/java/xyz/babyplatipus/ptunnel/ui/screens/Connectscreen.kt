package xyz.babyplatipus.ptunnel.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import xyz.babyplatipus.ptunnel.data.model.ConnectState
import xyz.babyplatipus.ptunnel.data.model.Credentials
import xyz.babyplatipus.ptunnel.data.model.StageLine
import androidx.compose.material3.TextButton

/**
 * Экран подключения. Пока идёт пайплайн — показывает, что именно
 * происходит; после — состояние соединения и предложение привязать
 * аккаунт в Telegram.
 */
@Composable
fun ConnectScreen(
    state: ConnectState,
    onLinkTelegram: () -> Unit,
    onDismissPrompt: () -> Unit,
    onDisconnect: () -> Unit,
    onOpenMenu: () -> Unit,
    onBack: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(24.dp)
    ) {
        Spacer(Modifier.height(24.dp))

        if (state.offline) {
            Text(
                text = "Нет подключения к интернету. Туннель восстановится автоматически.",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.error
            )
            Spacer(Modifier.height(12.dp))
        }

        Text(
            text = state.tariff?.title ?: "Подключение",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.height(24.dp))

        state.lines.forEach { line ->
            StageRow(line)
            Spacer(Modifier.height(12.dp))
        }

        state.error?.let { err ->
            Spacer(Modifier.height(16.dp))
            Text(
                text = err,
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.error
            )
            Spacer(Modifier.height(16.dp))
            OutlinedButton(onClick = onBack) { Text("Назад к тарифам") }
        }

        if (state.connected) {
            Spacer(Modifier.height(24.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("●", color = Color(0xFF4CAF50), fontSize = 18.sp)
                Spacer(Modifier.width(8.dp))
                Text(
                    "Подключено · ${state.tariff?.title ?: ""}",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF4CAF50)
                )
            }
            Spacer(Modifier.height(12.dp))
            ConnectionSummary(state.credentials)
            Spacer(Modifier.height(24.dp))

            if (state.showTelegramPrompt) {
                TelegramPrompt(onLink = onLinkTelegram, onDismiss = onDismissPrompt)
                Spacer(Modifier.height(16.dp))
            }

            OutlinedButton(
                onClick = onDisconnect,
                modifier = Modifier.fillMaxWidth()
            ) { Text("Отключиться") }
            Spacer(Modifier.height(8.dp))

            TextButton(onClick = onOpenMenu, modifier = Modifier.fillMaxWidth()) {
                Text("Меню")
            }
        }
    }
}

@Composable
private fun StageRow(line: StageLine) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier.size(20.dp),
            contentAlignment = Alignment.Center
        ) {
            when (line.status) {
                StageLine.Status.RUNNING -> CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp
                )
                StageLine.Status.OK -> Text("✓", color = Color(0xFF4CAF50), fontSize = 16.sp)
                StageLine.Status.FAILED -> Text("✕", color = MaterialTheme.colorScheme.error, fontSize = 16.sp)
                StageLine.Status.PENDING -> Text("·", color = Color.Gray, fontSize = 16.sp)
            }
        }
        Spacer(Modifier.size(12.dp))
        Text(
            text = line.label,
            fontSize = 15.sp,
            color = when (line.status) {
                StageLine.Status.PENDING -> MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f)
                else -> MaterialTheme.colorScheme.onBackground
            }
        )
    }
}

@Composable
private fun ConnectionSummary(creds: Credentials?) {
    val detail = when (creds) {
        is Credentials.Awg ->
            "AmneziaWG · ${creds.endpointHost}:${creds.endpointPort}\nадрес ${creds.address}"
        is Credentials.Xray ->
            "VLESS + Reality · ${creds.host}:${creds.port}\nSNI ${creds.sni}"
        null -> ""
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(16.dp)
    ) {
        Text(
            text = "Подключено",
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF4CAF50)
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = detail,
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f)
        )
    }
}

@Composable
private fun TelegramPrompt(onLink: () -> Unit, onDismiss: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
            .padding(16.dp)
    ) {
        Text(
            text = "Подтвердите аккаунт",
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = "Без подтверждения в Telegram доступ действует ограниченное время.",
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.75f)
        )
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = onLink) { Text("Подтвердить") }
            OutlinedButton(onClick = onDismiss) { Text("Позже") }
        }
    }
}
