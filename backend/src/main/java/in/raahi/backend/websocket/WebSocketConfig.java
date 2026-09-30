package in.raahi.backend.websocket;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final RaahiWebSocketHandler handler;
    private final AuthHandshakeInterceptor authHandshakeInterceptor;

    public WebSocketConfig(RaahiWebSocketHandler handler, AuthHandshakeInterceptor authHandshakeInterceptor) {
        this.handler = handler;
        this.authHandshakeInterceptor = authHandshakeInterceptor;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        // Same path as the old backend (/ws) so the protocol surface is unchanged — the
        // security model behind it is what changed. No SockJS fallback: this is a native
        // Android client (OkHttp WebSocket), not a browser needing HTTP long-polling fallback.
        registry.addHandler(handler, "/ws")
                .addInterceptors(authHandshakeInterceptor)
                .setAllowedOrigins("*");
    }
}
