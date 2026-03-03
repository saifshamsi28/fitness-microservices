package com.saif.fitness.activityservice.config;

import com.saif.fitness.activityservice.models.Activity;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.support.serializer.JacksonJsonSerializer;

import java.util.HashMap;
import java.util.Map;

@Configuration
public class KafkaProducerConfig {

    private static final String DEFAULT_BOOTSTRAP = "localhost:9092";

    /**
     * Reads env vars directly from the OS
     */
    private String getEnv(String key, String defaultVal) {
        String val = System.getenv(key);
        return (val != null && !val.isBlank()) ? val : defaultVal;
    }

    @Bean
    public ProducerFactory<String, Activity> producerFactory() {

        String bootstrapServers = getEnv("KAFKA_BOOTSTRAP_SERVERS", DEFAULT_BOOTSTRAP);
        String saslUsername     = getEnv("KAFKA_SASL_USERNAME", "");
        String saslPassword     = getEnv("KAFKA_SASL_PASSWORD", "");

        Map<String, Object> props = new HashMap<>();

        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ProducerConfig.RETRIES_CONFIG, 3);
        props.put(ProducerConfig.ACKS_CONFIG, "all");
        props.put(ProducerConfig.DELIVERY_TIMEOUT_MS_CONFIG, 30000);
        props.put(ProducerConfig.REQUEST_TIMEOUT_MS_CONFIG, 15000);

        // SASL/SSL — only when credentials present (Render production)
        if (!saslUsername.isBlank()) {
            String jaasConfig = String.format(
                    "org.apache.kafka.common.security.scram.ScramLoginModule required username=\"%s\" password=\"%s\";",
                    saslUsername, saslPassword
            );
            props.put("security.protocol", "SASL_SSL");
            props.put("sasl.mechanism", "SCRAM-SHA-256");
            props.put("sasl.jaas.config", jaasConfig);
        }

        // Modern constructor-based serializer (future-safe)
        JacksonJsonSerializer<Activity> valueSerializer = new JacksonJsonSerializer<>();
        valueSerializer.setAddTypeInfo(false); // Must match consumer's setUseTypeHeaders(false)

        return new DefaultKafkaProducerFactory<>(
                props,
                new StringSerializer(),
                valueSerializer
        );
    }

    @Bean
    public KafkaTemplate<String, Activity> kafkaTemplate() {
        return new KafkaTemplate<>(producerFactory());
    }
}