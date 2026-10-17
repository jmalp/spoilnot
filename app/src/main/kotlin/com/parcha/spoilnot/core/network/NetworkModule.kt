package com.parcha.spoilnot.core.network

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import okhttp3.OkHttpClient

/** One OkHttp client (connection pool and threads) for recipe pages and images. */
@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {
    @Provides
    @Singleton
    fun okHttpClient(): OkHttpClient = OkHttpClient.Builder().build()
}
