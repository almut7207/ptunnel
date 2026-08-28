package xyz.babyplatipus.ptunnel.ui.screens
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import xyz.babyplatipus.ptunnel.data.model.TunnelInfo

@Composable
fun TunnelsScreen(
    tunnels: List<TunnelInfo>,
    loading: Boolean,
    error: String?,
    currentId: String?,
    onConnect: (String) -> Unit,
    onCopyConfig: (String) -> Unit,
    onPay: (TunnelInfo) -> Unit,
    onBack: () -> Unit
) {
    error?.let {
        Text(
            it,
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.padding(bottom = 12.dp)
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(24.dp)
    ) {
        Spacer(Modifier.height(24.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Мои туннели",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            TextButton(onClick = onBack) { Text("Назад") }
        }
        Spacer(Modifier.height(12.dp))

        if (loading) {
            error?.let {
                Text(
                    it,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.error
                )
                Spacer(Modifier.height(12.dp))
            }
        }

        if (tunnels.isEmpty() && !loading) {
            Text(
                "Туннелей пока нет",
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
            )
        }

        LazyColumn {
            items(tunnels) { t ->
                TunnelCard(
                    tunnel = t,
                    isCurrent = t.id == currentId,
                    onConnect = { onConnect(t.id) },
                    onCopyConfig = { onCopyConfig(t.id) },
                    onPay = { onPay(t) }
                )
                Spacer(Modifier.height(12.dp))
            }
        }
    }
}

@Composable
private fun TunnelCard(
    tunnel: TunnelInfo,
    isCurrent: Boolean,
    onConnect: () -> Unit,
    onCopyConfig: () -> Unit,
    onPay: () -> Unit
) {
    val days = tunnel.balanceMinutes / 1440
    val hours = (tunnel.balanceMinutes % 1440) / 60

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                tunnel.type,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (isCurrent) {
                Text("активен", fontSize = 12.sp, color = Color(0xFF4CAF50))
            }
        }

        if (tunnel.local) {
            Spacer(Modifier.height(2.dp))
            Text(
                "на этом устройстве",
                fontSize = 11.sp,
                color = Color(0xFF4CAF50)
            )
        }

        Spacer(Modifier.height(4.dp))
        Text(
            tunnel.id.take(20) + if (tunnel.id.length > 20) "…" else "",
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
        )

        Spacer(Modifier.height(8.dp))
        Text(
            when {
                tunnel.balanceMinutes < 0 -> "Баланс неизвестен"
                tunnel.balanceMinutes > 0 -> "Осталось: $days д $hours ч"
                else -> "Баланс исчерпан"
            },
            fontSize = 13.sp,
            color = when {
                tunnel.balanceMinutes < 0 ->
                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                tunnel.balanceMinutes > 0 -> MaterialTheme.colorScheme.onSurface
                else -> MaterialTheme.colorScheme.error
            }
        )

        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (tunnel.local && !isCurrent
                && tunnel.tariff != "light"
                && tunnel.balanceMinutes >= 0) {
                Button(onClick = onConnect) { Text("Подключить", fontSize = 12.sp) }
            }
            if (tunnel.local) {
                OutlinedButton(onClick = onCopyConfig) { Text("Конфиг", fontSize = 12.sp) }
            }
            if (tunnel.balanceMinutes >= 0) {
                OutlinedButton(onClick = onPay) { Text("Продлить", fontSize = 12.sp) }
            }
        }

        if (!tunnel.local) {
            Spacer(Modifier.height(6.dp))
            Text(
                "Создан на другом устройстве — подключиться отсюда нельзя",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
            )
        }
    }
}