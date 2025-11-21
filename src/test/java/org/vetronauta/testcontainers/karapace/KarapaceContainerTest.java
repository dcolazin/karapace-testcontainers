package org.vetronauta.testcontainers.karapace;

import org.testng.Assert;
import org.testng.annotations.Test;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

public class KarapaceContainerTest {
    private static final String CONTENT_TYPE_HEADER = "Content-Type";
    private static final String SCHEMA_REGISTRY_CONTENT_TYPE = "application/vnd.schemaregistry.v1+json";

    //TODO improve tests: multiple karapace instances
    //TODO improve tests: test multiple versions of karapace/kafka

    @Test
    void startStopTest() throws URISyntaxException, IOException, InterruptedException {
        try (KarapaceContainer karapaceContainer = KarapaceContainer.builder().build();
             HttpClient client = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build()) {

            karapaceContainer.start();

            int port = karapaceContainer.getMappedPort(KarapaceContainer.ORIGINAL_EXPOSED_PORT);
            String baseUri = String.format("http://%s:%s", karapaceContainer.getHost(), port);

            //ensures registry is empty
            HttpRequest request = HttpRequest.newBuilder()
                .uri(new URI(baseUri + "/subjects"))
                .build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            Assert.assertEquals(response.statusCode(), 200);
            Assert.assertEquals(response.body(), "[]");

            Thread.sleep(6000); //TODO this should be in the wait strategy; there are issues for newly elected masters

            //register avro schema
            request = HttpRequest.newBuilder()
                .uri(new URI(baseUri + "/subjects/test-key/versions"))
                .header(CONTENT_TYPE_HEADER, SCHEMA_REGISTRY_CONTENT_TYPE)
                .POST(HttpRequest.BodyPublishers.ofString("{\"schema\": \"{\\\"type\\\": \\\"record\\\", \\\"name\\\": \\\"Obj\\\", \\\"fields\\\":[{\\\"name\\\": \\\"age\\\", \\\"type\\\": \\\"int\\\"}]}\"}"))
                .build();
            response = client.send(request, HttpResponse.BodyHandlers.ofString());
            Assert.assertEquals(response.statusCode(), 200);
            Assert.assertEquals(response.body(), "{\"id\":1}");

            //register json schema
            request = HttpRequest.newBuilder()
                .uri(new URI(baseUri + "/subjects/test-key-json-schema/versions"))
                .header(CONTENT_TYPE_HEADER, SCHEMA_REGISTRY_CONTENT_TYPE)
                .POST(HttpRequest.BodyPublishers.ofString("{\"schemaType\": \"JSON\", \"schema\": \"{\\\"type\\\": \\\"object\\\",\\\"properties\\\":{\\\"age\\\":{\\\"type\\\": \\\"number\\\"}},\\\"additionalProperties\\\":true}\"}"))
                .build();
            response = client.send(request, HttpResponse.BodyHandlers.ofString());
            Assert.assertEquals(response.statusCode(), 200);
            Assert.assertEquals(response.body(), "{\"id\":2}");

            //list subjects
            request = HttpRequest.newBuilder()
                .uri(new URI(baseUri + "/subjects"))
                .build();
            response = client.send(request, HttpResponse.BodyHandlers.ofString());
            Assert.assertEquals(response.statusCode(), 200);
            Assert.assertEquals(response.body(), "[\"test-key\",\"test-key-json-schema\"]");

            //list versions
            request = HttpRequest.newBuilder()
                .uri(new URI(baseUri + "/subjects/test-key/versions"))
                .build();
            response = client.send(request, HttpResponse.BodyHandlers.ofString());
            Assert.assertEquals(response.statusCode(), 200);
            Assert.assertEquals(response.body(), "[1]");

            //fetch schema
            request = HttpRequest.newBuilder()
                .uri(new URI(baseUri + "/schemas/ids/1"))
                .build();
            response = client.send(request, HttpResponse.BodyHandlers.ofString());
            Assert.assertEquals(response.statusCode(), 200);
            Assert.assertEquals(response.body(), "{\"schema\":\"{\\\"fields\\\":[{\\\"name\\\":\\\"age\\\",\\\"type\\\":\\\"int\\\"}],\\\"name\\\":\\\"Obj\\\",\\\"type\\\":\\\"record\\\"}\"}");

            karapaceContainer.stop();
        }

    }

}
