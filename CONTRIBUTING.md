# Contributing Code or Documentation to Micronaut

Sign the [Contributor License Agreement (CLA)](https://cla-assistant.io/micronaut-projects/micronaut-control-panel). This is required before any of your code or pull requests are accepted.

## Finding Issues to Work on

If you are interested in contributing to Micronaut and are looking for issues to work on, take a look at the issues tagged with [help wanted](https://github.com/micronaut-projects/micronaut-control-panel/issues?q=is%3Aopen+is%3Aissue+label%3A%22status%3A+help+wanted%22).

## JDK Setup

Micronaut Control Panel currently requires JDK 25. The repository includes an `.sdkmanrc` file for SDKMAN users.

## IDE Setup

Micronaut Control Panel can be imported into IntelliJ IDEA by opening the `settings.gradle.kts` file.

## Docker Setup

Micronaut Control Panel tests use Testcontainers and Micronaut Test Resources, so Docker must be installed and running for the relevant integration and example tests.

## Running Tests

To run the full test and verification suite, use:

```
./gradlew check
```

To run a module test task, use the Gradle project name with the `micronaut-` prefix. For example:

```
./gradlew :micronaut-control-panel-core:test
```

The runnable example application includes Playwright-based end-to-end tests. Its test task installs the required Playwright browsers before running:

```
./gradlew :doc-examples:example-java:test
```

## Working on UI Assets

The UI JavaScript sources are located at `control-panel-ui/src/main/javascript`. The Gradle build runs the npm build for the UI module and packages the generated resources.

To rebuild the UI bundle directly while working on JavaScript changes, run:

```
./gradlew :micronaut-control-panel-ui:npm_run_build
```

## Building Documentation

The documentation sources are located at `src/main/docs/guide`.

To build the documentation, run `./gradlew publishGuide` (or `./gradlew pG`), then open `build/docs/index.html`.

To also build the Javadocs, run `./gradlew docs`.

## Working on the code base

If you use IntelliJ IDEA, you can import the project using the IntelliJ Gradle Tooling ("File / Import Project" and selecting the `settings.gradle.kts` file).

To get a local development version of Micronaut Control Panel working, first run the `publishToMavenLocal` task.

```
./gradlew pTML
```

You can then reference the version specified with `projectVersion` in `gradle.properties` in a test project's `build.gradle` or `pom.xml`. If you use Gradle, add the `mavenLocal` repository (Maven automatically does this):

```
repositories {
    mavenLocal()
    mavenCentral()
}
```

## Creating a pull request

Once you are satisfied with your changes:

- Commit your changes in your local branch
- Push your changes to your remote branch on GitHub
- Send us a [pull request](https://help.github.com/articles/creating-a-pull-request)

## Merging a pull request

Before we merge a PR into a module's target branch, we have to consider:

Can this PR be merged into a patch release (e.g. documentation fixes, bug fix, patch transitive dependency upgrade, breaking change due to security, GitHub actions sync, Micronaut Build Plugin upgrade)?

Should this PR be merged into the next minor version of the module? For example, a new feature, a new module, or a minor transitive dependency upgrade.

If the PR is going into the next minor version of the module, we need to release a patch version, and branch off the target branch a new branch for the current minor module's version. If the `gradle.properties`'s `projectVersion` is 3.1.2-SNAPSHOT the branch should be named 3.1.x, and we push it to GitHub. If the target branch contains only commits such as GitHub actions sync (no commits with benefits to users), we can branch off without doing a patch release.

When you merge a PR that will go into the next module's minor release:

- Update `gradle.properties`'s `githubCoreBranch` to point to the next minor branch of Micronaut Core.
- Update `gradle.properties`'s `projectVersion` to the next minor snapshot.
- Upgrade the module to the latest version of Micronaut.

## Checkstyle

We want to keep the code clean, following good practices about organization, Javadoc, and style as much as possible.

Micronaut Control Panel uses [Checkstyle](https://checkstyle.sourceforge.io/) to make sure that the code follows those standards. The configuration is defined in `config/checkstyle/checkstyle.xml`. To execute Checkstyle for a module, run:

```
./gradlew <module-name>:checkstyleMain
```

Before starting to contribute new code, we recommend that you install the IntelliJ [CheckStyle-IDEA](https://plugins.jetbrains.com/plugin/1065-checkstyle-idea) plugin and configure it to use Micronaut's checkstyle configuration file.

IntelliJ will mark in red the issues Checkstyle finds. For example:

![](https://github.com/micronaut-projects/micronaut-core/raw/master/src/main/docs/resources/img/checkstyle-issue.png)

In this case, to fix the issues, we need to:

- Add one empty line before `package` in line 16
- Add the Javadoc for the constructor in line 27
- Add a space after `if` in line 34

The plugin also adds a new tab in the bottom of the IDE to run Checkstyle and show errors and warnings. We recommend that you run the report and fix all issues before submitting a pull request.
