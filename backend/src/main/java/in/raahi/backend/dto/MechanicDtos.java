package in.raahi.backend.dto;

import jakarta.validation.constraints.NotNull;

public class MechanicDtos {

    public static class MechanicDto {
        public String userId;
        public String name;
        public String phone;
        public String shopName;
        public String specializations;
        public boolean isAvailable;
        public Double lat;
        public Double lng;
        public double distanceKm;
        public double ratingAvg;
    }

    public static class UpdateLocationRequest {
        @NotNull(message = "lat is required")
        public Double lat;
        @NotNull(message = "lng is required")
        public Double lng;
    }
}
