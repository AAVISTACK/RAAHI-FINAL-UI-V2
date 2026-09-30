package in.raahi.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public class JobDtos {

    public static class CreateJobRequest {
        @NotBlank(message = "problemType is required")
        public String problemType;
        public String problemDesc;
        @NotNull(message = "lat is required")
        public Double lat;
        @NotNull(message = "lng is required")
        public Double lng;
        public String highwayName;
        public Double rewardAmount = 0.0;
    }

    public static class VerifyOtpRequest {
        @NotBlank(message = "otp is required")
        public String otp;
    }

    public static class JobDto {
        public String id;
        public String status;
        public String problemType;
        public String problemDesc;
        public Double lat;
        public Double lng;
        public Double rewardAmount;
        public String helperOtp; // only populated for the requester, stripped for anyone else
        public String requesterName;
        public String requesterPhone; // only populated for the assigned helper — see toDto
        public String helperName;
        public String helperPhone; // only populated for the requester
        public Double helperRatingAvg;
        public Integer helperTotalHelps;
        public Boolean helperVerified;
        // "REQUESTER" or "HELPER" — computed server-side so the Android client never has to
        // guess which role it's viewing a job as (e.g. by string-matching names) when
        // rendering /jobs/mine, which mixes both.
        public String viewerRole;
    }
}
