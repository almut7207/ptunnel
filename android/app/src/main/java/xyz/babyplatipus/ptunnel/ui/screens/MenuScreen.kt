package xyz.babyplatipus.ptunnel.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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

@Composable
fun MenuScreen(
    linked: Boolean,
    onTunnels: () -> Unit,
    onSplit: () -> Unit,
    onImport: () -> Unit,
    onPasteLink: () -> Unit,
    onLinkTelegram: () -> Unit,
    onSupport: () -> Unit,
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
                "Меню",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            TextButton(onClick = onBack) { Text("Закрыть") }
        }
        Spacer(Modifier.height(20.dp))

        MenuItem("Мои туннели", "Баланс, продление, переключение", onTunnels)
        MenuItem("Раздельные туннели", "Какие приложения идут мимо VPN", onSplit)
        MenuItem("Импорт конфигов", "Перенести туннели, созданные раньше", onImport)
        MenuItem("Вставить ссылку", "vless-ссылка из бота для ARMOR", onPasteLink)
        if (!linked) {
            MenuItem("Подтвердить в Telegram", "Нужно для оплаты и продления", onLinkTelegram)
        }
        MenuItem("Поддержка", "Написать в бот", onSupport)
    }
}

@Composable
private fun MenuItem(title: String, subtitle: String, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable { onClick() }
            .padding(vertical = 14.dp, horizontal = 4.dp)
    ) {
        Text(title, fontSize = 16.sp, color = MaterialTheme.colorScheme.onBackground)
        Spacer(Modifier.height(2.dp))
        Text(
            subtitle,
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.55f)
        )
    }
}