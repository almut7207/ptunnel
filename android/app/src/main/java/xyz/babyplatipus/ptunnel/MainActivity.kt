package xyz.babyplatipus.ptunnel

import android.content.Intent
import android.net.Uri
import android.net.VpnService
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.launch
import xyz.babyplatipus.ptunnel.ui.MainViewModel
import xyz.babyplatipus.ptunnel.ui.screens.ConnectScreen
import xyz.babyplatipus.ptunnel.ui.screens.PhoneScreen
import xyz.babyplatipus.ptunnel.ui.screens.SplitScreen
import xyz.babyplatipus.ptunnel.ui.screens.TariffScreen
import xyz.babyplatipus.ptunnel.ui.theme.PtunnelTheme
import xyz.babyplatipus.ptunnel.vpn.PtunnelVpnService
import androidx.compose.runtime.LaunchedEffect
import xyz.babyplatipus.ptunnel.ui.screens.BypassSuggestScreen
import xyz.babyplatipus.ptunnel.ui.screens.PaymentDialog
import xyz.babyplatipus.ptunnel.ui.screens.TunnelsScreen
import xyz.babyplatipus.ptunnel.ui.screens.ImportDialog
import xyz.babyplatipus.ptunnel.ui.screens.OfferImportDialog
import xyz.babyplatipus.ptunnel.ui.screens.LoginScreen
import xyz.babyplatipus.ptunnel.ui.screens.MenuScreen
import android.content.Context
import android.content.BroadcastReceiver
import android.content.IntentFilter

class MainActivity : ComponentActivity() {

    private val vm: MainViewModel by viewModels()

