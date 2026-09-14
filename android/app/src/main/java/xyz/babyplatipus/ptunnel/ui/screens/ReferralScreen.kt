package xyz.babyplatipus.ptunnel.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import xyz.babyplatipus.ptunnel.ui.MainViewModel

@Composable
fun ReferralScreen(
    state: MainViewModel.ReferralState?,
    error: String?,
    onShare: (String) -> Unit,
    onCopy: (String) -> Unit,
    onBack: () -> Unit
) {
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
                "Приглашайте друзей",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            TextButton(onClick = onBack) { Text("Назад") }
        }
        Spacer(Modifier.height(12.dp))

        Text(
            "За каждого приглашённого вы получаете до 4 дней доступа, " +
                    "а он — 3 дня бесплатно.",
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
        )
        Spacer(Modifier.height(20.dp))

        if (error != null) {
            Text(error, fontSize = 13.sp, color = MaterialTheme.colorScheme.error)
            return@Column
        }

        if (state == null) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            return@Column
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(MaterialTheme.colorScheme.surface)
                .padding(16.dp)
        ) {
            Text(
                "Ваш код",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
            Spacer(Modifier.height(4.dp))
            Text(state.key, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(12.dp))
            Text(
                "Приглашено: ${state.invited} · начислено дней: ${state.daysEarned}",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f)
            )
        }

        Spacer(Modifier.height(20.dp))
        Button(
            onClick = { onShare(state.botLink) },
            modifier = Modifier.fillMaxWidth()
        ) { Text("Поделиться ссылкой") }

        Spacer(Modifier.height(8.dp))
        OutlinedButton(
            onClick = { onCopy(state.botLink) },
            modifier = Modifier.fillMaxWidth()
        ) { Text("Скопировать ссылку на бота") }

        Spacer(Modifier.height(8.dp))
        OutlinedButton(
            onClick = { onCopy(state.siteLink) },
            modifier = Modifier.fillMaxWidth()
        ) { Text("Скопировать ссылку на сайт") }

        Spacer(Modifier.height(16.dp))
        Text(
            "Ссылка на бота надёжнее: приглашённый сразу попадает в него, " +
                    "и код засчитывается автоматически.",
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.55f)
        )
    }
}