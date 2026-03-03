package com.saif.fitness.aiservice.config;

import com.saif.fitness.aiservice.model.Activity;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.support.serializer.ErrorHandlingDeserializer;
import org.springframework.kafka.support.serializer.JacksonJsonDeserializer;

import java.util.HashMap;
import java.util.Map;

@EnableKafka
@Configuration
public class KafkaConsumerConfig {

    private static final String DEFAULT_BOOTSTRAP = "localhost:9092";
    private static final String DEFAULT_GROUP_ID  = "activity-processor-group";

    /**
     * Reads env vars directly from the OS, bypassing Spring's property resolution
     * chain (which would receive empty/unresolved values forwarded by the config
     * server running on Railway where KAFKA_SASL_* vars are not set).
     */
    private String getEnv(String key, String defaultVal) {
        String val = System.getenv(key);
        return (val != null && !val.isBlank()) ? val : defaultVal;
    }

    @Bean
    public ConsumerFactory<String, Activity> consumerFactory() {
        String bootstrapServers = getEnv("KAFKA_BOOTSTRAP_SERVERS", DEFAULT_BOOTSTRAP);
        String saslUsername     = getEnv("KAFKA_SASL_USERNAME", "");
        String saslPassword     = getEnv("KAFKA_SASL_PASSWORD", "");

        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, DEFAULT_GROUP_ID);
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
        props.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, 1);
        props.put("session.timeout.ms", 120000);
        props.put("max.poll.interval.ms", 300000);
        props.put("request.timeout.ms", 120000);

        // SASL/SSL — only added when credentials are present (production on Render)
        if (!saslUsername.isBlank()) {
            String jaasConfig = String.format(
                    "org.apache.kafka.common.security.scram.ScramLoginModule required username=\"%s\" password=\"%s\";",
                    saslUsername, saslPassword);
            props.put("security.protocol", "SASL_SSL");
            props.put("sasl.mechanism", "SCRAM-SHA-256");
            props.put("sasl.jaas.config", jaasConfig);
        }

        // Deserializers — instantiated directly (avoids deprecated JsonDeserializer class-config approach)
        JacksonJsonDeserializer<Activity> valueDeserializer =
                new JacksonJsonDeserializer<>(Activity.class);
        valueDeserializer.setUseTypeHeaders(false);
        valueDeserializer.addTrustedPackages("*");

        return new DefaultKafkaConsumerFactory<>(
                props,
                new StringDeserializer(),
                new ErrorHandlingDeserializer<>(valueDeserializer));
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, Activity> kafkaListenerContainerFactory() {
        ConcurrentKafkaListenerContainerFactory<String, Activity> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory());
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL);
        factory.setConcurrency(1);
        return factory;
    }
}