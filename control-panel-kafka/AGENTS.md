# control-panel-kafka Agent Guidance

This module owns the Kafka Streams control panel that renders configured stream topologies as Mermaid diagrams.

## Where To Work

- Panel implementation: `src/main/java/io/micronaut/controlpanel/panels/kafka/KafkaStreamsControlPanel.java`
- Module defaults: `src/main/resources/micronaut-control-panel`
- Templates: `src/main/resources/views/kafka-streams` and `src/main/resources/views/health`
- Tests: `src/test/java/io/micronaut/controlpanel/panels/kafka`
- User-guide page: `src/main/docs/guide/controlPanels/kafka.adoc`

## Rules

- Keep the panel conditional on `ConfiguredStreamBuilder` beans and Kafka Streams availability.
- Preserve one panel per configured stream builder.
- Keep Mermaid output deterministic by sorting sub-topologies, nodes, and edges where possible.
- Test labels and graph relationships rather than relying on generated internal node IDs.
- Keep Kafka dependencies optional for main code and test-scoped where they are only needed for verification.

## Verification

- Targeted tests: `./gradlew :micronaut-control-panel-kafka:test`
- Run `./gradlew publishGuide` when changing the Kafka guide page or navigation.
