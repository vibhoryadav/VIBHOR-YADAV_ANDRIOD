package com.example.network

import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.GET
import java.util.concurrent.TimeUnit

@JsonClass(generateAdapter = true)
data class RemoteHealthResponse(
    val status: String = "unknown",
    val database: String? = null,
    val version: String? = null
)

interface LedgerLabBackendService {
    @GET("health")
    suspend fun getHealth(): Response<RemoteHealthResponse>
}

object LedgerLabRemoteClient {
    private val moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    private val okHttp = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .addInterceptor(
            HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BASIC
            }
        )
        .build()

    suspend fun checkHealth(baseUrl: String): Result<RemoteHealthResponse> {
        return try {
            val normalized = baseUrl.trim().let {
                if (it.endsWith("/")) it else "$it/"
            }
            val retrofit = Retrofit.Builder()
                .baseUrl(normalized)
                .client(okHttp)
                .addConverterFactory(MoshiConverterFactory.create(moshi))
                .build()
            val service = retrofit.create(LedgerLabBackendService::class.java)
            val response = service.getHealth()
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                Result.failure(IllegalStateException("HTTP ${response.code()}: ${response.message()}"))
            }
        } catch (ex: Exception) {
            Result.failure(ex)
        }
    }
}
