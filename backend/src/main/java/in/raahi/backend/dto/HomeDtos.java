package in.raahi.backend.dto;

/** DTOs for the data that backs the Home screen. Every field is derived from persisted rows. */
public class HomeDtos {

    public static class WeeklyKmDto {
        public Integer km;               // null when it cannot be derived from real readings
        public String since;             // ISO instant of the baseline odometer reading
        public boolean fullWindow;       // false => baseline is newer than 7 days (partial window)
        public String unavailableReason; // e.g. NEED_MORE_ODOMETER_READINGS, NO_VEHICLE
    }

    public static class HomeSummaryDto {
        public WeeklyKmDto weeklyKm;
        public int helpsGivenThisWeek;   // COMPLETED jobs where this user was the helper, last 7 days
        public int unreadNotifications;
    }

    public static class FuelSummaryDto {
        public int periodDays;
        public int fillUps;
        public double totalSpent;
        public Double totalLitres;       // null unless every log in the period recorded litres
        public String lastFilledAt;      // most recent fill-up overall (may be older than the period)
        public boolean hasAnyLogs;
    }

    public static class FuelLogDto {
        public String id;
        public String filledAt;
        public Double litres;
        public Double pricePerLitre;
        public double totalCost;
        public Integer odometerKm;
        public String fuelType;
    }

    public static class CreateFuelLogRequest {
        @jakarta.validation.constraints.Positive public java.math.BigDecimal totalCost;
        @jakarta.validation.constraints.Positive public java.math.BigDecimal litres;
        @jakarta.validation.constraints.Positive public java.math.BigDecimal pricePerLitre;
        @jakarta.validation.constraints.PositiveOrZero public Integer odometerKm;
        @jakarta.validation.constraints.Size(max = 20) public String fuelType;
        public String filledAt; // ISO-8601 instant; defaults to now
    }

    public static class NotificationDto {
        public String id;
        public String title;
        public String body;
        public String createdAt;
        public boolean read;
    }

    public static class AiStatusDto {
        public String state;       // AVAILABLE | UNAVAILABLE
        public boolean available;
        public String provider;
        public String message;
    }
}
