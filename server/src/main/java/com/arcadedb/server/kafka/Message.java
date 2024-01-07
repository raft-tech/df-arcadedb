package com.arcadedb.server.kafka;

import java.time.Instant;
import java.util.UUID;

import com.google.gson.Gson;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

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

    @Override
    public String toString() {
        return new Gson().toJson(this);
    }
}
