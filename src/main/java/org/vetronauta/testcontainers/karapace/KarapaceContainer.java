package org.vetronauta.testcontainers.karapace;

import lombok.NonNull;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.utility.DockerImageName;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class KarapaceContainer extends GenericContainer<KarapaceContainer> {

    public static final DockerImageName DEFAULT_IMAGE_NAME = DockerImageName
        .parse("aiven-open/karapace")
        .withRegistry("ghcr.io");
    public static final int ORIGINAL_EXPOSED_PORT = 8081;

    private static final Map<String, String> ENV_MAP;

    private final boolean shouldTearDownKafka;
    private final KafkaContainer kafkaContainer;

    static {
        Map<String, String> envMap = new HashMap<>();
        envMap.put("KARAPACE_KARAPACE_REGISTRY", "true");
        envMap.put("KARAPACE_ADVERTISED_HOSTNAME", "karapace-schema-registry");
        envMap.put("KARAPACE_BOOTSTRAP_URI", "kafka:9093");
        envMap.put("KARAPACE_HOST", "0.0.0.0");
        envMap.put("KARAPACE_CLIENT_ID", "karapace-schema-registry-0");
        envMap.put("KARAPACE_GROUP_ID", "karapace-schema-registry");
        envMap.put("KARAPACE_MASTER_ELECTION_STRATEGY", "highest");
        envMap.put("KARAPACE_LOG_LEVEL", "INFO");
        envMap.put("KARAPACE_COMPATIBILITY", "FULL");
        envMap.put("KARAPACE_STATSD_HOST", "statsd-exporter");
        envMap.put("KARAPACE_TAGS__APP", "karapace-schema-registry");
        ENV_MAP = Collections.unmodifiableMap(envMap);
    }

    private KarapaceContainer(Builder builder) {
        super(builder.karapaceImageName);
        if (builder.assertCompatible) {
            builder.karapaceImageName.assertCompatibleWith(DEFAULT_IMAGE_NAME);
        }
        if (builder.kafkaContainer != null) {
            this.kafkaContainer = builder.kafkaContainer;
            this.shouldTearDownKafka = false;
        } else {
            //3.9.0 is not the best to use https://issues.apache.org/jira/browse/KAFKA-18281
            this.kafkaContainer = new KafkaContainer("apache/kafka:3.9.1")
                .withNetworkAliases("kafka")
                .withNetwork(Network.builder().driver("bridge").build());
            this.shouldTearDownKafka = true;
        }
        addExposedPort(ORIGINAL_EXPOSED_PORT);
        dependsOn(this.kafkaContainer);
        setNetwork(this.kafkaContainer.getNetwork());
        setCommand("python3 -m karapace");
        setWaitStrategy(Wait.forHttp("/_health"));
        ENV_MAP.forEach(this::addEnv);
    }

    @Override
    public void start() {
        if (!kafkaContainer.isRunning()) {
            kafkaContainer.start();
        }
        super.start();
    }

    @Override
    public void stop() {
        super.stop();
        if (shouldTearDownKafka) {
            kafkaContainer.stop();
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    @Setter
    @Accessors(fluent = true)
    public static class Builder {
        @NonNull DockerImageName karapaceImageName = DEFAULT_IMAGE_NAME;
        KafkaContainer kafkaContainer;
        boolean assertCompatible = true;

        public KarapaceContainer build() {
            return new KarapaceContainer(this);
        }

    }

}
