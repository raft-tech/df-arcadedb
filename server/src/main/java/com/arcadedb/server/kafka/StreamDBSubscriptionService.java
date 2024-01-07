package com.arcadedb.server.kafka;

import com.arcadedb.database.DatabaseInternal;
import com.arcadedb.event.AfterRecordCreateListener;
import com.arcadedb.event.AfterRecordDeleteListener;
import com.arcadedb.event.AfterRecordUpdateListener;
import com.arcadedb.log.LogManager;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.logging.Level;

public class StreamDBSubscriptionService extends Thread {
    private final ConcurrentMap<String, KafkaEventListener> registeredEventListeners = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, DatabaseInternal> databases;
    private final KafkaClient kafkaClient = new KafkaClient();
    private final String dbNamePattern;
    private final long serviceTimeoutMillis;
    private boolean running = true;

    public StreamDBSubscriptionService(final String dbNamePattern, final ConcurrentMap<String, DatabaseInternal> databases, long serviceTimeoutMillis) {
        this.databases = databases;
        this.dbNamePattern = dbNamePattern;
        this.serviceTimeoutMillis = serviceTimeoutMillis;
    }

    @Override
    public void run() {
        while (this.running) {
            for (Map.Entry<String, DatabaseInternal> entry : this.databases.entrySet()) {
                if (entry.getKey().matches(this.dbNamePattern) && !registeredEventListeners.containsKey(entry.getKey())) {
                    LogManager.instance().log(this, Level.INFO, String.format("Adding event listeners for database: '%s'", entry.getKey()));
                    KafkaEventListener listener = registeredEventListeners.computeIfAbsent(entry.getKey(), k -> new KafkaEventListener(this.kafkaClient, entry.getKey()));
                    entry.getValue().getEvents().registerListener((AfterRecordCreateListener) listener).registerListener((AfterRecordUpdateListener) listener)
                            .registerListener((AfterRecordDeleteListener) listener);
                }

                try {
                    Thread.sleep(this.serviceTimeoutMillis);
                } catch (InterruptedException ignored) {
                    LogManager.instance().log(this, Level.SEVERE, "Shutting down %s. Flushing messages. ", this.getName());
                    this.kafkaClient.shutdown();
                }
            }
        }
    }
}
