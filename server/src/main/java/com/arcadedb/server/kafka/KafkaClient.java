package com.arcadedb.server.kafka;

import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.logging.Level;

import com.arcadedb.log.LogManager;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.common.errors.TopicExistsException;

public class KafkaClient {
    
    private final AdminClient adminClient;

    private final ConcurrentHashMap<String, Producer> producerCache = new ConcurrentHashMap<>();

    public KafkaClient() {
        adminClient = AdminClient.create(KafkaClientConfiguration.getKafkaClientConfiguration());
    }

    public void createTopicIfNotExists(String topicName) {
        try {
            // Check if the topic already exists
            if (!adminClient.listTopics().names().get().contains(topicName)) {
                int numPartitions = 1;
                short replicationFactor = 2;
                NewTopic newTopic = new NewTopic(topicName, numPartitions, replicationFactor);
                adminClient.createTopics(Collections.singleton(newTopic)).all().get();
                LogManager.instance().log(this, Level.INFO, "Topic created: %s", topicName);
            } else {
                System.out.println(": " + topicName);
                LogManager.instance().log(this, Level.INFO, "Topic already exists: %s", topicName);

            }
        } catch (InterruptedException | ExecutionException e) {
            if (!(e.getCause() instanceof TopicExistsException)) {
                LogManager.instance().log(this, Level.SEVERE, e.getMessage());
            } else {
                LogManager.instance().log(this, Level.INFO, "Topic already exists: %s", topicName);
            }
        }
    }

    public void sendMessage(String database, Message message) {
        LogManager.instance().log(this, Level.INFO, String.format("Event triggered for database %s, payload: %s", database, message));
        producerCache.computeIfAbsent(database, d -> new Producer(getTopicNameForDatabase(d)));
        producerCache.get(database).send(message);
    }

    String getTopicNameForDatabase(String databaseName) {
        return "arcade-cdc_" + databaseName;
    }

    protected void shutdown() {
        for (Map.Entry<String, Producer> entry : this.producerCache.entrySet()) {
            entry.getValue().flush();
        }
    }
}
