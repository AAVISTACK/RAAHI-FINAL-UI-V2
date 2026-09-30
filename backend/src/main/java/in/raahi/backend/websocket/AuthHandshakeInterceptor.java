package in.raahi.backend.websocket;

import in.raahi.backend.security.AuthenticatedUser;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.util.Map;

/**
 * The old Node backend derived WebSocket identity from an unauthenticated query param
 * (ws://host/ws?userId=xxx) — anyone could open a socket claiming to be any user. That is
 * explicitly banned by the migration brief.
 *
 * The upgrade request here is still a normal HTTP request, so it already passed through
 * JwtAuthFilter (same Bearer-token verification every REST call uses) before Spring's
 * WebSocket handshake machinery even runs. We simply read the AuthenticatedUser that
 * JwtAuthFilter already placed in the SecurityContext for this request. If the request never
 * carried a valid token, no AuthenticatedUser exists here and the handshake is refused with
 * 401 before a socket is ever opened. Nothing about identity is trusted from the client.
 */
@Component
public class AuthHandshakeInterceptor implements HandshakeInterceptor {

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                    WebSocketHandler wsHandler, Map<String, Object> attributes) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof AuthenticatedUser principal)) {
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            return false;
        }
        attributes.put("userId", principal.userId());
        attributes.put("role", principal.role());
        return true;
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                WebSocketHandler wsHandler, Exception exception) {
        // no-op
    }
}
