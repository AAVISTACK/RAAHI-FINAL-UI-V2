package in.raahi.backend.dto;

public class HelperDtos {

    public static class HelperApplicationDto {
        public String id;
        public String status;
        public String email;
        public String rejectionReason;
        public String submittedAt;
        public String reviewedAt;
        public DocSummary aadhaarFront;
        public DocSummary aadhaarBack;
        public DocSummary selfie;
        // Only populated for ADMIN callers reviewing the queue — a helper checking their own
        // application status doesn't need to see or identify themselves by these fields again.
        public String applicantName;
        public String applicantPhone;
    }

    public static class DocSummary {
        public String id;
        public String aiStatus;
        public String aiNote;
    }

    public static class RejectRequest {
        public String reason;
    }
}
