package br.com.pvss.rinhabackend2025;

import org.bson.Document;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.CompoundIndexDefinition;
import org.springframework.data.mongodb.core.index.IndexDefinition;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication
public class RinhaBackend2025Application implements CommandLineRunner {

    private final MongoTemplate mongoTemplate;

    public RinhaBackend2025Application(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    public static void main(String[] args) {
        SpringApplication.run(RinhaBackend2025Application.class, args);
    }

    @Override
    public void run(String... args) {
        String collectionName = "payments";
        Document indexKeys = new Document();
        indexKeys.put("requestedAt", 1);
        indexKeys.put("processor", 1);

        IndexDefinition indexDefinition = new CompoundIndexDefinition(indexKeys);
        mongoTemplate.indexOps(collectionName).createIndex(indexDefinition);
    }
}