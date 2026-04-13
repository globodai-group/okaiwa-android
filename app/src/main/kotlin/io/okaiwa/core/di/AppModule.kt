package io.okaiwa.core.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.okaiwa.core.config.AppConfig
import io.okaiwa.features.auth.data.remote.AuthApi
import io.okaiwa.features.auth.domain.repositories.AuthRepository
import io.okaiwa.features.chat.domain.repositories.ChatRepository
import io.okaiwa.features.wallet.domain.repositories.WalletRepository
import kotlinx.serialization.json.Json
import okhttp3.CertificatePinner
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

/**
 * Main Hilt dependency injection module.
 *
 * Provides singleton instances for networking (OkHttp with cert pinning,
 * Retrofit), JSON serialization, and repository bindings.
 */
@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideJson(): Json = Json {
        ignoreUnknownKeys = true
        isLenient = false
        encodeDefaults = true
        prettyPrint = false
        coerceInputValues = true
    }

    @Provides
    @Singleton
    fun provideCertificatePinner(): CertificatePinner {
        val env = AppConfig.environment
        val builder = CertificatePinner.Builder()
        val hostname = env.apiBaseUrl
            .removePrefix("https://")
            .removeSuffix("/v1/")

        env.certPinningHashes.forEach { hash ->
            builder.add(hostname, hash)
        }
        return builder.build()
    }

    @Provides
    @Singleton
    fun provideOkHttpClient(
        certificatePinner: CertificatePinner,
    ): OkHttpClient = OkHttpClient.Builder()
        .certificatePinner(certificatePinner)
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .addInterceptor { chain ->
            val request = chain.request().newBuilder()
                .header("User-Agent", "Okaiwa-Android/0.1.0")
                .header("Accept", "application/json")
                .build()
            chain.proceed(request)
        }
        .build()

    @Provides
    @Singleton
    fun provideRetrofit(
        okHttpClient: OkHttpClient,
        json: Json,
    ): Retrofit = Retrofit.Builder()
        .baseUrl(AppConfig.environment.apiBaseUrl)
        .client(okHttpClient)
        .addConverterFactory(
            json.asConverterFactory("application/json; charset=UTF-8".toMediaType())
        )
        .build()

    @Provides
    @Singleton
    fun provideAuthApi(retrofit: Retrofit): AuthApi = retrofit.create(AuthApi::class.java)
}

/**
 * Repository bindings module.
 *
 * Currently binds the stub implementations shipped in the first test
 * build. Real implementations will replace these as the data layer
 * (HTTP clients, WebSocket manager, Room database, FFI bridges) lands.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    // Swapped to the live implementation — calls the identity service
    // at AppConfig.environment.apiBaseUrl. StubAuthRepository is kept
    // around as a reference and for offline unit tests only.
    @dagger.Binds
    @Singleton
    abstract fun bindAuthRepository(
        impl: io.okaiwa.features.auth.data.repositories.RemoteAuthRepository
    ): AuthRepository

    // Wired to the mock while the relay service + Signal Protocol FFI
    // are still under construction. The mock serves a realistic
    // conversation graph so the whole UX can be test-driven before the
    // real HTTP + WebSocket client lands. Swap back to
    // `StubChatRepository` or a production impl by changing this single
    // line.
    @dagger.Binds
    @Singleton
    abstract fun bindChatRepository(
        impl: io.okaiwa.features.chat.data.repositories.MockChatRepository
    ): ChatRepository

    @dagger.Binds
    @Singleton
    abstract fun bindWalletRepository(
        impl: io.okaiwa.features.wallet.data.repositories.StubWalletRepository
    ): WalletRepository

    @dagger.Binds
    @Singleton
    abstract fun bindDeviceContactsRepository(
        impl: io.okaiwa.features.contacts.data.repositories.AndroidDeviceContactsRepository
    ): io.okaiwa.features.contacts.domain.repositories.DeviceContactsRepository

    @dagger.Binds
    @Singleton
    abstract fun bindProfileRepository(
        impl: io.okaiwa.features.profile.data.repositories.MockProfileRepository
    ): io.okaiwa.features.profile.domain.repositories.ProfileRepository
}
