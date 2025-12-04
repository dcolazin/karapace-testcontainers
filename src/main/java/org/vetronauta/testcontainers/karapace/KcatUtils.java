package org.vetronauta.testcontainers.karapace;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class KcatUtils {

    public static void logListeners(Network network, String broker) {
        GenericContainer<?> kcat = new GenericContainer<>("edenhill/kcat:1.7.1")
            .withNetwork(network)
            .withCommand(String.format("-b %s -L", broker));
        kcat.start();
        while (kcat.isRunning()) {}
        System.out.println(kcat.getLogs()); //TODO logging
    }

}
