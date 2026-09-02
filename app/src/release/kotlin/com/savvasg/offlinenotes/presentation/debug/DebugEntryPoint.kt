package com.savvasg.offlinenotes.presentation.debug

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder

/** Release counterpart of the debug seam: no opener, no destinations, no debug UI in the binary. */
val openDebugPanel: ((NavController) -> Unit)? = null

fun NavGraphBuilder.debugDestinations(onBack: () -> Unit) = Unit
