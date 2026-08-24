package com.savvasg.offlinenotes.di

import com.savvasg.offlinenotes.sync.transport.TransportSettings
import com.savvasg.offlinenotes.sync.transport.http.OfflineNotesApi
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

@Module
@InstallIn(SingletonComponent::class)
object HttpModule {

    private const val PLACEHOLDER_BASE_URL = "http://offline-notes.invalid/"

    @Provides
    @Singleton
    fun provideJson(): Json = Json { ignoreUnknownKeys = true }

    /**
     * Retrofit needs an absolute base URL at build time, but §9 wants it changeable at runtime
     * from the debug panel (emulator vs. physical device on the LAN). Fixed placeholder base +
     * this interceptor rewriting scheme/host/port on every request from [TransportSettings],
     * read live rather than captured once at Retrofit-build time.
     */
    @Provides
    @Singleton
    fun provideOkHttpClient(settings: TransportSettings): OkHttpClient =
        OkHttpClient.Builder()
            .addInterceptor(
                Interceptor { chain ->
                    val original = chain.request()
                    val dynamicBase = settings.baseUrl.value.toHttpUrlOrNull()
                    val request = if (dynamicBase != null) {
                        val newUrl = original.url.newBuilder()
                            .scheme(dynamicBase.scheme)
                            .host(dynamicBase.host)
                            .port(dynamicBase.port)
                            .build()
                        original.newBuilder().url(newUrl).build()
                    } else {
                        original
                    }
                    chain.proceed(request)
                },
            )
            .build()

    @Provides
    @Singleton
    fun provideRetrofit(okHttpClient: OkHttpClient, json: Json): Retrofit =
        Retrofit.Builder()
            .baseUrl(PLACEHOLDER_BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()

    @Provides
    @Singleton
    fun provideOfflineNotesApi(retrofit: Retrofit): OfflineNotesApi = retrofit.create(OfflineNotesApi::class.java)
}
