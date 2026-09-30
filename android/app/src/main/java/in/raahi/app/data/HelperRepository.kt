package `in`.raahi.app.data

import android.content.Context
import android.net.Uri
import com.google.gson.Gson
import dagger.hilt.android.qualifiers.ApplicationContext
import `in`.raahi.app.network.ApiEnvelope
import `in`.raahi.app.network.HelperApplicationDto
import `in`.raahi.app.network.RaahiApi
import `in`.raahi.app.network.apiCall
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.HttpException
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HelperRepository @Inject constructor(
    private val api: RaahiApi,
    @ApplicationContext private val context: Context,
) {
    suspend fun apply(email: String?, aadhaarFrontUri: Uri, aadhaarBackUri: Uri, selfieUri: Uri?): HelperApplicationDto {
        val emailBody = email?.takeIf { it.isNotBlank() }?.toRequestBody("text/plain".toMediaTypeOrNull())
        val frontPart = uriToPart("aadhaarFront", aadhaarFrontUri)
        val backPart = uriToPart("aadhaarBack", aadhaarBackUri)
        val selfiePart = selfieUri?.let { uriToPart("selfie", it) }
        return apiCall { api.applyAsHelper(emailBody, frontPart, backPart, selfiePart) }
    }

    suspend fun myApplication(): HelperApplicationDto? {
        return try {
            apiCall { api.myHelperApplication() }
        } catch (e: Exception) {
            // NO_APPLICATION is an expected 404 (never applied yet), not a real error.
            // apiCall already collapsed the HttpException into an IllegalStateException by
            // this point, so the underlying HttpException (if any) is its cause.
            val httpEx = e.cause as? HttpException
            val code = httpEx?.let { runCatching {
                Gson().fromJson(it.response()?.errorBody()?.string(), ApiEnvelope::class.java)?.error?.code
            }.getOrNull() }
            if (code == "NO_APPLICATION") null else throw e
        }
    }

    // Copies the picked content:// image into the app's cache dir as a real file so OkHttp
    // can stream it with a known length, rather than holding the whole image in memory as a
    // byte array — images from a phone camera can be several MB each and this uploads three.
    private fun uriToPart(fieldName: String, uri: Uri): MultipartBody.Part {
        val contentType = context.contentResolver.getType(uri) ?: "image/jpeg"
        val cacheFile = File.createTempFile(fieldName, ".img", context.cacheDir)
        context.contentResolver.openInputStream(uri)?.use { input ->
            cacheFile.outputStream().use { output -> input.copyTo(output) }
        }
        val body = cacheFile.asRequestBody(contentType.toMediaTypeOrNull())
        return MultipartBody.Part.createFormData(fieldName, "$fieldName.jpg", body)
    }
}
