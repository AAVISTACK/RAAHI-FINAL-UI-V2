package in.raahi.backend.ai;

import java.util.List;

/**
 * The Android client never sees an AI provider key or calls the provider directly — every
 * chat turn goes Android → Spring Boot (this) → provider, matching the migration brief's
 * architecture diagram exactly. Implementations must never fabricate diagnostic certainty
 * ("it's definitely your alternator") — general guidance and a recommendation to see a real
 * mechanic for anything serious is the expected tone, enforced via the system prompt in
 * GeminiAiMechanicProvider, not by this interface itself.
 */
public interface AiMechanicProvider {
    record ChatTurn(String role, String content) {} // role: "user" | "assistant"

    /** Returns the assistant's reply text, or throws AiUnavailableException if no real
     * provider is configured or the call fails — callers must surface that honestly rather
     * than inventing a fallback answer. */
    String complete(List<ChatTurn> history) throws AiUnavailableException;

    /** True only when a real provider credential is configured. Does NOT prove the provider
     * is reachable right now — a failed call still surfaces as AiUnavailableException. */
    boolean isConfigured();

    String providerName();

    class AiUnavailableException extends Exception {
        public AiUnavailableException(String message) { super(message); }
        public AiUnavailableException(String message, Throwable cause) { super(message, cause); }
    }
}
