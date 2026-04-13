package io.okaiwa.features.auth.data.remote

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST

/**
 * Retrofit definition for the identity service auth endpoints.
 *
 * The base URL is injected via `AppConfig.environment.apiBaseUrl`
 * (`https://okaiwa-api.globodai.group/v1/` in DEV), so the paths below
 * are relative to that prefix and NOT prefixed with `/v1/`.
 *
 * Every call returns `Response<T>` rather than `T` directly so the
 * repository can distinguish 4xx / 5xx bodies from transport failures
 * without relying on exception-for-control-flow.
 */
interface AuthApi {
    @POST("auth/register")
    suspend fun register(@Body body: RegisterRequest): Response<RegisterResponse>

    @POST("auth/verify")
    suspend fun verify(@Body body: VerifyRequest): Response<SessionTokenResponse>

    @POST("auth/refresh")
    suspend fun refresh(@Body body: RefreshRequest): Response<SessionTokenResponse>
}
