package in.raahi.backend.ai;

import in.raahi.backend.entity.VerificationDocument;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Active whenever no real provider key is configured (AI_PROVIDER_API_KEY unset). Per the
 * migration brief: "If real data/provider is unavailable: show an honest empty/unavailable/
 * error state." A fabricated "looks fine" verdict here would be worse than no verdict at all,
 * since it could wrongly reassure an admin reviewing the application. Every submission still
 * requires manual admin review regardless of what this returns (see HelperController) — this
 * only ever affects the note surfaced to that admin, never the approve/reject decision itself.
 */
@Component
public class NoOpAiTamperScreeningProvider implements AiTamperScreeningProvider {

    private final boolean configured;

    public NoOpAiTamperScreeningProvider(@Value("${raahi.ai.gemini-api-key:}") String apiKey) {
        this.configured = apiKey != null && !apiKey.isBlank();
    }

    @Override
    public ScreeningResult screen(byte[] imageBytes, String contentType) {
        if (!configured) {
            return new ScreeningResult(
                VerificationDocument.AiStatus.UNAVAILABLE,
                "AI tamper screening is not configured on this server — pending manual review."
            );
        }
        // A real provider integration would call out here (backend-only — the API key never
        // reaches the Android client) and map its response to one of the AiStatus values.
        // Not implemented in this pass: no provider credentials exist in this environment to
        // integrate against or test with.
        return new ScreeningResult(
            VerificationDocument.AiStatus.UNAVAILABLE,
            "AI tamper screening provider configured but not yet implemented — pending manual review."
        );
    }
}
