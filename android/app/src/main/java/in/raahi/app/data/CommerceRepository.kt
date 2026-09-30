package `in`.raahi.app.data

import `in`.raahi.app.network.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CommerceRepository @Inject constructor(private val api: RaahiApi) {

    suspend fun fuelRates(): List<FuelRateDto> = apiCall { api.fuelRates() }

    suspend fun shopProducts(category: String? = null): List<ShopProductDto> = apiCall { api.shopProducts(category) }

    suspend fun subscriptionPlans(): List<SubscriptionPlanDto> = apiCall { api.subscriptionPlans() }

    suspend fun mySubscription(): SubscriptionStatusDto = apiCall { api.mySubscription() }

    /** Always throws — no payment gateway is configured on the backend. The message comes
     * straight from the server's real 503 (PAYMENT_UNAVAILABLE), not a client-side guess. */
    suspend fun subscribe(tier: String): Nothing {
        apiCall { api.subscribe(tier) }
        error("unreachable")
    }
}
