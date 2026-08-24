package com.savvasg.offlinenotes.di

import com.savvasg.offlinenotes.data.repository.NoteRepositoryImpl
import com.savvasg.offlinenotes.domain.repository.NoteRepository
import com.savvasg.offlinenotes.domain.repository.NoteSyncOperations
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
