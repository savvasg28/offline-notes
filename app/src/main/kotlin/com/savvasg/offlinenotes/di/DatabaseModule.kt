package com.savvasg.offlinenotes.di

import android.content.Context
import androidx.room.Room
import com.savvasg.offlinenotes.data.local.dao.ConflictDao
import com.savvasg.offlinenotes.data.local.dao.NoteDao
import com.savvasg.offlinenotes.data.local.database.OfflineNotesDatabase
import com.savvasg.offlinenotes.data.local.dao.OutboxDao
import com.savvasg.offlinenotes.data.local.dao.SyncMetaDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): OfflineNotesDatabase =
        Room.databaseBuilder(context, OfflineNotesDatabase::class.java, OfflineNotesDatabase.DB_NAME)
            // Bundled rather than platform SQLite: FTS5 is not guaranteed on the OS-provided one.
            .setDriver(BundledSQLiteDriver())
            .setQueryCoroutineContext(Dispatchers.IO)
            .addCallback(OfflineNotesDatabase.SCHEMA_CALLBACK)
            .addCallback(OfflineNotesDatabase.FTS_CALLBACK)
            .build()

    @Provides
    fun provideNoteDao(db: OfflineNotesDatabase): NoteDao = db.noteDao()

    @Provides
    fun provideOutboxDao(db: OfflineNotesDatabase): OutboxDao = db.outboxDao()

    @Provides
    fun provideConflictDao(db: OfflineNotesDatabase): ConflictDao = db.conflictDao()

    @Provides
    fun provideSyncMetaDao(db: OfflineNotesDatabase): SyncMetaDao = db.syncMetaDao()
}
