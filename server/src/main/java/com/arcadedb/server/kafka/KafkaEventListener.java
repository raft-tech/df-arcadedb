package com.arcadedb.server.kafka;

import com.arcadedb.database.Record;
import com.arcadedb.event.AfterRecordCreateListener;
import com.arcadedb.event.AfterRecordDeleteListener;
import com.arcadedb.event.AfterRecordUpdateListener;

public class KafkaEventListener implements AfterRecordCreateListener, AfterRecordUpdateListener, AfterRecordDeleteListener {
    enum RecordEvents {
        AFTER_RECORD_UPDATE("AFTER_RECORD_UPDATE"),
        AFTER_RECORD_DELETE("AFTER_RECORD_DELETE"),
        AFTER_RECORD_CREATE("AFTER_RECORD_CREATE");

        private final String value;

        RecordEvents(final String value) {
            this.value = value;
        }

        @Override
        public String toString() {
            return value;
        }
    }

    private final KafkaClient client;
    private final String databaseName;

    public KafkaEventListener(final KafkaClient client, final String dbName) {
        this.client = client;
        this.databaseName = dbName;
        this.client.createTopicIfNotExists(this.client.getTopicNameForDatabase(this.databaseName));
    }

    @Override
    public void onAfterCreate(Record record) {

        Message message = new Message(
                RecordEvents.AFTER_RECORD_CREATE.toString(),
                record.toJSON().toString(),
                record.getDatabase().getCurrentUserName(),
                getEntityName(record));

        this.client.sendMessage(this.databaseName, message);
    }

    @Override
    public void onAfterDelete(Record record) {
        Message message = new Message(
                RecordEvents.AFTER_RECORD_DELETE.toString(),
                record.toJSON().toString(),
                record.getDatabase().getCurrentUserName(),
                getEntityName(record));

        this.client.sendMessage(this.databaseName, message);
    }

    @Override
    public void onAfterUpdate(Record record) {
        Message message = new Message(
                RecordEvents.AFTER_RECORD_UPDATE.toString(),
                record.toJSON().toString(),
                record.getDatabase().getCurrentUserName(),
                getEntityName(record));

        this.client.sendMessage(this.databaseName, message);
    }

    private String getEntityName(Record record) {
        if (record != null) {
            return record.asDocument().getTypeName();
        } else {
            return "";
        }
    }
}
