package com.arcadedb.server.kafka;

import java.time.Instant;
import java.util.UUID;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class Message {
    
    private String eventId = UUID.randomUUID().toString();
    private String timestamp = String.valueOf(Instant.now().toEpochMilli());

    private String username;
    private String eventType;
    private String eventPayload;

    public Message(String eventType, String eventPayload, String username) {
        this.eventType = eventType;
        this.eventPayload = eventPayload;
        this.username = username;
    }
}
