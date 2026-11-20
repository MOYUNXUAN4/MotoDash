package com.motodash.data

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.motodash.spotify.SpotifyAuthApi
import com.motodash.spotify.SpotifyConfig
import com.motodash.spotify.SpotifyWebApi
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object SpotifyNetworkModule {
    fun createGson(): Gson = GsonBuilder()
        .serializeNulls()
        .create()

    fun createOkHttpClient(enableLogging: Boolean = false): OkHttpClient {
        val builder = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)

        if (enableLogging) {
            val logging = HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BASIC
            }
            builder.addInterceptor(logging)
        }
        return builder.build()
    }

    fun createSpotifyWebApi(
        baseUrl: String = SpotifyConfig.API_BASE_URL,
        client: OkHttpClient = createOkHttpClient(),
        gson: Gson = createGson()
    ): SpotifyWebApi {
        return Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
            .create(SpotifyWebApi::class.java)
    }

    fun createSpotifyAuthApi(
        baseUrl: String = SpotifyConfig.AUTH_BASE_URL,
        client: OkHttpClient = createOkHttpClient(),
        gson: Gson = createGson()
    ): SpotifyAuthApi {
        return Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
            .create(SpotifyAuthApi::class.java)
    }
}
