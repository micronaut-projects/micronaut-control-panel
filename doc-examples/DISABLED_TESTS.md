# Python Docs Disabled Test Inventory

This file tracks Python docs examples of Micronaut Control Panel (`doc-examples/example-python`) that are present but
disabled, or that deviate from the Java example because the direct port currently fails compilation or at runtime
(Python compiler gaps). Use it as the bug-fixing task list for the final migration wave.

## Reconciliation

- Last generated active `@Disabled` count: 0.
- Last generated command: `rg -n "@Disabled\(" doc-examples/example-python/src`.
- Last full-suite command: `./gradlew :micronaut-doc-examples:micronaut-example-python:test -Ppython-ci`.
- Last full-suite result: build successful, 1 test executed (1 test class), 0 skipped.

## Migration Rules

- Python classes cannot extend Java classes: `MyApplicationControlPanel` implements the `ControlPanel[Body]` interface
  directly (the Java, Kotlin and Groovy examples extend `AbstractControlPanel`) and delegates the title, icon, order
  and enabled flag to the injected `ControlPanelConfiguration`; the guide carries a `[.lang-python]` note.
- Methods overriding a *default* method of the Java interface (`getOrder`, `getCategory`) need `@Executable` to be
  bridged to Java; without it the Java view of the bean uses the interface defaults (order 0, `Category.MAIN`) while the
  Python object still answers the overridden values. The test asserts both views through `getBeansOfType`.
- Imported classes work as runtime type arguments of `getBean(ControlPanelConfiguration, qualifier)`, but not of
  `getBeansOfType(...)`: the imported `ControlPanel` is matched against the `getBeansOfType(Argument)` overload and fails
  with `UnsupportedOperationException: Unsupported operation identifier 'typeHashCode' ... type: _MicronautJavaType`, so
  the test uses a `java.type("io.micronaut.controlpanel.core.ControlPanel")` alias there (`# TODO(python)`).
- The nested Java record `ControlPanel.Category` is imported as `from micronaut.controlpanel.core.ControlPanel import
  Category` and constructed directly.
- `Body` is a `@ReflectiveAccess @dataclass` at module level (nested classes are not used in the Python port).
- The example project only holds the documented control panel and its test (the full `example-java` application with
  Kafka, object storage, Hibernate and Playwright end-to-end tests is not duplicated); the documented control panel
  lives in `src/main/python` and the test in `src/test/python`, merged into one compilation (`mergePythonSources`).
- This branch builds on Micronaut core 5.1 while the Python compiler and runtime ship with core 5.2+: only
  `example-python` resolves the Micronaut 5.2 core BOM (`micronaut-python` in `gradle/libs.versions.toml`,
  `micronautBuild.python.compilerVersion`).

## Active `@Disabled` Tests

None.

## Commented Unsupported Snippet Ports

None.

## Intentionally Unsupported Snippet Targets

None.
