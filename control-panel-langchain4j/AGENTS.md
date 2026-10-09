# control-panel-langchain4j Agent Guidance

This module owns the LangChain4j control panel: it lists the AI models, embedding stores, AI services and tools of an application that uses Micronaut LangChain4j, and offers a chat with its chat models.

## Where To Work

- Panel implementation: `src/main/java/io/micronaut/controlpanel/panels/langchain4j/LangChain4jControlPanel.java`
- Chat endpoint: `src/main/java/io/micronaut/controlpanel/panels/langchain4j/LangChain4jControlPanelController.java`
- Module defaults: `src/main/resources/micronaut-control-panel`
- Templates: `src/main/resources/views/langchain4j`
- Tests: `src/test/java/io/micronaut/controlpanel/panels/langchain4j`
- User-guide page: `src/main/docs/guide/controlPanels/langchain4j.adoc`

## Rules

- Keep Micronaut LangChain4j a `compileOnly` dependency and guard the beans with `@Requires(classes = ...)`, so that the application chooses the Micronaut LangChain4j version.
- Only use the Micronaut LangChain4j API of the released version declared in the version catalog.
- Keep the chat behind `ControlPanelWriteAccessEvaluator`: calling a model costs tokens.
- Test with scripted `ChatModel` beans, never with a real model.

## Verification

- Targeted tests: `./gradlew :micronaut-control-panel-langchain4j:test`
- Run `./gradlew publishGuide` when changing the LangChain4j guide page or navigation.
