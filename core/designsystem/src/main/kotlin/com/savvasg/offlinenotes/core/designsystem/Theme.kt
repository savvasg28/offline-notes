package com.savvasg.offlinenotes.core.designsystem

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable

/**
 * The single place the app's look and feel is named. It wraps plain [MaterialTheme] defaults
 * today — this app has no bespoke color scheme or typography yet — but every screen composes
 * against this function rather than [MaterialTheme] directly, so a real color scheme or type
 * scale is a one-file change instead of a find-and-replace across every feature module.
 */
@Composable
fun OfflineNotesTheme(content: @Composable () -> Unit) {
    MaterialTheme(content = content)
}
