package org.example.kinesisflow.websocket;

import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;
import org.java_websocket.exceptions.WebsocketNotConnectedException;

import java.net.URI;

public class CustomWebSocketClient extends WebSocketClient {

    public CustomWebSocketClient(URI serverUri) {
        super(serverUri);
    }

    @Override
    public void onOpen(ServerHandshake handshakedata) {
        System.out.println("Connected to server");
        try {
            send("Hello WebSocket Server");
        } catch (WebsocketNotConnectedException e) {
            System.err.println("Cannot send message, not connected.");
        }
    }

    @Override
    public void onMessage(String message) {
        System.out.println("Received message: " + message);
        close();
    }

    @Override
    public void onClose(int code, String reason, boolean remote) {
        System.out.println("Connection closed: " + reason);
    }

    @Override
    public void onError(Exception ex) {
        System.err.println("Error: " + ex.getMessage());
    }
}
