package com.arcadedb.server.kafka;

import java.util.Properties;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import org.apache.kafka.clients.producer.Callback;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.errors.RetriableException;
import org.apache.kafka.common.serialization.ByteArraySerializer;

import com.fasterxml.jackson.databind.ser.std.StringSerializer;

public class Producer implements Callback {
    // Constants for configuration
    private static final String BOOTSTRAP_SERVERS = "df-kafka-bootstrap:9092";
    private String topicName = "my-topic";
    private static final long NUM_MESSAGES = 50;
    private static final int MESSAGE_SIZE_BYTES = 100;
    private static final long PROCESSING_DELAY_MS = 1000L;

    protected AtomicLong messageCount = new AtomicLong(0);

    KafkaProducer<String, Message> kafkaProducer;

    public Producer(String topicName) {
        this.topicName = topicName;
        try (var producer = createKafkaProducer()) {
            this.kafkaProducer = producer;
        }
    }

    public void send(Message message) {
        kafkaProducer.send(new ProducerRecord<>(topicName, message.getEventId(), message), this);
        messageCount.incrementAndGet();
    }

    private KafkaProducer<String, Message> createKafkaProducer() {
        // Create properties for the Kafka producer
        Properties props = new Properties();
        
        // Configure the connection to Kafka brokers
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, BOOTSTRAP_SERVERS);

        // Set a unique client ID for tracking
        props.put(ProducerConfig.CLIENT_ID_CONFIG, "client_arcade-" + UUID.randomUUID());
        
        // Configure serializers for keys and values
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, ByteArraySerializer.class);

        return new KafkaProducer<>(props);
    }

    private boolean retriable(Exception e) {
        if (e instanceof IllegalArgumentException
            || e instanceof UnsupportedOperationException
            || !(e instanceof RetriableException)) {
            return false;
        } else {
            return true;
        }
    }

    @Override
    public void onCompletion(RecordMetadata metadata, Exception e) {
        if (e != null) {
            // If an exception occurred while sending the record
            System.err.println(e.getMessage());

            if (!retriable(e)) {
                // If the exception is not retriable, print the stack trace and exit
                e.printStackTrace();
      //          System.exit(1);
            }
        } else {
            // If the record was successfully sent
            System.out.printf("Record sent to %s-%d with offset %d%n",
                    metadata.topic(), metadata.partition(), metadata.offset());
        }
    }
}
