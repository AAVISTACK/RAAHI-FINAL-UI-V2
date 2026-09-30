package in.raahi.backend.dto;

import jakarta.validation.constraints.NotNull;

public class SosDtos {

    public static class TriggerSosRequest {
        @NotNull
        public Double lat;
        @NotNull
        public Double lng;
    }

    public static class SosDto {
        public String id;
        public String status;
        public Double lat;
        public Double lng;
        public String createdAt;
        // Only populated for a caller who is allowed to see who triggered this event —
        // the requester themself, or a nearby MECHANIC/HELPER viewing the nearby-alerts list.
        // Phone number is intentionally never included here at all: the old Node /sos/active
        // endpoint returned the triggering user's phone + vehicle_reg to any authenticated
        // caller with no role/radius check, which this migration does not reproduce.
        public String requesterName;
        public Double distanceKm;
    }

    public static class TriggerSosResponse {
        public SosDto sos;
        public int nearbyMechanicsNotified;
    }
}
