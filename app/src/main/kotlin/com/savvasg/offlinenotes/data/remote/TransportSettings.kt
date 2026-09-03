package com.savvasg.offlinenotes.data.remote

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The base URL is runtime-configurable because it differs by target: the emulator reaches the host
 * at `10.0.2.2`, a physical device needs the host's LAN IP. In memory only, deliberately — this is
 * a demo toggle, not a user setting.
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
        /** The AVD's alias for the host machine. */
        const val DEFAULT_EMULATOR_BASE_URL = "http://10.0.2.2:8080/"
    }
}
