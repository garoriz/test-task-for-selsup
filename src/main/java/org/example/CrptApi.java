package org.example;

import org.apache.http.client.fluent.Content;
import org.apache.http.client.fluent.Request;
import org.apache.http.entity.ContentType;

import java.io.IOException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class CrptApi {
    private final int requestLimit;
    private final long timeIntervalMillis;
    private long lastResetTime;
    private int requestCount;
    private final Lock lock;

    public CrptApi(TimeUnit timeUnit, int requestLimit) {
        this.requestLimit = requestLimit;
        this.timeIntervalMillis = timeUnit.toMillis(1);
        this.lastResetTime = System.currentTimeMillis();
        this.requestCount = 0;
        this.lock = new ReentrantLock();
    }

    public void createDocument(Document document, String signature) throws InterruptedException, IOException {
        // Acquire a lock to ensure thread safety
        lock.lock();
        try {
            long currentTime = System.currentTimeMillis();

            // Check if the time interval has passed since the last reset
            if (currentTime - lastResetTime >= timeIntervalMillis) {
                // Reset the request count and update the last reset time
                requestCount = 0;
                lastResetTime = currentTime;
            }

            // Check if the request count exceeds the limit
            if (requestCount >= requestLimit) {
                long sleepTime = lastResetTime + timeIntervalMillis - currentTime;
                if (sleepTime > 0) {
                    // Sleep to wait until the time interval elapses
                    Thread.sleep(sleepTime);
                }
                // Reset the request count and update the last reset time
                requestCount = 0;
                lastResetTime = System.currentTimeMillis();
            }

            final Content postResultForm = Request.Post("https://reqres.in/api/users")
                    .bodyString("{\"name\": \"morpheus\",\"job\":\"leader\"}", ContentType.APPLICATION_JSON)
                    .execute().returnContent();
            System.out.println(postResultForm.asString());

            // Increment the request count
            requestCount++;
        } finally {
            // Release the lock
            lock.unlock();
        }
    }

    // Inner class to represent the document
    private static class Document {
        // Implement the document structure as needed
    }

    public static void main(String[] args) {
        CrptApi api = new CrptApi(TimeUnit.MINUTES, 5);

        try {
            // Create a document and signature
            Document document = new Document();
            String signature = "sampleSignature";

            // Call the createDocument method
            api.createDocument(document, signature);
        } catch (InterruptedException e) {
            e.printStackTrace();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
