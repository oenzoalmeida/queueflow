package com.queueflow.config;

import org.springframework.beans.factory.annotation.Value;
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
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {
    @Value("${app.cors.allowed-origins}")
    private String allowedOrigins;

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws")
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
