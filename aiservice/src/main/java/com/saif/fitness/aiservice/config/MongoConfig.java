package com.saif.fitness.aiservice.config;

import com.mongodb.ConnectionString;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.config.AbstractMongoClientConfiguration;
import org.springframework.data.mongodb.config.EnableMongoAuditing;

@Configuration
@EnableMongoAuditing
public class MongoConfig extends AbstractMongoClientConfiguration {

    private static final String DEFAULT_URI = "mongodb://localhost:27017/ai_db";
    private static final String DEFAULT_DB  = "ai_db";

    /**
     * Reads MONGO_AI_URI directly from the OS environment, completely
     * bypassing Spring's property resolution chain (which would otherwise pick up
     * an empty/unresolved value forwarded by the config server on Railway).
     */
    private String resolveUri() {
        String uri = System.getenv("MONGO_AI_URI");
        return (uri != null && !uri.isBlank()) ? uri : DEFAULT_URI;
    }

    @Override
    public MongoClient mongoClient() {
        return MongoClients.create(resolveUri());
    }

    @Override
    protected String getDatabaseName() {
        try {
            String uri = System.getenv("MONGO_AI_URI");
            if (uri != null && !uri.isBlank()) {
                String db = new ConnectionString(uri).getDatabase();
                if (db != null && !db.isBlank()) return db;
            }
        } catch (Exception ignored) {}
        return DEFAULT_DB;
    }

    @Override
    protected boolean autoIndexCreation() {
        return true;
    }
}
