package in.raahi.backend.ai;

import in.raahi.backend.entity.VerificationDocument;

/**
 * Screens a single uploaded Aadhaar/selfie image for signs of digital tampering or editing.
 * Never asserts the underlying document is "officially genuine" — UIDAI/eKYC integration,
 * OTP verification, and biometric/face matching are explicitly out of scope. The result is
 * always advisory input to a human admin's approve/reject decision (see HelperController),
 * never an automatic approval.
 */
public interface AiTamperScreeningProvider {
    ScreeningResult screen(byte[] imageBytes, String contentType);

    record ScreeningResult(VerificationDocument.AiStatus status, String note) {}
}
