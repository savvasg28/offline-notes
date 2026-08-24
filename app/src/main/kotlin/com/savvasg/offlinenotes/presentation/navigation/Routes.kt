package com.savvasg.offlinenotes.presentation.navigation

object Routes {
    const val NOTES_LIST = "notes"
    const val EDITOR = "editor/{noteId}"
    const val EDITOR_ARG_NOTE_ID = "noteId"
    const val CONFLICT = "conflict/{noteId}"
    const val CONFLICT_ARG_NOTE_ID = "noteId"
    const val DEBUG_PANEL = "debug"

    fun editor(noteId: String) = "editor/$noteId"
    fun conflict(noteId: String) = "conflict/$noteId"
}
