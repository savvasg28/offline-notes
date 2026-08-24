package com.savvasg.offlinenotes.presentation.navigation

object Routes {
    const val NOTES_LIST = "notes"
    const val EDITOR = "editor/{noteId}"
    const val EDITOR_ARG_NOTE_ID = "noteId"

    fun editor(noteId: String) = "editor/$noteId"
}
