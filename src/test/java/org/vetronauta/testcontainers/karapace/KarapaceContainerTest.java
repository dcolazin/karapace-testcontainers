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

    //TODO improve tests: write/read schemas
    //TODO improve tests: multiple karapace instances
    //TODO improve tests: test multiple versions of karapace/kafka

    @Test
    void startStopTest() throws URISyntaxException, IOException, InterruptedException {
        try (KarapaceContainer karapaceContainer = KarapaceContainer.builder().build();
             HttpClient client = HttpClient.newHttpClient()) {

            karapaceContainer.start();

            int port = karapaceContainer.getMappedPort(KarapaceContainer.ORIGINAL_EXPOSED_PORT);
            String baseUri = String.format("http://%s:%s", karapaceContainer.getHost(), port);
            HttpRequest request = HttpRequest.newBuilder()
                .uri(new URI(baseUri + "/subjects"))
                .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            Assert.assertEquals(response.statusCode(), 200);
            Assert.assertEquals(response.body(), "[]");

            karapaceContainer.stop();
        }

    }

}
