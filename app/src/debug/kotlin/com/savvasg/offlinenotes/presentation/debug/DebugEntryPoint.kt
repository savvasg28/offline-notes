package com.savvasg.offlinenotes.presentation.debug

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import kotlinx.serialization.Serializable

@Serializable
private data object DebugPanelRoute

/**
 * The debug build's half of the seam that keeps developer tooling out of release. `src/release`
 * declares the same two symbols — a null opener and a no-op graph — so `MainActivity` compiles
 * against either variant without knowing which one it's in, and the route type itself stays
 * private to this source set.
 */
val openDebugPanel: ((NavController) -> Unit)? = { it.navigate(DebugPanelRoute) }

fun NavGraphBuilder.debugDestinations(onBack: () -> Unit) {
    composable<DebugPanelRoute> { DebugPanelScreen(onBack = onBack) }
}
