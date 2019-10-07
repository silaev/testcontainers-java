package org.testcontainers.containers.integration;

import com.mongodb.ReadConcern;
import com.mongodb.ReadPreference;
import com.mongodb.TransactionOptions;
import com.mongodb.WriteConcern;
import com.mongodb.client.TransactionBody;
import lombok.val;
import org.bson.Document;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.platform.commons.JUnitException;
import org.testcontainers.containers.MongoDbContainer;
import org.testcontainers.containers.core.IntegrationTest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@IntegrationTest
class TransactionITTest {
    private final MongoDbContainer mongoDbContainer = new MongoDbContainer();

    @BeforeEach
    void setUp() {
        mongoDbContainer.start();
    }

    @AfterEach
    void tearDown() {
        mongoDbContainer.stop();
    }

    /**
     * Taken from <a href="https://docs.mongodb.com/manual/core/transactions/">https://docs.mongodb.com</a>
     */
    @Test
    void shouldExecuteTransactions() {
        //GIVEN
        val mongoRsUrl = mongoDbContainer.getReplicaSetUrl();
        assertNotNull(mongoRsUrl);
        val mongoSyncClient = com.mongodb.client.MongoClients.create(mongoRsUrl);
        mongoSyncClient.getDatabase("mydb1").getCollection("foo")
            .withWriteConcern(WriteConcern.MAJORITY).insertOne(new Document("abc", 0));
        mongoSyncClient.getDatabase("mydb2").getCollection("bar")
            .withWriteConcern(WriteConcern.MAJORITY).insertOne(new Document("xyz", 0));

        val clientSession = mongoSyncClient.startSession();
        val txnOptions = TransactionOptions.builder()
            .readPreference(ReadPreference.primary())
            .readConcern(ReadConcern.LOCAL)
            .writeConcern(WriteConcern.MAJORITY)
            .build();

        val trxResult = "Inserted into collections in different databases";

        //WHEN + THEN
        TransactionBody<String> txnBody = () -> {
            val coll1 = mongoSyncClient.getDatabase("mydb1").getCollection("foo");
            val coll2 = mongoSyncClient.getDatabase("mydb2").getCollection("bar");

            coll1.insertOne(clientSession, new Document("abc", 1));
            coll2.insertOne(clientSession, new Document("xyz", 999));
            return trxResult;
        };

        try {
            val trxResultActual = clientSession.withTransaction(txnBody, txnOptions);
            assertEquals(trxResult, trxResultActual);
        } catch (RuntimeException re) {
            throw new JUnitException(re.getMessage(), re);
        } finally {
            clientSession.close();
            mongoSyncClient.close();
        }
    }
}
