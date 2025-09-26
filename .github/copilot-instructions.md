# Copilot Instructions for Micronaut Control Panel

## Project Overview

This is the **Micronaut Control Panel** module, which provides a web UI that allows you to view and manage the state of your Micronaut application, typically in a development environment. This is a multi-module Gradle project that is part of the Micronaut Framework ecosystem.

**Key Points:**
- This is a **library module**, not an application - modules are designed to be used as dependencies
- The root project contains no code and serves as a parent project for coordination and documentation
- Project follows standard Micronaut conventions and Gradle best practices

## Project Structure

```
├── micronaut-control-panel-bom/          # Bill of Materials for dependency management
├── micronaut-control-panel-core/         # Core control panel functionality
├── micronaut-control-panel-management/   # Management endpoint integrations
├── micronaut-control-panel-ui/           # Web UI components and assets
├── micronaut-doc-examples/               # Documentation examples and demos
├── buildSrc/                             # Gradle convention plugins
├── src/main/docs/                        # AsciiDoc documentation
└── .clinerules/                          # Comprehensive development guidelines
```

## Development Guidelines

### 🔧 Build and Test Commands

**Essential Commands:**
```bash
# Run tests for all modules
./gradlew test

# Run tests for specific module  
./gradlew :micronaut-control-panel-core:test

# Check code style and formatting
./gradlew spotlessCheck

# Apply code formatting  
./gradlew spotlessApply

# Run all checks (tests + linting)
./gradlew check

# Build documentation
./gradlew docs
```

### 📋 Required Development Workflow

**ALWAYS follow this sequence when making code changes:**

1. **Compile affected modules first:**
   ```bash
   ./gradlew -q :<module>:compileTestJava :<module>:compileTestGroovy
   ```

2. **Run targeted tests for quick feedback:**
   ```bash
   ./gradlew :micronaut-control-panel-core:test --tests 'pkg.ClassTest'
   ```

3. **Run full tests for all affected modules:**
   ```bash
   ./gradlew :micronaut-control-panel-core:test
   ```

4. **Check code style:**
   ```bash
   ./gradlew -q spotlessCheck
   ```

5. **Apply formatting if needed (new files only):**
   ```bash
   ./gradlew -q spotlessApply
   ```

6. **Verify clean working tree:**
   ```bash
   git status
   ```

### ⚠️ Critical Requirements

**You MUST confirm ALL of these before completing any task:**
- ✅ Changes compile successfully (affected modules)
- ✅ Targeted tests pass
- ✅ Full tests for affected modules pass  
- ✅ Spotless formatting check passes
- ✅ Documentation updated when necessary
- ✅ Working tree is clean (no unrelated changes)

**If ANY item fails, DO NOT mark the task as complete.**

### 🏗️ Build System Guidelines

- **Convention over Configuration:** Use convention plugins in `buildSrc/` directory
- **NO custom build logic** directly in `build.gradle(.kts)` files
- **Avoid duplication** by creating reusable convention plugins
- **Prefer composition** of convention plugins

### 📚 Documentation Standards

- Documentation is written in **AsciiDoc** format in `src/main/docs/guide/`
- Follow the existing `toc.yml` structure for table of contents
- Include practical, runnable examples from `doc-examples/` directory
- Build docs with `./gradlew docs` and verify output in `build/docs/`

### 🎯 Code Quality Standards

- Follow **Micronaut Framework** coding conventions
- Use **existing libraries** whenever possible - avoid adding new dependencies unless absolutely necessary
- Ensure **GraalVM compatibility** for all code
- Focus on **annotation-driven configuration** and **modular design**
- Write **minimal, surgical changes** - change as few lines as possible

### 🔍 Key Files and References

- **Comprehensive development guide:** `.clinerules/coding.md` - Contains detailed Micronaut-specific guidelines
- **Documentation guidelines:** `.clinerules/docs.md` - AsciiDoc and documentation standards  
- **Project description:** `gradle.properties` - Contains `projectDesc` key with module description
- **License template:** `config/spotless.license.java` - Standard Apache 2.0 license header

### 💡 Best Practices

1. **Understand before changing** - Analyze existing code patterns and follow them
2. **Test early and often** - Run tests after every significant change
3. **Keep changes minimal** - Only modify what's necessary to fix the issue
4. **Document when needed** - Update AsciiDoc docs for user-facing changes
5. **Follow Micronaut patterns** - Use factories, interceptors, and annotation processors appropriately

## Module-Specific Notes

- **micronaut-control-panel-core:** Core abstractions and interfaces
- **micronaut-control-panel-management:** Integration with Micronaut Management endpoints
- **micronaut-control-panel-ui:** Web UI components, likely contains static assets and templates
- **micronaut-control-panel-bom:** Dependency management - defines versions for all modules

When working on any module, ensure your changes are consistent with Micronaut Framework conventions and maintain backward compatibility.