    private val vpnPermission = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val granted = result.resultCode == RESULT_OK
        if (granted) startVpnService(pendingConfig, pendingExcluded)
        vm.onVpnPermission(granted)
    }

    private val folderPicker = registerForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            contentResolver.takePersistableUriPermission(
                uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        }
        vm.onFolderPicked(uri)
    }

    private var stopWaiter: (() -> Unit)? = null
    private var pendingConfig: String? = null
    private var pendingExcluded: List<String> = emptyList()

    private val stoppedReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(c: Context?, i: Intent?) {
            stopWaiter?.invoke()
            stopWaiter = null
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        androidx.core.content.ContextCompat.registerReceiver(
            this, stoppedReceiver,
            android.content.IntentFilter(PtunnelVpnService.ACTION_STOPPED),
            androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED
        )

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                vm.events.collect { event ->
                    when (event) {
                        is MainViewModel.Event.RequestVpnPermission -> {
                            pendingConfig = event.configBlob
                            pendingExcluded = event.excluded
                            requestVpn()
                        }
                        is MainViewModel.Event.OpenTelegram -> openTelegram(event.deeplink)
                        is MainViewModel.Event.StopVpnService -> stopVpnService()
                        is MainViewModel.Event.OpenUrl ->
                            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(event.url)))
                        is MainViewModel.Event.PickFolder -> {
                            // Подсказываем папку загрузок Telegram
                            val hint = Uri.parse(
                                "content://com.android.externalstorage.documents/document/" +
                                        "primary%3ADownload%2FTelegram"
                            )
                            folderPicker.launch(hint)
                        }
                    }
                }
            }
        }

        setContent {
            PtunnelTheme {
                var screen by remember { mutableStateOf(Screen.TARIFF) }
                val state by vm.state.collectAsState()
                val needPhone by vm.needPhone.collectAsState()
                val needLogin by vm.needLogin.collectAsState()
                val offerImport by vm.offerImport.collectAsState()
                if (offerImport) {
                    OfferImportDialog(
                        onAccept = { vm.acceptOfferImport() },
                        onDismiss = { vm.dismissOfferImport() }
                    )
                }

                val importState by vm.importState.collectAsState()
                importState?.let {
                    ImportDialog(
                        state = it,
                        onRevoke = { vm.revokeFolderAccess() },
                        onKeep = { vm.dismissImport() }
                    )
                }
                val bypass by vm.bypassSuggestion.collectAsState()
                if (bypass.isNotEmpty()) {
                    BypassSuggestScreen(
                        apps = bypass,
                        onConfirm = { vm.applyBypass(it) },
                        onSkip = { vm.skipBypass() }
                    )
                    return@PtunnelTheme
                }
                val autoConnect by vm.autoConnectReady.collectAsState()
                LaunchedEffect(autoConnect) {
                    if (autoConnect) {
                        vm.consumeAutoConnect()
                        vm.reconnectLast()
                        screen = Screen.CONNECT
                    }
                }
                if (needPhone) {
                    PhoneScreen(onSubmit = { vm.onPhoneEntered(it) })
                    return@PtunnelTheme
                }
                if (needLogin) {
                    val waiting by vm.loginWaiting.collectAsState()
                    val err by vm.loginError.collectAsState()
                    LoginScreen(
                        waiting = waiting,
                        error = err,
                        onLogin = { vm.startLogin() },
                        onUsePhone = { vm.switchToPhone() }
                    )
                    return@PtunnelTheme
                }

                when (screen) {
                    Screen.TARIFF -> {
                        val st by vm.state.collectAsState()
                        val activeId by vm.activeTunnelId.collectAsState()
                        val allTunnels by vm.tunnels.collectAsState()
                        val hasOther = allTunnels.any {
                            it.local && it.id != activeId && it.balanceMinutes != 0
                        }
                        LaunchedEffect(Unit) { vm.loadTunnels() }

                        TariffScreen(
                            connected = st.connected,
                            activeTariff = st.tariff?.title,
                            hasOtherLocal = hasOther,
                            onDisconnect = {
                                stopVpnService()
                                vm.disconnect()
                            },
                            onSelect = {
                                vm.onTariffSelected(it)
                                screen = Screen.CONNECT
                            },
                            onReconnect = {
                                vm.reconnectLast()
                                screen = Screen.CONNECT
                            },
                            onOpenTunnels = { screen = Screen.TUNNELS },
                            onOpenMenu = { screen = Screen.MENU }
                        )
                    }

                    Screen.TUNNELS -> {
                        val tunnels by vm.tunnels.collectAsState()
                        val loading by vm.tunnelsLoading.collectAsState()
                        val tunnelsError by vm.tunnelsError.collectAsState()
                        val activeId by vm.activeTunnelId.collectAsState()
                        LaunchedEffect(Unit) { vm.loadTunnels() }

                        TunnelsScreen(
                            tunnels = tunnels,
                            loading = loading,
                            error = tunnelsError,
                            currentId = activeId,
                            onConnect = {
                                vm.switchTo(it)
                                screen = Screen.CONNECT
                            },
                            onCopyConfig = { id ->
                                lifecycleScope.launch {
                                    vm.configText(id)?.let { text ->
                                        val cm = getSystemService(android.content.ClipboardManager::class.java)
                                        cm.setPrimaryClip(
                                            android.content.ClipData.newPlainText("config", text)
                                        )
                                    }
                                }
                            },
                            onPay = { vm.startPayment(it) },
                            onBack = { screen = Screen.TARIFF }
                        )

                        val payment by vm.payment.collectAsState()
                        payment?.let {
                            PaymentDialog(
                                state = it,
                                onPay = { mean -> vm.pay(mean) },
                                onDismiss = { vm.cancelPayment() }
                            )
                        }
                    }

                    Screen.CONNECT -> ConnectScreen(
                        state = state,
                        onLinkTelegram = { vm.onLinkTelegram() },
                        onDismissPrompt = { vm.dismissTelegramPrompt() },
                        onDisconnect = {
                            stopVpnService()
                            vm.disconnect()
                            screen = Screen.TARIFF
                        },
                        onBack = {
                            vm.clearError()
                            screen = Screen.TARIFF
                        },
                        onOpenMenu = { screen = Screen.MENU }
                    )

                    Screen.SPLIT -> {
                        val apps by vm.apps.collectAsState()
                        val dirty by vm.splitDirty.collectAsState()
                        SplitScreen(
                            apps = apps,
                            dirty = dirty,
                            onToggle = { pkg, excluded -> vm.onSplitToggle(pkg, excluded) },
                            onApply = {
                                vm.applySplitChanges()
                                screen = Screen.CONNECT
                            },
                            onBack = { screen = Screen.TARIFF }
                        )
                    }

                    Screen.MENU -> MenuScreen(
                        linked = false,
                        onTunnels = { screen = Screen.TUNNELS },
                        onSplit = { screen = Screen.SPLIT },
                        onImport = { vm.beginImport() },
                        onLinkTelegram = { vm.onLinkTelegram() },
                        onSupport = { vm.openSupport() },
                        onBack = { screen = Screen.TARIFF }
                    )
                }
            }
        }
    }

    private enum class Screen { TARIFF, CONNECT, SPLIT, TUNNELS, MENU }

    // -----------------------------------------------------------------

    private fun requestVpn() {
        val intent = VpnService.prepare(this)
        if (intent != null) {
            vpnPermission.launch(intent)
        } else {
            // разрешение уже выдано раньше
            startVpnService(pendingConfig, pendingExcluded)
            vm.onVpnPermission(true)
        }
    }

    private fun startVpnService(configBlob: String?, excluded: List<String>) {
        if (configBlob == null) return   // stainless — его поднимает AwgTunnel
        androidx.core.content.ContextCompat.startForegroundService(
            this,
            Intent(this, PtunnelVpnService::class.java)
                .setAction(PtunnelVpnService.ACTION_CONNECT)
                .putExtra(PtunnelVpnService.EXTRA_CONFIG, configBlob)
                .putStringArrayListExtra(
                    PtunnelVpnService.EXTRA_EXCLUDED, ArrayList(excluded)
                )
        )
    }

    private fun stopVpnService() {
        startService(
            Intent(this, PtunnelVpnService::class.java)
                .setAction(PtunnelVpnService.ACTION_DISCONNECT)
        )
    }

    /**
     * Открывает бота. Сначала пробуем нативный tg://, если клиента
     * нет — уходим в браузер по https://t.me/...
     */
    private fun openTelegram(deeplink: String) {
        val native = Intent(Intent.ACTION_VIEW, Uri.parse(deeplink))
        try {
            startActivity(native)
        } catch (e: Exception) {
            val web = deeplink
                .replace("tg://resolve?domain=", "https://t.me/")
                .replace("&start=", "?start=")
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(web)))
        }
    }
    override fun onDestroy() {
        runCatching { unregisterReceiver(stoppedReceiver) }
        super.onDestroy()
    }
}
