package in.raahi.backend.dto;

import java.util.List;

public class CommerceDtos {

    public static class FuelRateDto {
        public String state;
        public double petrol;
        public double diesel;
        public Double cng;
        public String updatedAt; // real DB timestamp — never fabricated "today"
        public String source;    // ADMIN_MANUAL | SEED_INDICATIVE — lets the UI avoid implying "live"
    }

    public static class ShopProductDto {
        public String id;
        public String name;
        public String category;
        public double price;
        public Double discountPrice;
        public String brand;
        public Double rating;
        public Integer reviewCount;
        public String description;
        // Real Amazon product-page deep link (with the affiliate tag if one is configured
        // server-side) — tapping it leaves the app. There is no in-app cart or checkout;
        // the reference product never had one for Shop (see SHOP_AFFILIATE_SETUP.md).
        public String affiliateUrl;
        public String imageUrl;
    }

    public static class SubscriptionPlanDto {
        public String tier; // "BASIC" | "PRO"
        public String name;
        public double priceMonthly;
        public double priceYearly;
        public List<String> features;
    }

    public static class SubscriptionStatusDto {
        public String tier;
        public String status;
        public String expiresAt;
        public boolean isActive;
    }
}
