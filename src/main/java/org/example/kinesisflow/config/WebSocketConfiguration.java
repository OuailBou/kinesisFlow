package org.example.kinesisflow.config;

import org.example.kinesisflow.service.JwtService;
import org.example.kinesisflow.service.NotifierWebSocketHandler;
import org.example.kinesisflow.websocket.AuthHandshakeInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;


@Configuration
@EnableWebSocket
public class WebSocketConfiguration implements WebSocketConfigurer {

    private final JwtService jwtService;
    private final NotifierWebSocketHandler notifierWebSocketHandler;


    public WebSocketConfiguration(JwtService jwtService, NotifierWebSocketHandler notifierWebSocketHandler) {
        this.jwtService = jwtService;
        this.notifierWebSocketHandler = notifierWebSocketHandler;
    }


    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(notifierWebSocketHandler, "/ws/notifications")
                .addInterceptors(new AuthHandshakeInterceptor(jwtService))
                .setAllowedOrigins("*");
    }

}