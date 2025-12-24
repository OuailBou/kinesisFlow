package com.kinesisflow.websocket;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.security.Principal;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class NotifierWebSocketHandler extends TextWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(NotifierWebSocketHandler.class);
    private final Map<String, WebSocketSession> sessionsByUserId = new ConcurrentHashMap<>();


    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        Principal principal = (Principal) session.getAttributes().get("userPrincipal");

        if (principal == null || principal.getName() == null) {
            log.warn("Connection {} established without authentication. closing.", session.getId());
            session.close(CloseStatus.POLICY_VIOLATION.withReason("Authentication required"));
            return;
        }

        String userId = principal.getName();
        sessionsByUserId.put(userId, session);
        log.info("Session {} REGISTERED for the user: {}", session.getId(), userId);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        Principal principal = (Principal) session.getAttributes().get("userPrincipal");
        if (principal != null && principal.getName() != null) {
            String userId = principal.getName();
            sessionsByUserId.remove(userId);
            log.info("Session for the user {} closed.", userId);
        }
    }

    public void sendMessageToUser(String userId, String payload) {
        WebSocketSession session = sessionsByUserId.get(userId);
        if (session != null && session.isOpen()) {
            try {
                session.sendMessage(new TextMessage(payload));
                log.info("MESSAGE SENT for the user {}.", userId);

            } catch (IOException e) {
                log.error("Error sending the message to the user {}: {}", userId, e.getMessage());
            }
        }
        else {
            System.out.println("❌ User " + userId + " not connected or session closed.");
        }
    }
}
