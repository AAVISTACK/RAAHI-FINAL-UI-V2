package `in`.raahi.app.network

import com.google.gson.Gson
import retrofit2.HttpException

private val errorGson = Gson()

/**
 * Runs [block], returning its data on success. On failure:
 *  - HttpException (any non-2xx response): parses the response body as ApiEnvelope<*> and
 *    throws IllegalStateException with the real ApiErrorBody.message, falling back to a
 *    generic message only if the body isn't in the expected shape.
 *  - Any other exception (network failure, timeout, etc.): rethrown as-is so callers can
 *    distinguish "server said no" from "couldn't reach the server" if they need to.
 *
 * Every repository's suspend network calls should go through this rather than reading
 * envelope.error directly, since that path is only ever reached for 2xx responses with
 * success=false, which none of this backend's controllers actually return — they use real
 * HTTP status codes (404/409/403/503) for failures instead.
 */
suspend fun <T> apiCall(block: suspend () -> ApiEnvelope<T>): T {
    try {
        val envelope = block()
        return envelope.data ?: throw IllegalStateException(envelope.error?.message ?: "Request failed")
    } catch (e: HttpException) {
        val message = runCatching {
            val body = e.response()?.errorBody()?.string()
            if (body.isNullOrBlank()) null
            else errorGson.fromJson(body, ApiEnvelope::class.java)?.error?.message
        }.getOrNull()
        throw IllegalStateException(message ?: "Request failed (${e.code()})", e)
    }
}

/**
 * Like [apiCall], but a 404 becomes `null` instead of a thrown exception — for endpoints
 * where "not found yet" is an expected, meaningful state rather than a real error. Added
 * specifically for `GET /vehicles/me`, which 404s (`VEHICLE_NOT_FOUND`) until the user
 * completes "Set up your car"; that's the signal to show the setup flow, not an error banner.
 * [apiCall] itself is untouched — every other repository call keeps its existing behavior.
 */
suspend fun <T> apiCallOrNullOn404(block: suspend () -> ApiEnvelope<T>): T? {
    return try {
        apiCall(block)
    } catch (e: IllegalStateException) {
        if ((e.cause as? HttpException)?.code() == 404) null else throw e
    }
}
