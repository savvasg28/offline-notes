package com.savvasg.offlinenotes.core.database

import android.content.Context
import androidx.room.Room
import com.savvasg.offlinenotes.core.database.dao.ConflictDao
import com.savvasg.offlinenotes.core.database.dao.NoteDao
import com.savvasg.offlinenotes.core.database.OfflineNotesDatabase
import com.savvasg.offlinenotes.core.database.dao.OutboxDao
import com.savvasg.offlinenotes.core.database.dao.SyncMetaDao
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
