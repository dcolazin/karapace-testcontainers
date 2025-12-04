package org.vetronauta.testcontainers.karapace;

import org.testcontainers.UnstableAPI;
import org.testcontainers.containers.Container;
import org.testcontainers.containers.Network;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.redpanda.RedpandaContainer;
import org.testcontainers.utility.DockerImageName;

public abstract class StorageDefinition<T extends Container<T>> {

    private final DockerImageName imageName;
    private final T container;

    protected StorageDefinition(DockerImageName imageName) {
        this.imageName = imageName;
        this.container = null;
    }

    protected StorageDefinition(T container) {
        this.imageName = null;
        this.container = container;
    }

    protected abstract T defineFromImage(DockerImageName imageName);
    protected abstract String bootstrapUri();

    /**
     * Temporary hack to fix certain unreadiness issues with Kafka/Karapace
     * @return millis to wait after starting Kafka, but before starting Karapace
     */
    @UnstableAPI
    protected abstract int waitBeforeStart();
    /**
     * Temporary hack to fix certain unreadiness issues with Redpanda/Karapace
     * @return millis to wait after starting Karapace, before doing any requests
     */
    @UnstableAPI
    protected abstract int waitAfterStart();

    public T storage() {
        if (container != null) {
            return container;
        }
        return defineFromImage(imageName);
    }

    public boolean shouldBeManaged() {
        return container == null;
    }

    public static class KafkaStorageDefinition extends StorageDefinition<KafkaContainer> {

        public static KafkaContainer defaultKafkaContainer(DockerImageName kafkaImage) {
            return new KafkaContainer(kafkaImage)
                .withNetworkAliases("kafka")
                .withNetwork(Network.builder().driver("bridge").build());
        }

        public KafkaStorageDefinition(DockerImageName imageName) {
            super(imageName);
        }

        public KafkaStorageDefinition(KafkaContainer container) {
            super(container);
        }

        @Override
        protected KafkaContainer defineFromImage(DockerImageName imageName) {
            return defaultKafkaContainer(imageName);
        }

        @Override
        protected String bootstrapUri() {
            return "kafka:9093";
        }

        @Override
        protected int waitBeforeStart() {
            return 1000;
        }

        @Override
        protected int waitAfterStart() {
            return 0;
        }

    }

    public static class RedpandaStorageDefinition extends StorageDefinition<RedpandaContainer> {

        public static RedpandaContainer defaultRedpandaContainer(DockerImageName redpandaImage) {
            return new RedpandaContainer(redpandaImage)
                .withListener("kafka:39093")
                .withNetworkAliases("kafka")
                .withNetwork(Network.builder().driver("bridge").build());
        }

        public RedpandaStorageDefinition(DockerImageName imageName) {
            super(imageName);
        }

        public RedpandaStorageDefinition(RedpandaContainer container) {
            super(container);
        }

        @Override
        protected RedpandaContainer defineFromImage(DockerImageName imageName) {
            return defaultRedpandaContainer(imageName);
        }

        @Override
        protected String bootstrapUri() {
            return "kafka:39093";
        }

        @Override
        protected int waitBeforeStart() {
            return 0;
        }

        @Override
        protected int waitAfterStart() {
            return 5000;
        }

    }

}
