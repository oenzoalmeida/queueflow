package com.queueflow.config;

import org.springframework.beans.factory.annotation.Value;
import com.queueflow.auth.AuthController;
import com.queueflow.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import java.util.List;
import java.util.Map;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Configuration
@EnableWebSocketMessageBroker
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {
    private final JwtService jwtService;
    private final UserRepository userRepository;

    @Value("${app.cors.allowed-origins}")
    private String allowedOrigins;

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws")
                .addInterceptors(new HandshakeInterceptor() {
                    @Override
                    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                                   WebSocketHandler handler, Map<String, Object> attributes) {
                        String cookieHeader = request.getHeaders().getFirst(HttpHeaders.COOKIE);
                        if (cookieHeader != null) {
                            for (String cookie : cookieHeader.split(";")) {
                                String[] parts = cookie.trim().split("=", 2);
                                if (parts.length == 2 && AuthController.SESSION_COOKIE.equals(parts[0])) {
                                    attributes.put("sessionJwt", parts[1]);
                                    break;
                                }
                            }
                        }
                        return true;
                    }
                    @Override
                    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                               WebSocketHandler handler, Exception exception) { }
                })
                .setAllowedOrigins(java.util.Arrays.stream(allowedOrigins.split(","))
                        .map(String::trim).filter(origin -> !origin.isEmpty()).toArray(String[]::new))
                .withSockJS();
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(new ChannelInterceptor() {
            @Override
            public Message<?> preSend(Message<?> message, MessageChannel channel) {
                StompHeaderAccessor headers = StompHeaderAccessor.wrap(message);
                StompCommand command = headers.getCommand();
                if (command == StompCommand.CONNECT) {
                    Map<String, Object> attributes = headers.getSessionAttributes();
                    Object rawToken = attributes == null ? null : attributes.remove("sessionJwt");
                    if (rawToken instanceof String token) {
                        var claims = jwtService.parse(token);
                        if (claims != null) {
                            userRepository.findByEmailIgnoreCase(claims.getSubject()).ifPresent(user -> {
                                if (user.isActive()) {
                                    headers.setUser(new UsernamePasswordAuthenticationToken(
                                            user.getEmail(), null,
                                            List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole()))));
                                }
                            });
                        }
                    }
                }
                if (command == StompCommand.SUBSCRIBE) {
                    String destination = headers.getDestination();
                    if ("/topic/display".equals(destination)) return message;
                    if ("/topic/attendant".equals(destination)
                            && headers.getUser() instanceof Authentication auth
                            && auth.isAuthenticated()
                            && auth.getAuthorities().stream().anyMatch(a ->
                                "ROLE_ADMIN".equals(a.getAuthority()) || "ROLE_ATTENDANT".equals(a.getAuthority()))) {
                        return message;
                    }
                    throw new AccessDeniedException("Assinatura WebSocket não autorizada.");
                }
                // Clients receive broadcasts; they never publish broker messages.
                if (command == StompCommand.SEND) {
                    throw new AccessDeniedException("Publicação WebSocket não permitida.");
                }
                return message;
            }
        });
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/topic");
    }
}
