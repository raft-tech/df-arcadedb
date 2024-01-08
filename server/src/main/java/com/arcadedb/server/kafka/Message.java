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

    private String username; // user that made the change
    private String eventType; // create read update
    private String entityName; // table in database
    private String eventPayload;

    public Message(String eventType, String eventPayload, String username, String entityName) {
        this.eventType = eventType;
        this.username = username;
        this.entityName = entityName;
        this.eventPayload = eventPayload;
    }

    @Override
    public String toString() {
        return new Gson().toJson(this);
    }
}
