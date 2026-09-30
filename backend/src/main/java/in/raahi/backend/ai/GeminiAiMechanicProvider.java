package in.raahi.backend.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
public class GeminiAiMechanicProvider implements AiMechanicProvider {

    // Raahi's AI Mechanic system prompt: general roadside guidance only, never a confident
    // diagnosis, always defers to a real mechanic for anything beyond basic troubleshooting.
    // Enforced here via the system instruction sent to the model — the model can still, in
    // principle, ignore it, so this is a mitigation, not a hard guarantee.
    private static final String SYSTEM_PROMPT = """
        You are Raahi's AI Mechanic assistant, helping Indian drivers with roadside vehicle
        problems over chat. Give general, practical guidance for common issues (flat tyre,
        dead battery, overheating, won't start, etc). Never claim certainty about what's
        actually wrong with their specific vehicle — you cannot see or inspect it. For
        anything involving brakes, steering, fuel leaks, smoke, fire, or that you are not
        confident is safe to diagnose over chat, clearly recommend they stop driving and
        contact a real mechanic or use Raahi's Roadside Help / SOS feature. Keep replies
        short and practical, in simple English (or Hindi if the user writes in Hindi).
        """;

    private final String apiKey;
    private final String model;
    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    public GeminiAiMechanicProvider(
            @Value("${raahi.ai.gemini-api-key:}") String apiKey,
            @Value("${raahi.ai.gemini-model:gemini-2.0-flash}") String model
    ) {
        this.apiKey = apiKey;
        this.model = model;
    }

    @Override
    public boolean isConfigured() { return apiKey != null && !apiKey.isBlank(); }

    @Override
    public String providerName() { return "gemini"; }

    @Override
    public String complete(List<ChatTurn> history) throws AiUnavailableException {
        if (apiKey == null || apiKey.isBlank()) {
            throw new AiUnavailableException(
                "AI Mechanic is not configured on this server yet — no GEMINI_API_KEY set.");
        }

        String url = "https://generativelanguage.googleapis.com/v1beta/models/" + model
                + ":generateContent?key=" + apiKey;

        List<Map<String, Object>> contents = new ArrayList<>();
        for (ChatTurn turn : history) {
            String geminiRole = "assistant".equals(turn.role()) ? "model" : "user";
            contents.add(Map.of("role", geminiRole, "parts", List.of(Map.of("text", turn.content()))));
        }

        Map<String, Object> body = Map.of(
                "systemInstruction", Map.of("parts", List.of(Map.of("text", SYSTEM_PROMPT))),
                "contents", contents
        );

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        try {
            JsonNode response = restTemplate.postForObject(url, new HttpEntity<>(body, headers), JsonNode.class);
            if (response == null) {
                throw new AiUnavailableException("AI Mechanic returned no response.");
            }
            JsonNode textNode = response.path("candidates").path(0).path("content").path("parts").path(0).path("text");
            if (textNode.isMissingNode() || textNode.asText().isBlank()) {
                throw new AiUnavailableException("AI Mechanic returned an empty response.");
            }
            return textNode.asText();
        } catch (AiUnavailableException e) {
            throw e;
        } catch (Exception e) {
            // Network failure, non-2xx, unexpected response shape, etc. — surfaced honestly
            // rather than falling back to a fabricated reply.
            throw new AiUnavailableException("Could not reach AI Mechanic right now.", e);
        }
    }
}
