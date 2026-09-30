package in.raahi.backend.stats;

/**
 * Pure odometer rules shared by every write path (vehicle edit, fuel log, service record),
 * so "the odometer never goes backwards" is defined in exactly one place.
 */
public final class OdometerRules {
    private OdometerRules() {}

    /** True when the entered reading is strictly higher than what is on file (or nothing is on file). */
    public static boolean advances(Integer current, Integer entered) {
        return entered != null && (current == null || entered > current);
    }

    /** True when the entered reading is strictly lower than what is on file. */
    public static boolean isDecrease(Integer current, Integer entered) {
        return current != null && entered != null && entered < current;
    }
}
