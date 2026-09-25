package com.savvasg.offlinenotes

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.savvasg.offlinenotes.designsystem.theme.OfflineNotesTheme
import com.savvasg.offlinenotes.feature.conflict.ConflictResolutionScreen
import com.savvasg.offlinenotes.feature.conflict.ConflictRoute
import com.savvasg.offlinenotes.feature.editor.EditorRoute
import com.savvasg.offlinenotes.feature.editor.NoteEditorScreen
import com.savvasg.offlinenotes.feature.notelist.NotesListRoute
import com.savvasg.offlinenotes.feature.notelist.NotesListScreen
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            OfflineNotesTheme {
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
    }
}
