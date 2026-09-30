package in.raahi.backend.dto;

import jakarta.validation.constraints.NotBlank;

public class AiChatDtos {

    public static class SendMessageRequest {
        @NotBlank(message = "content is required")
        public String content;
    }

    public static class SessionDto {
        public String id;
        public String title;
        public String createdAt;
        public String lastMessageAt;
    }

    public static class MessageDto {
        public String id;
        public String role; // "USER" | "ASSISTANT"
        public String content;
        public String createdAt;
        // Set only on an ASSISTANT message that couldn't actually be generated (no provider
        // configured, or the provider call failed) — the content in that case is an honest
        // "AI Mechanic is unavailable" notice, never a fabricated diagnosis standing in for it.
        public boolean unavailable;
    }
}
