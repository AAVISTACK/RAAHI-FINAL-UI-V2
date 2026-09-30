package in.raahi.backend.controller;

import in.raahi.backend.dto.ApiResponse;
import in.raahi.backend.dto.CommerceDtos.SubscriptionPlanDto;
import in.raahi.backend.dto.CommerceDtos.SubscriptionStatusDto;
import in.raahi.backend.entity.Subscription;
import in.raahi.backend.exception.ApiException;
import in.raahi.backend.repository.SubscriptionRepository;
import in.raahi.backend.security.AuthenticatedUser;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/subscriptions")
public class SubscriptionController {

    private final SubscriptionRepository subscriptionRepository;
    private final boolean paymentConfigured;

    public SubscriptionController(SubscriptionRepository subscriptionRepository,
                                   @Value("${raahi.payment.razorpay-key-id:}") String razorpayKeyId) {
        this.subscriptionRepository = subscriptionRepository;
        this.paymentConfigured = razorpayKeyId != null && !razorpayKeyId.isBlank();
    }

    // Real, fixed pricing matching the Flutter reference's subscription screen exactly —
    // this part needs no payment gateway to be honest, it's just product/plan information.
    @GetMapping("/plans")
    public ApiResponse<List<SubscriptionPlanDto>> plans() {
        SubscriptionPlanDto basic = plan("BASIC", "Basic", 999, 9999,
                List.of("Unlimited roadside help requests", "Priority in helper search", "Basic support"));
        SubscriptionPlanDto pro = plan("PRO", "Pro", 2499, 24999,
                List.of("Everything in Basic", "AI Mechanic priority responses", "24x7 priority support", "Zero platform fee on jobs"));
        return ApiResponse.ok(List.of(basic, pro));
    }

    @GetMapping("/me")
    public ApiResponse<SubscriptionStatusDto> me(@AuthenticationPrincipal AuthenticatedUser principal) {
        Subscription sub = subscriptionRepository.findByUserId(principal.userId())
                .orElseThrow(() -> ApiException.notFound("NO_SUBSCRIPTION", "No subscription record found"));
        SubscriptionStatusDto dto = new SubscriptionStatusDto();
        dto.tier = sub.getTier().name();
        dto.status = sub.getStatus().name();
        dto.expiresAt = sub.getExpiresAt() != null ? sub.getExpiresAt().toString() : null;
        dto.isActive = sub.isCurrentlyActive();
        return ApiResponse.ok(dto);
    }

    // Deliberately does NOT activate anything or return a fake success. No Razorpay/Cashfree
    // (or any other payment gateway) credentials exist in this environment — per the
    // migration brief: "If payment provider isn't configured: use a proper unavailable
    // state," never "never fake payment success / subscription activation." A 503 here is
    // the honest answer until real gateway credentials and a checkout integration exist.
    @PostMapping("/subscribe")
    public ApiResponse<Object> subscribe(@AuthenticationPrincipal AuthenticatedUser principal, @RequestParam String tier) {
        if (!tier.equals("BASIC") && !tier.equals("PRO")) {
            throw ApiException.badRequest("INVALID_TIER", "tier must be BASIC or PRO");
        }
        if (!paymentConfigured) {
            throw ApiException.serviceUnavailable("PAYMENT_UNAVAILABLE",
                    "Subscriptions are not available for purchase yet — payment isn't configured on this server.");
        }
        // A real integration would create a Razorpay/Cashfree order here and return
        // checkout details for the Android client to open, then a webhook or a
        // verify-payment endpoint would actually flip the subscription to ACTIVE. Not
        // implemented: no gateway credentials to build or test this against.
        throw ApiException.serviceUnavailable("PAYMENT_UNAVAILABLE", "Payment integration not yet implemented.");
    }

    private SubscriptionPlanDto plan(String tier, String name, double monthly, double yearly, List<String> features) {
        SubscriptionPlanDto dto = new SubscriptionPlanDto();
        dto.tier = tier;
        dto.name = name;
        dto.priceMonthly = monthly;
        dto.priceYearly = yearly;
        dto.features = features;
        return dto;
    }
}
