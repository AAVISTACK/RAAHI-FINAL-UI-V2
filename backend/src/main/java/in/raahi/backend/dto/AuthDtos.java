package in.raahi.backend.dto;

import jakarta.validation.constraints.NotBlank;

public class AuthDtos {

    public static class VerifyRequest {
        @NotBlank(message = "idToken is required")
        public String idToken;
        public String requestedRole; // optional, defaults to DRIVER server-side; client cannot pick ADMIN/etc
    }

    public static class VerifyResponse {
        public String token;
        public boolean isNewUser;
        public UserDto user;
    }

    public static class UserDto {
        public String id;
        public String name;
        public String phone;
        public String role;
        public String vehicleType;
        public String vehicleReg;
        public boolean isVerified;
        public double ratingAvg;
        public int totalHelps;
    }

    public static class UpdateProfileRequest {
        public String name;
        public String vehicleType;
        public String vehicleReg;
        public String language;
    }

    public static class LocationRequest {
        public Double lat;
        public Double lng;
    }

    public static class FcmTokenRequest {
        public String token;
    }
}
