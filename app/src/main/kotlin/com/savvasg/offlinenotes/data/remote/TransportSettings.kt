package com.savvasg.offlinenotes.data.remote

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The debug-panel toggle between the default in-process fake and the demo HTTP path (§9), plus
 * the HTTP base URL — configurable at runtime since it differs by target: the emulator reaches
 * the host at `10.0.2.2`, a physical device needs the host's LAN IP (§9 "Practical notes").
 *
 * Deliberately in-memory only, not persisted: this is a reviewer/demo toggle, not a user setting.
 */
@Singleton
class TransportSettings @Inject constructor() {
    private val mutableUseHttpTransport = MutableStateFlow(false)
    val useHttpTransport: StateFlow<Boolean> = mutableUseHttpTransport.asStateFlow()

    private val mutableBaseUrl = MutableStateFlow(DEFAULT_EMULATOR_BASE_URL)
    val baseUrl: StateFlow<String> = mutableBaseUrl.asStateFlow()

    fun setUseHttpTransport(value: Boolean) {
        mutableUseHttpTransport.value = value
    }

    fun setBaseUrl(value: String) {
        mutableBaseUrl.value = value
    }

    companion object {
        /** The AVD's alias for the host machine — see §9 "Emulator networking". */
        const val DEFAULT_EMULATOR_BASE_URL = "http://10.0.2.2:8080/"
    }
}
