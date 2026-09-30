package `in`.raahi.app.data

import `in`.raahi.app.network.CreateJobRequest
import `in`.raahi.app.network.JobDto
import `in`.raahi.app.network.RaahiApi
import `in`.raahi.app.network.VerifyOtpRequest
import `in`.raahi.app.network.apiCall
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class JobsRepository @Inject constructor(private val api: RaahiApi) {

    suspend fun myJobs(): List<JobDto> = apiCall { api.myJobs() }

    suspend fun availableJobs(): List<JobDto> = apiCall { api.availableJobs() }

    suspend fun getJob(id: String): JobDto = apiCall { api.getJob(id) }

    suspend fun createJob(
        problemType: String, problemDesc: String?, lat: Double, lng: Double,
        highwayName: String? = null, rewardAmount: Double = 0.0
    ): JobDto = apiCall { api.createJob(CreateJobRequest(problemType, problemDesc, lat, lng, highwayName, rewardAmount)) }

    suspend fun acceptJob(id: String): JobDto = apiCall { api.acceptJob(id) }

    suspend fun verifyOtp(id: String, otp: String): JobDto = apiCall { api.verifyJobOtp(id, VerifyOtpRequest(otp)) }

    suspend fun completeJob(id: String) { apiCall { api.completeJob(id) } }

    suspend fun cancelJob(id: String) { apiCall { api.cancelJob(id) } }
}
