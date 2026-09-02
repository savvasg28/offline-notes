package com.savvasg.offlinenotes

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.savvasg.offlinenotes.presentation.conflict.ConflictResolutionScreen
import com.savvasg.offlinenotes.presentation.conflict.ConflictRoute
import com.savvasg.offlinenotes.presentation.debug.debugDestinations
import com.savvasg.offlinenotes.presentation.debug.openDebugPanel
import com.savvasg.offlinenotes.presentation.editor.EditorRoute
import com.savvasg.offlinenotes.presentation.editor.NoteEditorScreen
import com.savvasg.offlinenotes.presentation.notelist.NotesListRoute
import com.savvasg.offlinenotes.presentation.notelist.NotesListScreen
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    OfflineNotesRoot()
                }
            }
        }
    }
}

@Composable
private fun OfflineNotesRoot() {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = NotesListRoute) {
        composable<NotesListRoute> {
            NotesListScreen(
                onNoteClick = { id -> navController.navigate(EditorRoute(id)) },
                onNewNote = { id -> navController.navigate(EditorRoute(id)) },
                onOpenDebugPanel = openDebugPanel?.let { open -> { open(navController) } },
            )
        }
        composable<EditorRoute> {
            NoteEditorScreen(
                onBack = { navController.popBackStack() },
                onResolveConflict = { id -> navController.navigate(ConflictRoute(id)) },
            )
        }
        composable<ConflictRoute> {
            ConflictResolutionScreen(
                onResolved = { navController.popBackStack() },
                onBack = { navController.popBackStack() },
            )
        }
        debugDestinations(onBack = { navController.popBackStack() })
    }
}
