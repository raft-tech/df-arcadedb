package com.arcadedb.server.kafka;

import com.arcadedb.database.Record;
import com.arcadedb.event.AfterRecordCreateListener;
import com.arcadedb.event.AfterRecordDeleteListener;
import com.arcadedb.event.AfterRecordUpdateListener;
import com.raft.arcadedb.cdc.Message;

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
        Message message = KafkaRecordUtil.createMessage(RecordEvents.AFTER_RECORD_CREATE, record);

        this.client.sendMessage(this.databaseName, message);
    }

    @Override
    public void onAfterDelete(Record record) {
        Message message = KafkaRecordUtil.createMessage(RecordEvents.AFTER_RECORD_DELETE, record);

        this.client.sendMessage(this.databaseName, message);
    }

    @Override
    public void onAfterUpdate(Record record) {
        Message message = KafkaRecordUtil.createMessage(RecordEvents.AFTER_RECORD_UPDATE, record);

        this.client.sendMessage(this.databaseName, message);
    }
}
