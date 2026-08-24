package xyz.babyplatipus.ptunnel.vpn

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import xyz.babyplatipus.ptunnel.data.model.VpnState

/** Синглтон-мост между VpnService и UI (простой, без DI). */
object VpnStateHolder {
    private val _state = MutableStateFlow(VpnState.DISCONNECTED)
    val state: StateFlow<VpnState> = _state

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    fun setConnecting() { _error.value = null; _state.value = VpnState.CONNECTING }
    fun setConnected()  { _state.value = VpnState.CONNECTED }
    fun setDisconnected(){ _state.value = VpnState.DISCONNECTED }
    fun setError(msg: String) { _error.value = msg; _state.value = VpnState.ERROR }
}
