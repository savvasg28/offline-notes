package com.savvasg.offlinenotes

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.savvasg.offlinenotes.presentation.conflict.ConflictResolutionScreen
import com.savvasg.offlinenotes.presentation.debug.DebugPanelScreen
import com.savvasg.offlinenotes.presentation.editor.NoteEditorScreen
import com.savvasg.offlinenotes.presentation.navigation.Routes
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
    NavHost(navController = navController, startDestination = Routes.NOTES_LIST) {
        composable(Routes.NOTES_LIST) {
            NotesListScreen(
                onNoteClick = { id -> navController.navigate(Routes.editor(id)) },
                onNewNote = { id -> navController.navigate(Routes.editor(id)) },
                onOpenDebugPanel = { navController.navigate(Routes.DEBUG_PANEL) },
            )
        }
        composable(
            route = Routes.EDITOR,
            arguments = listOf(navArgument(Routes.EDITOR_ARG_NOTE_ID) { type = NavType.StringType }),
        ) {
            NoteEditorScreen(
                onBack = { navController.popBackStack() },
                onResolveConflict = { id -> navController.navigate(Routes.conflict(id)) },
            )
        }
        composable(
            route = Routes.CONFLICT,
            arguments = listOf(navArgument(Routes.CONFLICT_ARG_NOTE_ID) { type = NavType.StringType }),
        ) {
            ConflictResolutionScreen(
                onResolved = { navController.popBackStack() },
                onBack = { navController.popBackStack() },
            )
        }
        composable(Routes.DEBUG_PANEL) {
            DebugPanelScreen(onBack = { navController.popBackStack() })
        }
    }
}
