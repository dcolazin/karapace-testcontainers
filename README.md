karapace-testcontainers
======================

A [Testcontainers 2](https://www.testcontainers.org/) module for running [Karapace](https://www.karapace.io/) clusters.

## Dependency management

### Maven
```xml
<dependency>
    <groupId>org.vetronauta</groupId>
    <artifactId>karapace-testcontainers</artifactId>
    <version>0.1.0</version>
    <scope>test</scope>
</dependency>
```

## Usage

The simpler example is as follows. By default, it will use Karapace 5.0.3 and Kafka 3.9.1.

```java
KarapaceContainer karapaceContainer = KarapaceContainer.builder().build();
karapaceContainer.start();
```

It is possible to specify the Karapace image through the builder. Moreover, it is possible to avoid the verification of the image compatibility. 

```java
KarapaceContainer karapaceContainer = KarapaceContainer.builder()
    .karapaceImageName(GHCR_IMAGE_NAME.withTag("5.0.2"))
    .build();
karapaceContainer.start();
```

```java
KarapaceContainer customKarapaceContainer = KarapaceContainer.builder()
    .karapaceImageName(DockerImageName.parse("my-company/karapace"))
    .assertCompatible(false)
    .build();
customKarapaceContainer.start();
```
By default, the Kafka container lifecycle will follow the Karapace container lifecycle (it starts before and stops after Karapace). 
There might be the need to use a specific Kafka version or to test a Karapace cluster.
It is possible to supply a Kafka container to use, but its lifecycle must be handled separately. 

```java
KafkaContainer kafka4Container = new KafkaContainer("apache/kafka:4.1.1");
KarapaceContainer karapaceContainer = KarapaceContainer.builder()
    .kafkaContainer(kafka4Container)
    .build();
karapaceContainer.start(); //will also start kafkaContainer if not yet running
/* ... */
karapaceContainer.stop();
kafka4Container.stop();
```

```java
KafkaContainer kafkaContainer = defaultKafkaContainer();
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
kafkaContainer.start();
masterContainer.start();
follower1Container.start();
follower2Container.start();
```