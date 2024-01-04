package com.arcadedb.server.kafka;

import java.util.Collections;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;

import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.common.errors.TopicExistsException;

public class KafkaClient {
    
    private final String brokerUrl = "df-kafka-bootstrap:9092"; // System.getenv("KAFKA_BOOTSTRAP");//, "df-kafka-bootstrap:9092");

    private final AdminClient adminClient;

    private ConcurrentHashMap<String, Producer> producerCache = new ConcurrentHashMap<>();

    public KafkaClient() {
        Properties config = new Properties();
        config.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, brokerUrl);
        adminClient = AdminClient.create(config);
    }

    public void createTopicIfNotExists(String topicName) {
        try {
            // Check if the topic already exists
            if (!adminClient.listTopics().names().get().contains(topicName)) {
                int numPartitions = 1;
                short replicationFactor = 2;
                NewTopic newTopic = new NewTopic(topicName, numPartitions, replicationFactor);
                adminClient.createTopics(Collections.singleton(newTopic)).all().get();
                System.out.println("Topic created: " + topicName);
            } else {
                System.out.println("Topic already exists: " + topicName);
            }
        } catch (InterruptedException | ExecutionException e) {
            if (!(e.getCause() instanceof TopicExistsException)) {
                // Handle exception
                e.printStackTrace();
            } else {
                // TopicExistsException - this can happen if the topic was created between the check and the create call
                System.out.println("Topic already exists: " + topicName);
            }
        } finally {
            adminClient.close();
        }
    }

    public void sendMessage(String database, Message message) {
        producerCache.computeIfAbsent(database, d -> new Producer(getTopicNameForDatabase(d)));
        producerCache.get(database).send(message);
    }

    private String getTopicNameForDatabase(String databaseName) {
        return "arcade-cdc_" + databaseName;
    }
}
