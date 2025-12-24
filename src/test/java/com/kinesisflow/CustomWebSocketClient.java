package com.kinesisflow;

import org.java_websocket.client.WebSocketClient;
import org.java_websocket.exceptions.WebsocketNotConnectedException;
import org.java_websocket.handshake.ServerHandshake;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;

public class CustomWebSocketClient extends WebSocketClient {

    private static final Logger log = LoggerFactory.getLogger(CustomWebSocketClient.class);

    public CustomWebSocketClient(URI serverUri) {
        super(serverUri);
    }

    @Override
    public void onOpen(ServerHandshake handshakedata) {
        log.info("Connected to server");
        try {
            send("Hello WebSocket Server");
        } catch (WebsocketNotConnectedException e) {
            log.warn("Cannot send message, not connected.", e);
        }
    }

    @Override
    public void onMessage(String message) {
        log.info("Received message: {}", message);
        close();
    }

    @Override
    public void onClose(int code, String reason, boolean remote) {
        log.info("Connection closed: {} (code: {}, remote: {})", reason, code, remote);
    }

    @Override
    public void onError(Exception ex) {
        log.error("WebSocket error occurred", ex);
    }
}
