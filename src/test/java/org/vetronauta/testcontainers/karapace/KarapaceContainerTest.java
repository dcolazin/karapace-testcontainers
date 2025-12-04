package org.vetronauta.testcontainers.karapace;

import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.utility.DockerImageName;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.vetronauta.testcontainers.karapace.KarapaceContainer.defaultKafkaContainer;

public class KarapaceContainerTest {
    private static final String CONTENT_TYPE_HEADER = "Content-Type";
    private static final String SCHEMA_REGISTRY_CONTENT_TYPE = "application/vnd.schemaregistry.v1+json";

    @Test
    void startStopKafka3Test() throws URISyntaxException, IOException, InterruptedException {
        try (KarapaceContainer karapaceContainer = KarapaceContainer.builder().build();
             HttpClient client = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build()) {

            karapaceContainer.start();
            int port = karapaceContainer.getMappedPort(KarapaceContainer.ORIGINAL_EXPOSED_PORT);
            String baseUri = String.format("http://%s:%s", karapaceContainer.getHost(), port);
            performRequests(baseUri, client);
            karapaceContainer.stop();
        }
    }

    @Test
    void startStopKafka4Test() throws URISyntaxException, IOException, InterruptedException {
        try (KarapaceContainer karapaceContainer = KarapaceContainer.builder()
                .kafkaImage(DockerImageName.parse("apache/kafka:4.1.1")).build();
             HttpClient client = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build()) {

            karapaceContainer.start();
            int port = karapaceContainer.getMappedPort(KarapaceContainer.ORIGINAL_EXPOSED_PORT);
            String baseUri = String.format("http://%s:%s", karapaceContainer.getHost(), port);
            performRequests(baseUri, client);
            karapaceContainer.stop();
        }
    }

    @Test
    void startStopRedpandaTest() throws URISyntaxException, IOException, InterruptedException {
        try (KarapaceContainer karapaceContainer = KarapaceContainer.builder()
            .redpandaImage(DockerImageName.parse("redpandadata/redpanda:v25.3.1")).build();
             HttpClient client = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build()) {

            karapaceContainer.start();
            int port = karapaceContainer.getMappedPort(KarapaceContainer.ORIGINAL_EXPOSED_PORT);
            String baseUri = String.format("http://%s:%s", karapaceContainer.getHost(), port);
            performRequests(baseUri, client);
            karapaceContainer.stop();
        }
    }

    @Test
    void masterFollowerTest() throws URISyntaxException, IOException, InterruptedException {
        try(KafkaContainer kafkaContainer = defaultKafkaContainer();
            KarapaceContainer masterContainer = KarapaceContainer.builder()
                .kafkaContainer(kafkaContainer)
                .build();
            KarapaceContainer follower1Container = KarapaceContainer.builder()
                .electionStrategy(ElectionStrategy.LOWEST)
                .expectedMaster(false)
                .kafkaContainer(kafkaContainer)
                .advertisedName("karapace-schema-registry-follower1")
                .build();
            KarapaceContainer follower2Container = KarapaceContainer.builder()
                .electionStrategy(ElectionStrategy.LOWEST)
                .expectedMaster(false)
                .kafkaContainer(kafkaContainer)
                .advertisedName("karapace-schema-registry-follower2")
                .build();
            HttpClient client = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build()) {

            masterContainer.start();
            follower1Container.start();
            follower2Container.start();

            int port = follower1Container.getMappedPort(KarapaceContainer.ORIGINAL_EXPOSED_PORT);
            String baseUri = String.format("http://%s:%s", follower1Container.getHost(), port);
            performRequests(baseUri, client);

            follower2Container.stop();
            follower1Container.stop();
            masterContainer.stop();
            kafkaContainer.stop();
        }
    }

    private static void performRequests(String baseUri, HttpClient client) throws URISyntaxException, IOException, InterruptedException {
        //ensures registry is empty
        HttpRequest request = HttpRequest.newBuilder()
            .uri(new URI(baseUri + "/subjects"))
            .build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        Assert.assertEquals(response.statusCode(), 200);
        Assert.assertEquals(response.body(), "[]");

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


    }

}
