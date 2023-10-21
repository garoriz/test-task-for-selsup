package org.example;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.http.HttpEntity;
import org.apache.http.client.fluent.Request;
import org.apache.http.entity.ContentType;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class CrptApi {
    private String URI = "https://ismp.crpt.ru/api/v3/lk/documents/commissioning/contract/create";
    private final int requestLimit;
    private final long timeIntervalMillis;
    private long lastResetTime;
    private int requestCount;
    private final Lock lock;
    ObjectMapper mapper = new ObjectMapper();


    public CrptApi(TimeUnit timeUnit, int requestLimit) {
        this.requestLimit = requestLimit;
        this.timeIntervalMillis = timeUnit.toMillis(1);
        this.lastResetTime = System.currentTimeMillis();
        this.requestCount = 0;
        this.lock = new ReentrantLock();
    }

    public DocumentIdentifier createDocument(String pg, Document document, String signature) throws InterruptedException {
        lock.lock();
        try {
            long currentTime = System.currentTimeMillis();

            if (currentTime - lastResetTime >= timeIntervalMillis) {
                requestCount = 0;
                lastResetTime = currentTime;
            }

            if (requestCount >= requestLimit) {
                long sleepTime = lastResetTime + timeIntervalMillis - currentTime;
                if (sleepTime > 0) {
                    Thread.sleep(sleepTime);
                }
                requestCount = 0;
                lastResetTime = System.currentTimeMillis();
            }

            Map<String, String> map = new HashMap<>();
            map.put("pg", pg);
            URI = addParams(map);

            document.setSignature(signature);
            String jsonDocument = mapper.writeValueAsString(document);

            final HttpEntity postResultForm = Request.Post(URI)
                    .bodyString(jsonDocument, ContentType.APPLICATION_JSON)
                    .execute().returnResponse().getEntity();
            String entityString = new String(postResultForm.getContent().readAllBytes(), StandardCharsets.UTF_8);
            DocumentIdentifier documentIdentifier = mapper.readValue(
                    entityString,
                    DocumentIdentifier.class
            );

            requestCount++;
            return documentIdentifier;
        } catch (IOException e) {
            throw new RuntimeException(e);
        } finally {
            lock.unlock();
        }
    }

    private String addParams(Map<String, String> params) {
        URI += "?";
        for (Map.Entry<String, String> param : params.entrySet()) {
            URI += param.getKey() + "=" + param.getValue() + "&";
        }
        return URI;
    }

    private static class Document {
        @JsonProperty("document_format")
        private DocumentFormat documentFormat;
        @JsonProperty("product_document")
        private String productDocument;
        @JsonProperty("product_group")
        private String productGroup;
        private String signature;

        public Document(DocumentFormat documentFormat, String productDocument, String productGroup) {
            this.documentFormat = documentFormat;
            this.productDocument = productDocument;
            this.productGroup = productGroup;
        }

        public DocumentFormat getDocumentFormat() {
            return documentFormat;
        }

        public String getProductDocument() {
            return productDocument;
        }

        public String getProductGroup() {
            return productGroup;
        }

        public String getSignature() {
            return signature;
        }

        public void setSignature(String signature) {
            this.signature = signature;
        }
    }

    public enum DocumentFormat {

        MANUAL,
        XML,
        CSV
    }

    private static class DocumentIdentifier {
        private String value;
        private String timestamp;
        private String code;
        private String error;
        private String message;
        private String path;

        public void setValue(String value) {
            this.value = value;
        }

        public void setCode(String code) {
            this.code = code;
        }

        public void setTimestamp(String timestamp) {
            this.timestamp = timestamp;
        }

        public void setError(String error) {
            this.error = error;
        }

        public void setMessage(String message) {
            this.message = message;
        }

        public void setPath(String path) {
            this.path = path;
        }
    }

    public static void main(String[] args) {
        CrptApi api = new CrptApi(TimeUnit.MINUTES, 5);

        try {
            Document document = new Document(
                    DocumentFormat.MANUAL,
                    "productDocument",
                    "productGroup"
            );
            String signature = "sampleSignature";

            DocumentIdentifier documentIdentifier = api.createDocument("milk", document, signature);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
}
