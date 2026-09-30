package in.raahi.backend.dto;

import jakarta.validation.constraints.NotBlank;

public class DailyDtos {

    public static class StreakDto {
        public int currentStreak;
        public int longestStreak;
        public int totalCheckins;
        public String lastCheckin;
        public boolean canCheckin;
        public String reward; // e.g. "7_day_milestone", null otherwise
    }

    public static class CreateAlertRequest {
        @NotBlank public String type;
        @NotBlank public String message;
        public String location;
        public Double lat;
        public Double lng;
    }

    public static class AlertDto {
        public String id;
        public String type;
        public String message;
        public String location;
        public Double lat;
        public Double lng;
        public int upvotes;
        public int downvotes;
        public String createdAt;
        public String postedBy;
        // What THIS caller voted, if anything — lets the Android client disable the button
        // they already used instead of letting them try again and get a 409.
        public String myVote;
    }

    public static class VoteRequest {
        @NotBlank public String vote; // "up" | "down"
    }

    public static class TipDto {
        public String title;
        public String body;
    }

    public static class PlaceDto {
        public String id;
        public String name;
        public double lat;
        public double lng;
        public Double distanceKm;
    }
}
