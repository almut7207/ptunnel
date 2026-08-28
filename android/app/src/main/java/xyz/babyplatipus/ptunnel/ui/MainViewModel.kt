package xyz.babyplatipus.ptunnel.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import xyz.babyplatipus.ptunnel.data.ConfigParsers
import xyz.babyplatipus.ptunnel.data.Prefs
import xyz.babyplatipus.ptunnel.data.model.ConnectState
import xyz.babyplatipus.ptunnel.data.model.Credentials
import xyz.babyplatipus.ptunnel.data.model.Stage
import xyz.babyplatipus.ptunnel.data.model.StageLine
import xyz.babyplatipus.ptunnel.data.model.Tariff
import xyz.babyplatipus.ptunnel.data.remote.ApiClient
import android.content.pm.PackageManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.withContext
import xyz.babyplatipus.ptunnel.data.DefaultBypass
import xyz.babyplatipus.ptunnel.data.model.AppEntry
import xyz.babyplatipus.ptunnel.vpn.AwgTunnel
import xyz.babyplatipus.ptunnel.vpn.TunnelProbe
import xyz.babyplatipus.ptunnel.data.TunnelStore
import xyz.babyplatipus.ptunnel.data.LocalTunnel
import xyz.babyplatipus.ptunnel.data.model.TunnelInfo
import xyz.babyplatipus.ptunnel.data.ConfigImporter
import kotlinx.coroutines.withTimeoutOrNull
import xyz.babyplatipus.ptunnel.vpn.NetworkWatcher

