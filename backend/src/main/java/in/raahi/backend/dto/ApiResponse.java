package in.raahi.backend.dto;

/**
 * Every endpoint returns one of these shapes — no exceptions.
 * Success: { "success": true, "data": {...} }
 * Error:   { "success": false, "error": { "code": ..., "message": ... } }
 */
public class ApiResponse<T> {
    public boolean success;
    public T data;
    public ApiError error;

    public static <T> ApiResponse<T> ok(T data) {
        ApiResponse<T> r = new ApiResponse<>();
        r.success = true;
        r.data = data;
        return r;
    }

    public static <T> ApiResponse<T> fail(String code, String message) {
        ApiResponse<T> r = new ApiResponse<>();
        r.success = false;
        r.error = new ApiError(code, message);
        return r;
    }

    public static class ApiError {
        public String code;
        public String message;

        public ApiError(String code, String message) {
            this.code = code;
            this.message = message;
        }
    }
}
