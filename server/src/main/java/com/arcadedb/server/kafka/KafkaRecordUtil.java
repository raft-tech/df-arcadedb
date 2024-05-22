package com.arcadedb.server.kafka;

import com.arcadedb.database.Document;
import com.arcadedb.database.Record;
import com.arcadedb.graph.Edge;
import com.arcadedb.graph.Vertex;
import com.raft.arcadedb.cdc.Message;

import java.time.Instant;
import java.util.UUID;

public class KafkaRecordUtil {
    protected static Message createMessage(KafkaEventListener.RecordEvents afterRecordEvent, Record record) {
        return Message.newBuilder()
                .setEventId(UUID.randomUUID().toString())
                .setTimestamp(String.valueOf(Instant.now().toEpochMilli()))
                .setEventType(afterRecordEvent.toString())
                .setEventPayload(record.toJSON().toString())
                .setUsername(record.getDatabase().getCurrentUserName())
                .setEntityName(getEntityName(record))
                .setEntityId(record.getIdentity().toString())
                .build();
    }

    protected static String getEntityName(Record record) {
        if (record instanceof Document) {
            return record.asDocument().getTypeName();
        } else if (record instanceof Edge) {
            return record.asEdge().getTypeName();
        } else if (record instanceof Vertex) {
            return record.asVertex().getTypeName();
        } else {
            return "";
        }
    }
}
