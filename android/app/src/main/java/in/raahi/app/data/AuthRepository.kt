package `in`.raahi.app.data

import android.app.Activity
import com.google.firebase.FirebaseException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.PhoneAuthCredential
import com.google.firebase.auth.PhoneAuthOptions
import com.google.firebase.auth.PhoneAuthProvider
import `in`.raahi.app.network.FcmTokenRequest
import `in`.raahi.app.network.RaahiApi
import `in`.raahi.app.network.UpdateProfileRequest
import `in`.raahi.app.network.UserDto
import `in`.raahi.app.network.VerifyRequest
import `in`.raahi.app.network.apiCall
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

sealed class PhoneAuthEvent {
    data class CodeSent(val verificationId: String) : PhoneAuthEvent()
    data class AutoVerified(val credential: PhoneAuthCredential) : PhoneAuthEvent()
    data class Failed(val message: String) : PhoneAuthEvent()
}

data class SignInResult(val token: String, val isNewUser: Boolean, val user: UserDto)

@Singleton
class AuthRepository @Inject constructor(
    private val firebaseAuth: FirebaseAuth,
    private val api: RaahiApi,
    private val tokenManager: TokenManager,
) {

    /** Wraps Firebase's callback-based verifyPhoneNumber as a Flow — no raw callbacks in the UI layer. */
    fun sendOtp(phone: String, activity: Activity) = callbackFlow {
        val callbacks = object : PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
            override fun onVerificationCompleted(credential: PhoneAuthCredential) {
                trySend(PhoneAuthEvent.AutoVerified(credential))
            }

            override fun onVerificationFailed(e: FirebaseException) {
                trySend(PhoneAuthEvent.Failed(e.message ?: "OTP bhejne mein error. Dobara try karo."))
            }

            override fun onCodeSent(verificationId: String, token: PhoneAuthProvider.ForceResendingToken) {
                trySend(PhoneAuthEvent.CodeSent(verificationId))
            }
        }

        val options = PhoneAuthOptions.newBuilder(firebaseAuth)
            .setPhoneNumber(phone) // caller passes full E.164, e.g. +91XXXXXXXXXX
            .setTimeout(60L, TimeUnit.SECONDS)
            .setActivity(activity)
            .setCallbacks(callbacks)
            .build()
        PhoneAuthProvider.verifyPhoneNumber(options)

        awaitClose { }
    }

    /** Confirms the 6-digit code against Firebase, then exchanges the Firebase ID token for our JWT. */
    suspend fun confirmOtpAndSignIn(verificationId: String, code: String): SignInResult {
        val credential = PhoneAuthProvider.getCredential(verificationId, code)
        return signInWithCredential(credential)
    }

    suspend fun signInWithCredential(credential: PhoneAuthCredential): SignInResult {
        val authResult = firebaseAuth.signInWithCredential(credential).await()
        val idToken = authResult.user?.getIdToken(false)?.await()?.token
            ?: throw IllegalStateException("Firebase sign-in succeeded but no ID token was returned")

        val data = apiCall { api.verify(VerifyRequest(idToken = idToken)) }
        tokenManager.saveToken(data.token)
        return SignInResult(data.token, data.isNewUser, data.user)
    }

    /** GET /auth/me — used by Home to load the signed-in user's real profile (name, vehicle, role, rating). */
    suspend fun currentUser(): UserDto = apiCall { api.me() }

    /** PUT /auth/profile — used by the new-user profile-setup step and any later profile edits. */
    suspend fun updateProfile(name: String?, vehicleType: String?, vehicleReg: String?): UserDto =
        apiCall { api.updateProfile(UpdateProfileRequest(name = name, vehicleType = vehicleType, vehicleReg = vehicleReg)) }

    suspend fun signOut() {
        firebaseAuth.signOut()
        tokenManager.clear()
    }

    /** No-ops when signed out (no auth token to call the API with yet) — the FCM SDK can
     * hand us a token before/without a Raahi session existing, e.g. right after install. */
    suspend fun registerFcmToken(token: String) {
        if (tokenManager.getToken() == null) return
        apiCall { api.updateFcmToken(FcmTokenRequest(token)) }
    }
}
