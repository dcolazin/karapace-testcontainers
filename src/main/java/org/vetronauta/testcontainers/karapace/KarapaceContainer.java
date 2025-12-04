package org.vetronauta.testcontainers.karapace;

import lombok.NonNull;
import lombok.Setter;
import lombok.SneakyThrows;
import lombok.experimental.Accessors;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.redpanda.RedpandaContainer;
import org.testcontainers.utility.DockerImageName;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Testcontainers implementation of Karapace.
 * <p>
 * Supported images: {@code ghcr.io/aiven-open/karapace}
 * <p>
 * Exposed ports: 8081
 */
public class KarapaceContainer extends GenericContainer<KarapaceContainer> {

    public static final DockerImageName GHCR_IMAGE_NAME = DockerImageName
        .parse("aiven-open/karapace")
        .withRegistry("ghcr.io");
    public static final DockerImageName DEFAULT_IMAGE_NAME = GHCR_IMAGE_NAME.withTag("5.0.3");
    public static final int ORIGINAL_EXPOSED_PORT = 8081;
    public static final String DEFAULT_REGISTRY_NAME = "karapace-schema-registry";

    private static final Map<String, String> ENV_MAP;

    private final boolean shouldTearDownStorage;
    private final GenericContainer<?> storageContainer;
    private final int waitBeforeStartMillis;
    private final int waitAfterStartMillis;

    static {
        Map<String, String> envMap = new HashMap<>();
        envMap.put("KARAPACE_KARAPACE_REGISTRY", "true");
        envMap.put("KARAPACE_HOST", "0.0.0.0");
        envMap.put("KARAPACE_LOG_LEVEL", "INFO");
        envMap.put("KARAPACE_COMPATIBILITY", "FULL");
        envMap.put("KARAPACE_GROUP_ID", DEFAULT_REGISTRY_NAME);
        ENV_MAP = Collections.unmodifiableMap(envMap);
    }

    private KarapaceContainer(Builder builder) {
        super(builder.karapaceImageName);
        if (builder.assertCompatible) {
            builder.karapaceImageName.assertCompatibleWith(GHCR_IMAGE_NAME);
        }
        if (builder.storageDefinition != null) {
            this.storageContainer = builder.storageDefinition.storage();
            this.shouldTearDownStorage = builder.storageDefinition.shouldBeManaged();
            this.waitBeforeStartMillis = builder.storageDefinition.waitBeforeStart();
            this.waitAfterStartMillis = builder.storageDefinition.waitAfterStart();
            addEnv("KARAPACE_BOOTSTRAP_URI", builder.storageDefinition.bootstrapUri());
        } else {
            //3.9.0 is not the best to use https://issues.apache.org/jira/browse/KAFKA-18281
            this.storageContainer = defaultKafkaContainer();
            this.shouldTearDownStorage = true;
            this.waitBeforeStartMillis = 1000;
            this.waitAfterStartMillis = 0;
            addEnv("KARAPACE_BOOTSTRAP_URI", "kafka:9093");
        }
        addExposedPort(ORIGINAL_EXPOSED_PORT);
        dependsOn(this.storageContainer);
        setNetwork(this.storageContainer.getNetwork());
        setCommand("python3 -m karapace");
        if (builder.expectedMaster) {
            setWaitStrategy(Wait.forLogMessage(".*Ready in \\d+\\.\\d+ seconds.*", 2));
        } else {
            setWaitStrategy(Wait.forHttp("/_health"));
        }
        ENV_MAP.forEach(this::addEnv);
        addEnv("KARAPACE_MASTER_ELECTION_STRATEGY", builder.electionStrategy.getPropertyValue());
        addEnv("KARAPACE_ADVERTISED_HOSTNAME", builder.advertisedName);
        addEnv("KARAPACE_CLIENT_ID", builder.advertisedName);
        addEnv("KARAPACE_TAGS__APP", builder.advertisedName);
        withNetworkAliases(builder.advertisedName);
    }

    @Override
    public void start() {
        if (!storageContainer.isRunning()) {
            storageContainer.start();
        }
        if (waitBeforeStartMillis > 0) {
            sneakyWait(waitBeforeStartMillis);
        }
        super.start();
        if (waitAfterStartMillis > 0) {
            sneakyWait(waitAfterStartMillis);
        }
    }

    @SneakyThrows
    private static void sneakyWait(long millis) {
        Thread.sleep(millis);
    }

    @Override
    public void stop() {
        super.stop();
        if (shouldTearDownStorage) {
            storageContainer.stop();
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    public static KafkaContainer defaultKafkaContainer() {
        //TODO sometimes (when?) Karapace is not able to actually start and logs repeatedly; is this an issue with Kafka wait strategy?
        // karapace.core.schema_reader	schema-reader	WARNING 	Topic does not yet exist.
        return StorageDefinition.KafkaStorageDefinition.defaultKafkaContainer(DockerImageName.parse("apache/kafka:3.9.1"));
    }

    @Setter
    @Accessors(fluent = true)
    public static class Builder {
        @NonNull String advertisedName = DEFAULT_REGISTRY_NAME;
        @NonNull DockerImageName karapaceImageName = DEFAULT_IMAGE_NAME;

        StorageDefinition<? extends GenericContainer<?>> storageDefinition;

        boolean assertCompatible = true;
        boolean expectedMaster = true;
        @NonNull ElectionStrategy electionStrategy = ElectionStrategy.HIGHEST;

        public Builder kafkaImage(DockerImageName kafkaImage) {
            this.storageDefinition = new StorageDefinition.KafkaStorageDefinition(kafkaImage);
            return this;
        }

        public Builder kafkaContainer(KafkaContainer kafkaContainer) {
            this.storageDefinition = new StorageDefinition.KafkaStorageDefinition(kafkaContainer);
            return this;
        }

        public Builder redpandaImage(DockerImageName redpandaImage) {
            this.storageDefinition = new StorageDefinition.RedpandaStorageDefinition(redpandaImage);
            return this;
        }

        public Builder redpandaContainer(RedpandaContainer redpandaContainer) {
            this.storageDefinition = new StorageDefinition.RedpandaStorageDefinition(redpandaContainer);
            return this;
        }

        public KarapaceContainer build() {
            return new KarapaceContainer(this);
        }

    }

}
