package in.raahi.backend.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public class VehicleDtos {

    /** Everything the user actually entered — nothing computed, nothing fabricated. */
    public static class VehicleDto {
        public String id;
        public String brand;
        public String model;
        public String variant;
        public Integer modelYear;
        public String fuelType;
        public String registrationNumber;
        public Integer odometerKm;
        public String odometerUpdatedAt;

        // Optional — null means "not provided", and every consumer (CarHealthService,
        // Android UI) must treat null as "unknown", never as zero/false/"none".
        public String lastServiceDate;
        public Integer lastServiceOdometerKm;
        public String insuranceExpiry;
        public String pucExpiry;
        public String tyreReplacedDate;
        public Integer tyreReplacedOdometerKm;
        public String batteryReplacedDate;
        public Integer batteryReplacedOdometerKm;

        public String createdAt;
        public String updatedAt;
    }

    /** Used for both initial "Set up your car" and later edits — same upsert endpoint. */
    public static class UpsertVehicleRequest {
        @NotBlank(message = "Brand is required")
        public String brand;
        @NotBlank(message = "Model is required")
        public String model;
        public String variant;
        @NotNull(message = "Model year is required")
        public Integer modelYear;
        @NotBlank(message = "Fuel type is required")
        public String fuelType;
        @NotBlank(message = "Registration number is required")
        public String registrationNumber;
        @NotNull(message = "Current odometer reading is required")
        @Min(value = 0, message = "Odometer cannot be negative")
        public Integer odometerKm;

        public String lastServiceDate;
        public Integer lastServiceOdometerKm;
        public String insuranceExpiry;
        public String pucExpiry;
        public String tyreReplacedDate;
        public Integer tyreReplacedOdometerKm;
        public String batteryReplacedDate;
        public Integer batteryReplacedOdometerKm;
    }

    public static class UpdateOdometerRequest {
        @NotNull(message = "odometerKm is required")
        @Min(value = 0, message = "Odometer cannot be negative")
        public Integer odometerKm;
    }

    /**
     * score == null means "not enough real data to compute a meaningful score" — the Android
     * client renders that as "Complete your vehicle information to calculate your Car Health",
     * never a fabricated number. See CarHealthService for the exact deterministic formula.
     */
    public static class CarHealthDto {
        public Integer score; // 0-100, or null
        public String message; // populated only when score is null
        public int factorsConsidered;
        public int factorsTotal;
        public List<MaintenanceItemDto> maintenanceItems;
    }

    public static class MaintenanceItemDto {
        public String type; // "Oil Change" | "Tyre Rotation" | "Battery Check" | "Insurance Renewal" | "PUC Renewal"
        public String status; // OK | DUE_SOON | OVERDUE
        public String detail; // human-readable, built from real fields only
        public Integer dueAtKm;
        public String dueAtDate;
    }

    public static class ServiceRecordDto {
        public String id;
        public String serviceDate;
        public Integer odometerKm;
        public String serviceType;
        public String notes;
        public Double cost;
        public String workshopName;
        public String partsReplaced;
        public String createdAt;
    }

    public static class CreateServiceRecordRequest {
        @NotBlank(message = "serviceDate is required")
        public String serviceDate;
        @NotNull(message = "odometerKm is required")
        @Min(value = 0, message = "Odometer cannot be negative")
        public Integer odometerKm;
        @NotBlank(message = "serviceType is required")
        public String serviceType;
        public String notes;
        public Double cost;
        public String workshopName;
        public String partsReplaced;
    }
}
