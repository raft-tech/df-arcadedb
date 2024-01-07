package com.arcadedb.server.kafka;

import com.arcadedb.database.Record;
import com.arcadedb.event.AfterRecordCreateListener;
import com.arcadedb.event.AfterRecordDeleteListener;
import com.arcadedb.event.AfterRecordUpdateListener;

public class KafkaEventListener implements AfterRecordCreateListener, AfterRecordUpdateListener, AfterRecordDeleteListener {
    enum RecordEvents {
        AFTER_RECORD_UPDATE("after_record_update"),
        AFTER_RECORD_DELETE("after_record_delete"),
        AFTER_RECORD_CREATE("after_record_create");

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
                record.getDatabase().getCurrentUserName());

        this.client.sendMessage(this.databaseName, message);
    }

    @Override
    public void onAfterDelete(Record record) {
        Message message = new Message(
                RecordEvents.AFTER_RECORD_DELETE.toString(),
                record.toJSON().toString(),
                record.getDatabase().getCurrentUserName());

        this.client.sendMessage(this.databaseName, message);
    }

    @Override
    public void onAfterUpdate(Record record) {
        Message message = new Message(
                RecordEvents.AFTER_RECORD_UPDATE.toString(),
                record.toJSON().toString(),
                record.getDatabase().getCurrentUserName());

        this.client.sendMessage(this.databaseName, message);
    }
}