class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val prefs = Prefs(app)
    private val store = TunnelStore(app)

    private val _state = MutableStateFlow(ConnectState())
    val state: StateFlow<ConnectState> = _state.asStateFlow()
    private val _apps = MutableStateFlow<List<AppEntry>>(emptyList())
    private val _splitDirty = MutableStateFlow(false)
    val splitDirty: StateFlow<Boolean> = _splitDirty.asStateFlow()
    val apps: StateFlow<List<AppEntry>> = _apps.asStateFlow()
    private val _needPhone = MutableStateFlow(false)
    val needPhone: StateFlow<Boolean> = _needPhone.asStateFlow()
    private val _autoConnectReady = MutableStateFlow(false)
    private val _needLogin = MutableStateFlow(false)
    val needLogin: StateFlow<Boolean> = _needLogin.asStateFlow()

    private val _loginWaiting = MutableStateFlow(false)
    val loginWaiting: StateFlow<Boolean> = _loginWaiting.asStateFlow()

    private val _loginError = MutableStateFlow<String?>(null)
    val loginError: StateFlow<String?> = _loginError.asStateFlow()
    val autoConnectReady: StateFlow<Boolean> = _autoConnectReady.asStateFlow()
    private val _bypassSuggestion = MutableStateFlow<List<AppEntry>>(emptyList())
    val bypassSuggestion: StateFlow<List<AppEntry>> = _bypassSuggestion.asStateFlow()
    private val _tunnels = MutableStateFlow<List<TunnelInfo>>(emptyList())
    val tunnels: StateFlow<List<TunnelInfo>> = _tunnels.asStateFlow()

    private val _tunnelsLoading = MutableStateFlow(false)

    private val _tunnelsError = MutableStateFlow<String?>(null)
    val tunnelsError: StateFlow<String?> = _tunnelsError.asStateFlow()
    val tunnelsLoading: StateFlow<Boolean> = _tunnelsLoading.asStateFlow()

    private val _importState = MutableStateFlow<ImportState?>(null)
    val importState: StateFlow<ImportState?> = _importState.asStateFlow()

    private val _offerImport = MutableStateFlow(false)
    val offerImport: StateFlow<Boolean> = _offerImport.asStateFlow()

    private val _activeTunnelId = MutableStateFlow<String?>(null)
    val activeTunnelId: StateFlow<String?> = _activeTunnelId.asStateFlow()

    data class ImportState(
        val running: Boolean = false,
        val result: ConfigImporter.Result? = null,
        val treeUri: String? = null,
        val error: String? = null
    )

    fun beginImport() {
        _importState.value = ImportState()
        viewModelScope.launch { _events.send(Event.PickFolder) }
    }

    private fun idOf(creds: Credentials?): String? = when (creds) {
        is Credentials.Awg -> creds.address.substringBefore("/")
        is Credentials.Xray -> creds.uuid
        else -> null
    }

    /**
     * Туннель мог быть снесён биллингом при нулевом балансе:
     * awg-пир и xray-uuid удаляются с нод и из базы.
     * Проверяем по списку с сервера.
     */
    private suspend fun tunnelStillExists(id: String): Boolean {
        val username = prefs.username() ?: return true
        return runCatching {
            ApiClient.userTunnels(username).any { o ->
                val type = o.optString("type")
                val remoteId = if (type.contains("ARMOR")) {
                    o.optString("uuid").ifBlank { o.optString("ip") }
                } else {
                    o.optString("ip")
                }.trim().substringBefore("/")
                remoteId == id
            }
        }.getOrDefault(true)
    }

    /** Локальная зачистка мёртвого туннеля. */
    private suspend fun dropLocalTunnel(id: String) {
        store.remove(id)
        if (prefs.credentials() != null) {
            val cur = ConfigParsers.deserialize(prefs.credentials())
            val curId = when (cur) {
                is Credentials.Awg -> cur.address.substringBefore("/")
                is Credentials.Xray -> cur.uuid
                else -> null
            }
            if (curId == id) prefs.clearCredentials()
        }
        pendingCredentials = null
    }

    fun onFolderPicked(uri: android.net.Uri?) {
        if (uri == null) {
            _importState.value = null
            return
        }
        viewModelScope.launch {
            _importState.value = ImportState(running = true, treeUri = uri.toString())
            try {
                val username = prefs.username() ?: throw IllegalStateException("нет аккаунта")
                val remote = ApiClient.userTunnels(username)
                val ids = remote.mapNotNull { o ->
                    o.optString("full_key").takeIf { it.isNotBlank() }
                        ?: o.optString("ip").takeIf { it.isNotBlank() }
                }.toSet()

                val res = ConfigImporter.scanFolder(getApplication(), uri, ids, store)
                _importState.value = ImportState(result = res, treeUri = uri.toString())
                loadTunnels()
            } catch (e: Exception) {
                _importState.value = ImportState(error = e.message ?: "ошибка импорта")
            }
        }
    }

    fun importFromClipboard(text: String) {
        viewModelScope.launch {
            try {
                val username = prefs.username() ?: return@launch
                val ids = ApiClient.userTunnels(username).mapNotNull {
                    it.optString("full_key").takeIf { s -> s.isNotBlank() }
                }.toSet()
                val ok = ConfigImporter.importVless(text, ids, store)
                _importState.value = ImportState(
                    result = ConfigImporter.Result(if (ok) 1 else 0, 1, emptyList())
                )
                if (ok) loadTunnels()
            } catch (e: Exception) {
                _importState.value = ImportState(error = e.message)
            }
        }
    }

    /** Отозвать доступ к папке после импорта. */
    fun revokeFolderAccess() {
        val uri = _importState.value?.treeUri ?: return
        viewModelScope.launch {
            runCatching {
                getApplication<Application>().contentResolver.releasePersistableUriPermission(
                    android.net.Uri.parse(uri),
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
            _importState.value = null
        }
    }

    fun dismissImport() {
        _importState.value = null
    }

    fun onPhoneEntered(phone: String) {
        viewModelScope.launch {
            prefs.savePhone(phone)
            _needPhone.value = false
            // Проверяем, есть ли у номера туннели, созданные раньше
            if (!prefs.importOffered()) {
                val has = runCatching {
                    ApiClient.userTunnels("phone_$phone").isNotEmpty()
                }.getOrDefault(false)
                if (has) _offerImport.value = true else prefs.markImportOffered()
            }
        }
    }

    /**
     * Гасит ядро, которое сейчас мешает.
     * @param keepSingbox true, если следом поднимаем armor — тогда процесс
     *        :singbox не убиваем, он переиспользует ядро через reload.
     *        Иначе интент ACTION_CONNECT прилетает в умирающий процесс и теряется.
     */
    private suspend fun stopAllTunnels(keepSingbox: Boolean = false) {
        withContext(Dispatchers.IO) {
            runCatching { AwgTunnel.stopAndWait(getApplication()) }
        }
        if (!keepSingbox) {
            _events.send(Event.StopVpnService)
            delay(1200)
        }
    }

    fun dismissOfferImport() {
        viewModelScope.launch {
            prefs.markImportOffered()
            _offerImport.value = false
        }
    }

    fun acceptOfferImport() {
        viewModelScope.launch {
            prefs.markImportOffered()
            _offerImport.value = false
            beginImport()
        }
    }

    fun openSupport() {
        viewModelScope.launch {
            _events.send(Event.OpenTelegram("tg://resolve?domain=PutInATunnel_bot"))
        }
    }

    fun loadTunnels() {
        android.util.Log.d("ptunnel", "loadTunnels()")
        _tunnelsLoading.value = true
        viewModelScope.launch {
            val local = store.all()
            try {
                val username = prefs.username()
                if (username.isNullOrBlank()) {
                    _tunnels.value = emptyList()
                    _tunnelsError.value = "Нужно войти в аккаунт"
                    return@launch
                }
                android.util.Log.d("ptunnel", "запрос туннелей для username=$username")

                val remote = withTimeoutOrNull(10_000) {
                    ApiClient.userTunnels(username)
                }

                if (remote == null) {
                    // Сервер не ответил — показываем то, что есть локально
                    _tunnels.value = local.map { t -> t.toInfo() }
                    _tunnelsError.value =
                        "Сервер недоступен. Показаны туннели этого устройства, " +
                                "остальные и балансы появятся позже."
                    return@launch
                }

                val localById = local.associateBy { it.id }
                android.util.Log.d("ptunnel", "local: ${localById.keys}")

                val fromRemote = remote.mapNotNull { o ->
                    val type = o.optString("type")
                    // armor опознаётся по uuid, wg/awg — по адресу.
                    // full_key для stainless — это публичный ключ, не идентификатор
                    val id = if (type.contains("ARMOR")) {
                        o.optString("uuid").ifBlank { o.optString("ip") }
                    } else {
                        o.optString("ip")
                    }.trim().substringBefore("/")

                    android.util.Log.d("ptunnel", "remote: type=$type id=$id")
                    if (id.isBlank()) return@mapNotNull null

                    val match = localById[id]
                    TunnelInfo(
                        id = id,
                        type = type,
                        balanceMinutes = o.optInt("balance"),
                        active = o.optBoolean("active"),
                        local = match != null,
                        tariff = match?.tariff
                    )
                }

                // Локальные конфиги, которых сервер не вернул: туннель мог быть
                // снесён биллингом, но креды на устройстве остались
                val remoteIds = fromRemote.map { it.id }.toSet()
                val orphans = local.filterNot { it.id in remoteIds }.map { t -> t.toInfo() }

                _tunnels.value = fromRemote + orphans
                _tunnelsError.value = null
            } catch (e: Exception) {
                _tunnels.value = local.map { t -> t.toInfo() }
                _tunnelsError.value = e.message ?: "не удалось загрузить список"
            } finally {
                _tunnelsLoading.value = false
            }
        }
    }

    /** Локальный конфиг без данных с сервера. */
    private fun LocalTunnel.toInfo() = TunnelInfo(
        id = id,
        type = when (tariff) {
            "armor" -> "ARMOR (xray)"
            "light" -> "LIGHT (WG)"
            else -> "STAINLESS (AWG)"
        },
        balanceMinutes = -1,
        active = false,
        local = true,
        tariff = tariff
    )

    /** Поднят ли VPN прямо сейчас — переживает пересоздание ViewModel. */
    private fun vpnIsUp(): Boolean {
        val cm = getApplication<Application>()
            .getSystemService(android.net.ConnectivityManager::class.java)
        return cm.allNetworks.any { n ->
            cm.getNetworkCapabilities(n)
                ?.hasTransport(android.net.NetworkCapabilities.TRANSPORT_VPN) == true
        }
    }

    /** Переключиться на другой локальный туннель. */
    fun switchTo(id: String) {
        viewModelScope.launch {
            if (!hasNetwork()) {
                _state.value = _state.value.copy(
                    error = "Нет подключения к интернету. Проверьте wi-fi или мобильную сеть."
                )
                return@launch
            }
            val t = store.byId(id) ?: return@launch
            val creds = ConfigParsers.deserialize(t.blob) ?: return@launch

            // гасим текущий
            val old = pendingCredentials
            if (old is Credentials.Awg) {
                withContext(Dispatchers.IO) { runCatching { AwgTunnel.stop(getApplication()) } }
            }
            stopAllTunnels(keepSingbox = creds is Credentials.Xray)

            prefs.saveCredentials(t.blob, t.tariff)
            pendingCredentials = creds

            val tariff = if (t.tariff == "armor") Tariff.ARMOR else Tariff.STAINLESS
            _state.value = ConnectState(
                tariff = tariff,
                lines = initialLines().map {
                    if (it.stage == Stage.REQUESTING_API || it.stage == Stage.KEYS_RECEIVED)
                        it.copy(status = StageLine.Status.OK) else it
                },
                credentials = creds
            )
            mark(Stage.CONFIGURING_ENGINE, StageLine.Status.OK)
            mark(Stage.ASKING_PERMISSION, StageLine.Status.RUNNING)
            _events.send(Event.RequestVpnPermission(
                configBlob = if (creds is Credentials.Xray)
                    ConfigParsers.serialize(creds) else null,
                excluded = prefs.splitExcluded().toList()
            ))
        }
    }

    /** Текст конфига — для переноса на роутер. */
    suspend fun configText(id: String): String? {
        val t = store.byId(id) ?: return null
        return when (val c = ConfigParsers.deserialize(t.blob)) {
            is Credentials.Awg -> c.rawConfig
            is Credentials.Xray -> c.rawLink
            else -> null
        }
    }

    private val _payment = MutableStateFlow<PaymentState?>(null)
    val payment: StateFlow<PaymentState?> = _payment.asStateFlow()

    data class PaymentState(
        val tunnel: TunnelInfo,
        val waiting: Boolean = false,
        val done: Boolean = false,
        val error: String? = null
    )

    fun startPayment(tunnel: TunnelInfo) {
        _payment.value = PaymentState(tunnel)
    }

    fun cancelPayment() {
        _payment.value = null
    }

    fun pay(mean: String) {
        val p = _payment.value ?: return
        viewModelScope.launch {
            _payment.value = p.copy(waiting = true, error = null)
            try {
                val username = prefs.username() ?: throw IllegalStateException("нет аккаунта")
                val type = if (p.tunnel.type.contains("ARMOR")) "ARMOR" else "STAINLESS"
                val amount = if (type == "ARMOR") 1000 else 350

                val (url, orderId) = ApiClient.createPayment(
                    username = username,
                    amount = amount,
                    tunnelTypes = listOf(type),
                    selectedIps = listOf(p.tunnel.id),
                    mean = mean
                )
                _events.send(Event.OpenUrl(url))

                if (orderId != null) {
                    val ok = ApiClient.awaitPayment(orderId)
                    _payment.value = _payment.value?.copy(waiting = false, done = ok)
                    if (ok) loadTunnels()
                } else {
                    _payment.value = _payment.value?.copy(waiting = false)
                }
            } catch (e: Exception) {
                _payment.value = _payment.value?.copy(
                    waiting = false,
                    error = e.message ?: "не удалось создать счёт"
                )
            }
        }
    }

    /** События для Activity: запросить VPN-разрешение, открыть Telegram. */
    sealed class Event {
        data class RequestVpnPermission(
            val configBlob: String?,
            val excluded: List<String>
        ) : Event()
        object StopVpnService : Event()
        data class OpenTelegram(val deeplink: String) : Event()
        data class OpenUrl(val url: String) : Event()
        object PickFolder : Event()
    }

    private val _events = Channel<Event>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    private var pendingCredentials: Credentials? = null

    // -----------------------------------------------------------------

    init {
        viewModelScope.launch {
            NetworkWatcher.start(getApplication())
            ApiClient.refreshEndpoints()

            ConfigParsers.deserialize(prefs.credentials())?.let {
                pendingCredentials = it
            }
            loadApps()
            _needLogin.value = prefs.needLogin()

            if (vpnIsUp()) {
                val code = prefs.tariff() ?: "stainless"
                _state.value = _state.value.copy(
                    connected = true,
                    tariff = if (code == "armor") Tariff.ARMOR else Tariff.STAINLESS,
                    credentials = pendingCredentials
                )
                _activeTunnelId.value = idOf(pendingCredentials)
            } else if (prefs.autoConnect()
                && prefs.credentials() != null
                && !prefs.needLogin()
            ) {
                _autoConnectReady.value = true
            }
        }

        // Отдельная корутина: collect не завершается, поэтому её нельзя
        // класть в один блок с инициализацией
            viewModelScope.launch {
            var wasOffline = false
            NetworkWatcher.online.collect { online ->
                _state.value = _state.value.copy(offline = !online)
                if (!online) {
                    wasOffline = true
                } else if (wasOffline) {
                    wasOffline = false
                    if (_state.value.connected || prefs.autoConnect()) {
                        android.util.Log.d("ptunnel", "сеть вернулась, переподключаемся")
                        reconnectLast()
                   }
                }
            }
        }
    }

    private fun initialLines() = listOf(
        StageLine(Stage.REQUESTING_API, "Запрашиваем ключи у сервера", StageLine.Status.PENDING),
        StageLine(Stage.KEYS_RECEIVED, "Ключи получены", StageLine.Status.PENDING),
        StageLine(Stage.CONFIGURING_ENGINE, "Настраиваем движок", StageLine.Status.PENDING),
        StageLine(Stage.ASKING_PERMISSION, "Разрешение на VPN", StageLine.Status.PENDING),
        StageLine(Stage.CONNECTING, "Соединение с сервером", StageLine.Status.PENDING),
        StageLine(Stage.ROUTING_TRAFFIC, "Заворачиваем трафик устройства", StageLine.Status.PENDING),
        StageLine(Stage.VERIFYING, "Проверяем соединение", StageLine.Status.PENDING)
    )

    private fun mark(stage: Stage, status: StageLine.Status) {
        _state.value = _state.value.copy(
            lines = _state.value.lines.map {
                if (it.stage == stage) it.copy(status = status) else it
            }
        )
    }

    /** Переход на ввод номера — если Telegram недоступен. */
    fun switchToPhone() {
        _needPhone.value = true
        _needLogin.value = false
    }

    private fun hasNetwork(): Boolean {
        val cm = getApplication<Application>()
            .getSystemService(android.net.ConnectivityManager::class.java)
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        return caps.hasCapability(
            android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET
        )
    }

    /** Вход через Telegram: генерим код и открываем бота. */
    fun startLogin() {
        viewModelScope.launch {
            val code = java.util.UUID.randomUUID().toString().replace("-", "").take(16)
            _loginWaiting.value = true
            _loginError.value = null
            _events.send(Event.OpenTelegram(
                "tg://resolve?domain=put_in_a_tunnel_bot&start=app$code"
            ))
            pollLogin(code)
        }
    }

    private fun pollLogin(code: String) {
        viewModelScope.launch {
            repeat(100) {
                delay(3000)
                val res = runCatching { ApiClient.checkAppLogin(code) }.getOrNull()
                if (res != null) {
                    prefs.saveLinked(res.first, res.second)
                    _loginWaiting.value = false
                    _needLogin.value = false
                    if (!prefs.importOffered()) {
                        val has = runCatching {
                            ApiClient.userTunnels(res.second).isNotEmpty()
                        }.getOrDefault(false)
                        if (has) _offerImport.value = true else prefs.markImportOffered()
                    }
                    return@launch
                }
            }
            _loginWaiting.value = false
            _loginError.value = "Подтверждение не получено. Попробуйте ещё раз."
        }
    }

    // -----------------------------------------------------------------
    // Пайплайн: нажатие на тариф
    // -----------------------------------------------------------------

    fun onTariffSelected(tariff: Tariff) {
        _state.value = ConnectState(tariff = tariff, lines = initialLines())

        viewModelScope.launch {
            try {
                if (!hasNetwork()) {
                    _state.value = _state.value.copy(
                        error = "Нет подключения к интернету. Проверьте wi-fi или мобильную сеть."
                    )
                    return@launch
                }
                stopAllTunnels(keepSingbox = tariff.code == "armor")
                // 1. Дёргаем api — сервер генерит ключи и кладёт их
                //    в базу под dev_<device_id>
                mark(Stage.REQUESTING_API, StageLine.Status.RUNNING)
                val username = prefs.username() ?: throw IllegalStateException("нет номера")
                val (kind, payload) = ApiClient.requestTunnel(username, tariff.code)
                mark(Stage.REQUESTING_API, StageLine.Status.OK)

                // 2. Ключи приехали обратно
                mark(Stage.KEYS_RECEIVED, StageLine.Status.RUNNING)
                val creds = when (kind) {
                    "awg" -> ConfigParsers.parseAwg(payload)
                    "xray" -> ConfigParsers.parseVless(payload)
                    else -> throw IllegalStateException("неизвестный тип конфига: $kind")
                }
                prefs.saveCredentials(ConfigParsers.serialize(creds), tariff.code)
                val tunnelId = when (creds) {
                    is Credentials.Awg -> creds.address.substringBefore("/")
                    is Credentials.Xray -> creds.uuid
                }
                store.save(LocalTunnel(
                    id = tunnelId,
                    tariff = tariff.code,
                    blob = ConfigParsers.serialize(creds),
                    createdAt = System.currentTimeMillis()
                ))
                pendingCredentials = creds
                _state.value = _state.value.copy(credentials = creds)
                mark(Stage.KEYS_RECEIVED, StageLine.Status.OK)

                // 3. Передаём параметры в движок
                mark(Stage.CONFIGURING_ENGINE, StageLine.Status.RUNNING)
                mark(Stage.CONFIGURING_ENGINE, StageLine.Status.OK)

                // 4. Разрешение на VPN — дальше продолжит Activity
                mark(Stage.ASKING_PERMISSION, StageLine.Status.RUNNING)
                _events.send(Event.RequestVpnPermission(
                    configBlob = if (creds is Credentials.Xray)
                        ConfigParsers.serialize(creds) else null,
                    excluded = prefs.splitExcluded().toList()
                ))

            } catch (e: Exception) {
                val failed = _state.value.lines.firstOrNull {
                    it.status == StageLine.Status.RUNNING
                }?.stage
                failed?.let { mark(it, StageLine.Status.FAILED) }
                _state.value = _state.value.copy(
                    error = e.message ?: "не удалось получить конфигурацию"
                )
            }
        }
    }

    /** Резолвим домен ноды здесь: в :singbox сеть уже за туннелем. */

    /** Activity сообщает результат диалога VpnService.prepare. */
    /** Activity сообщает результат диалога VpnService.prepare. */
    /** Activity сообщает результат диалога VpnService.prepare. */
    fun onVpnPermission(granted: Boolean) {
        if (!granted) {
            mark(Stage.ASKING_PERMISSION, StageLine.Status.FAILED)
            _state.value = _state.value.copy(error = "нет разрешения на VPN")
            return
        }
        mark(Stage.ASKING_PERMISSION, StageLine.Status.OK)

        viewModelScope.launch {
            engineReady.tryReceive()
            val creds = pendingCredentials

            mark(Stage.CONNECTING, StageLine.Status.RUNNING)
            if (creds is Credentials.Awg) {
                withContext(Dispatchers.IO) {
                    AwgTunnel.start(getApplication(), creds.rawConfig, prefs.splitExcluded())
                }
            }
            mark(Stage.CONNECTING, StageLine.Status.OK)

            mark(Stage.ROUTING_TRAFFIC, StageLine.Status.RUNNING)
            var awgUp = false
            if (creds is Credentials.Awg) {
                awgUp = withContext(Dispatchers.IO) {
                    repeat(20) {
                        if (AwgTunnel.isUp()) return@withContext true
                        delay(200)
                    }
                    AwgTunnel.isUp()
                }
                if (!awgUp) {
                    mark(Stage.ROUTING_TRAFFIC, StageLine.Status.FAILED)
                    _state.value = _state.value.copy(error = "туннель не поднялся")
                    return@launch
                }
            }
            mark(Stage.ROUTING_TRAFFIC, StageLine.Status.OK)

            mark(Stage.VERIFYING, StageLine.Status.RUNNING)
            if (creds is Credentials.Xray) {
                withTimeoutOrNull(15_000) { engineReady.receive() }
            }

            val exitIps = runCatching { ApiClient.exitIps() }.getOrDefault(emptySet())
            val probe = TunnelProbe.run(exitIps)

            when (probe) {
                is TunnelProbe.Result.Ok -> {
                    mark(Stage.VERIFYING, StageLine.Status.OK)
                    _state.value = _state.value.copy(connected = true)
                    _activeTunnelId.value = idOf(creds)
                    if (!prefs.isTelegramLinked()) {
                        _state.value = _state.value.copy(showTelegramPrompt = true)
                    }
                    maybeSuggestBypass()
                }

                is TunnelProbe.Result.ServerUnreachable,
                is TunnelProbe.Result.Blocked -> {
                    mark(Stage.VERIFYING, StageLine.Status.FAILED)
                    _activeTunnelId.value = null

                    val id = when (creds) {
                        is Credentials.Awg -> creds.address.substringBefore("/")
                        is Credentials.Xray -> creds.uuid
                        else -> null
                    }

                    val gone = id != null && !tunnelStillExists(id)
                    if (gone) {
                        dropLocalTunnel(id!!)
                        _state.value = _state.value.copy(
                            error = "Этот туннель больше не существует — закончилось " +
                                    "оплаченное время. Создайте новый на экране тарифов."
                        )
                    } else if (probe is TunnelProbe.Result.ServerUnreachable) {
                        _state.value = _state.value.copy(
                            error = "Нет связи с сервером. Проверьте интернет или " +
                                    "попробуйте создать новый туннель."
                        )
                    } else {
                        _state.value = _state.value.copy(
                            error = "Туннель поднялся, но трафик идёт мимо него. " +
                                    (if (creds is Credentials.Awg)
                                        "Попробуйте тариф ARMOR — он устойчивее к блокировкам."
                                    else "Попробуйте пересоздать туннель.")
                        )
                    }
                    stopEverything()
                }

                is TunnelProbe.Result.Failed -> {
                    mark(Stage.VERIFYING, StageLine.Status.FAILED)
                    _activeTunnelId.value = null
                    _state.value = _state.value.copy(error = probe.message)
                    stopEverything()
                }
            }
        }
    }

    fun credentialsForService(): Credentials? = pendingCredentials

    // -----------------------------------------------------------------
    // Привязка Telegram
    // -----------------------------------------------------------------

    fun onLinkTelegram() {
        viewModelScope.launch {
            val phone = prefs.phone() ?: return@launch
            _events.send(Event.OpenTelegram(
                "tg://resolve?domain=put_in_a_tunnel_bot&start=ph$phone"
            ))
            pollLink(phone)
        }
    }

    private suspend fun stopEverything() {
        val creds = pendingCredentials
        if (creds is Credentials.Awg) {
            withContext(Dispatchers.IO) { runCatching { AwgTunnel.stop(getApplication()) } }
        }
        _events.send(Event.StopVpnService)
    }

    /** После открытия бота ждём, пока сервер подтвердит привязку. */
    private fun pollLink(phone: String) {
        viewModelScope.launch {
            repeat(60) {
                delay(3000)
                val res = runCatching { ApiClient.checkPhone(phone) }.getOrNull()
                if (res != null) {
                    prefs.saveLinked(res.first, res.second)
                    _state.value = _state.value.copy(showTelegramPrompt = false)
                    return@launch
                }
            }
        }
    }

    fun dismissTelegramPrompt() {
        _state.value = _state.value.copy(showTelegramPrompt = false)
    }

    private suspend fun loadApps() {
        val excluded = prefs.splitExcluded()
        val app = getApplication<Application>()
        val pm = app.packageManager

        val list = withContext(Dispatchers.IO) {
            val launchable = pm.queryIntentActivities(
                android.content.Intent(android.content.Intent.ACTION_MAIN)
                    .addCategory(android.content.Intent.CATEGORY_LAUNCHER),
                0
            ).map { it.activityInfo.applicationInfo }
                .distinctBy { it.packageName }

            launchable
                .filter { it.packageName != app.packageName }
                .filter {
                    // системные показываем только если пользователь их обновлял
                    val isSystem = (it.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM) != 0
                    val isUpdated = (it.flags and
                            android.content.pm.ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0
                    !isSystem || isUpdated
                }
                .map {
                    AppEntry(
                        packageName = it.packageName,
                        label = pm.getApplicationLabel(it).toString(),
                        excluded = it.packageName in excluded
                    )
                }
                .sortedBy { it.label.lowercase() }
        }
        _apps.value = list
    }

    /** true = приложение идёт МИМО туннеля. */
    fun onSplitToggle(packageName: String, excluded: Boolean) {
        viewModelScope.launch {
            val current = prefs.splitExcluded().toMutableSet()
            if (excluded) current.add(packageName) else current.remove(packageName)
            prefs.setSplitExcluded(current)
            _apps.value = _apps.value.map {
                if (it.packageName == packageName) it.copy(excluded = excluded) else it
            }
            if (_state.value.connected) _splitDirty.value = true
        }
    }

    /** Перезапустить туннель, чтобы новый список исключений вступил в силу. */
    fun applySplitChanges() {
        _splitDirty.value = false
        reconnectLast()
    }

    fun dismissSplitChanges() {
        _splitDirty.value = false
    }

    /** Поднять последний сохранённый туннель без обращения к серверу. */
    /** Поднять последний сохранённый туннель без обращения к серверу. */
    fun reconnectLast() {
        viewModelScope.launch {
            if (!hasNetwork()) {
                _state.value = _state.value.copy(
                    error = "Нет подключения к интернету. Проверьте wi-fi или мобильную сеть."
                )
                return@launch
            }
            val creds = ConfigParsers.deserialize(prefs.credentials())
            if (creds == null) {
                _state.value = _state.value.copy(error = "нет сохранённого туннеля")
                return@launch
            }

            // Проверяем ДО попытки: туннель мог быть снесён биллингом,
            // тогда незачем ждать таймаутов подключения
            val id = when (creds) {
                is Credentials.Awg -> creds.address.substringBefore("/")
                is Credentials.Xray -> creds.uuid
                else -> null
            }
            if (id != null && !tunnelStillExists(id)) {
                dropLocalTunnel(id)
                _state.value = _state.value.copy(
                    error = "Этот туннель удалён — закончилось оплаченное время. " +
                            "Создайте новый на экране тарифов."
                )
                return@launch
            }

            stopAllTunnels(keepSingbox = creds is Credentials.Xray)

            val code = prefs.tariff() ?: "stainless"
            val tariff = if (code == "armor") Tariff.ARMOR else Tariff.STAINLESS

            pendingCredentials = creds
            _state.value = ConnectState(
                tariff = tariff,
                lines = initialLines().map {
                    if (it.stage == Stage.REQUESTING_API || it.stage == Stage.KEYS_RECEIVED)
                        it.copy(status = StageLine.Status.OK) else it
                },
                credentials = creds
            )
            mark(Stage.CONFIGURING_ENGINE, StageLine.Status.OK)
            mark(Stage.ASKING_PERMISSION, StageLine.Status.RUNNING)

            _events.send(Event.RequestVpnPermission(
                configBlob = if (creds is Credentials.Xray)
                    ConfigParsers.serialize(creds) else null,
                excluded = prefs.splitExcluded().toList()
            ))
        }
    }

    /** Есть ли что переподключать — для показа кнопки. */
    suspend fun hasSavedTunnel(): Boolean = prefs.credentials() != null

    fun disconnect() {
        viewModelScope.launch {
            stopAllTunnels()
            _state.value = _state.value.copy(connected = false, lines = emptyList())
            _activeTunnelId.value = null
            _splitDirty.value = false
        }
    }

    val lowBalanceCount: StateFlow<Int> = _tunnels.map { list ->
        list.count { it.minutesLeft in 0..4320 }
    }.stateIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.Eagerly, 0)

    private val engineStarted = kotlinx.coroutines.CompletableDeferred<Unit>()
    private val engineReady = Channel<Unit>(Channel.CONFLATED)

    fun onServiceStarted() {
        engineReady.trySend(Unit)
    }

    fun consumeAutoConnect() {
        _autoConnectReady.value = false
    }

    suspend fun isAutoConnectEnabled(): Boolean = prefs.autoConnect()

    fun setAutoConnect(enabled: Boolean) {
        viewModelScope.launch { prefs.setAutoConnect(enabled) }
    }

    /** Вызывается после первого успешного подключения. */
    private suspend fun maybeSuggestBypass() {
        if (prefs.bypassOffered()) return
        val installed = _apps.value.associateBy { it.packageName }
        val candidates = DefaultBypass.PACKAGES.mapNotNull { installed[it] }
        if (candidates.isEmpty()) {
            prefs.markBypassOffered()
            return
        }
        _bypassSuggestion.value = candidates
    }

    fun applyBypass(packages: Set<String>) {
        viewModelScope.launch {
            val current = prefs.splitExcluded().toMutableSet()
            current.addAll(packages)
            prefs.setSplitExcluded(current)
            prefs.markBypassOffered()
            _bypassSuggestion.value = emptyList()
            loadApps()
            if (_state.value.connected) {
                reconnectLast()
            }
        }
    }

    fun skipBypass() {
        viewModelScope.launch {
            prefs.markBypassOffered()
            _bypassSuggestion.value = emptyList()
        }
    }

    fun clearError() {
        _state.value = _state.value.copy(error = null)
    }
}
