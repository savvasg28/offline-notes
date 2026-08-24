package com.savvasg.offlinenotes.di

import android.content.Context
import androidx.room.Room
import com.savvasg.offlinenotes.data.local.ConflictDao
import com.savvasg.offlinenotes.data.local.NoteDao
import com.savvasg.offlinenotes.data.local.OfflineNotesDatabase
import com.savvasg.offlinenotes.data.local.OutboxDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): OfflineNotesDatabase =
        Room.databaseBuilder(context, OfflineNotesDatabase::class.java, OfflineNotesDatabase.DB_NAME)
            .addCallback(OfflineNotesDatabase.SCHEMA_CALLBACK)
            .addCallback(OfflineNotesDatabase.FTS_CALLBACK)
            .build()

    @Provides
    fun provideNoteDao(db: OfflineNotesDatabase): NoteDao = db.noteDao()

    @Provides
    fun provideOutboxDao(db: OfflineNotesDatabase): OutboxDao = db.outboxDao()

    @Provides
    fun provideConflictDao(db: OfflineNotesDatabase): ConflictDao = db.conflictDao()
}
