package com.savvasg.offlinenotes.core.data

import com.savvasg.offlinenotes.core.data.NoteRepositoryImpl
import com.savvasg.offlinenotes.core.domain.NoteRepository
import com.savvasg.offlinenotes.core.domain.NoteSyncOperations
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    @Binds
    @Singleton
    abstract fun bindNoteRepository(impl: NoteRepositoryImpl): NoteRepository

    @Binds
    @Singleton
    abstract fun bindNoteSyncOperations(impl: NoteRepositoryImpl): NoteSyncOperations
}
