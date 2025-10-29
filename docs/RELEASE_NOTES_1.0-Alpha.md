# Flylang 1.0-Alpha — Release Notes

Date: 2025-10-29

## Overview
Flylang 1.0-Alpha delivers a stable async execution model, verified example suite, and polished tooling (CLI, REPL, LSP, Maven plugin), with all modules versioned to 1.0-Alpha.

## Highlights
- Reliable async/timeout codegen for primitives (no boxing surprises)
- JVM verifier-safe lambdas (receiver casts inserted where needed)
- End-to-end example suite builds and runs cleanly
- Spring Boot integration guide finalized; demo app works out of the box
- Tooling refreshed: CLI, REPL, LSP, Maven plugin, and editor plugin docs/scripts

## What’s New
### Compiler and Codegen
- Async and timeout blocks now correctly unbox primitive return types.
  - Explicit casts and wrapper unboxing calls (e.g., `intValue()`, `booleanValue()`) are inserted when returning primitives from Object-typed async expressions.
- Added CHECKCAST on lambda self receivers in call sites to satisfy JVM verifier in nested/closure scenarios.
- Safer capture of in-scope locals for async/timeout closures; prior hoisting workarounds are no longer required.

### Runtime
- Futures and timeout scheduling continue to use daemon executors to avoid JVM shutdown/blocked lifecycle issues.

### Tooling
- Global version bump to 1.0-Alpha across compiler, runtime, stdlib, CLI, REPL, LSP, Maven plugin, and examples.
- Maven plugin coordinates: `com.firefly:firefly-maven-plugin:1.0-Alpha`.
- Editor integrations (VS Code/IntelliJ) documentation and install scripts updated to 1.0-Alpha.

### Documentation
- Root README updated with verified examples and quick commands.
- Spring Boot guides updated and validated:
  - docs/SPRING_BOOT_GUIDE.md (usage)
  - docs/SPRING_BOOT_IMPLEMENTATION_NOTES.md, SPRING_BOOT_INTEGRATION_COMPLETE.md (internals and verification)

## Verified Examples (build and run)
- hello-world — basics
- async-demo — async fn, Future#get()
- concurrency-demo — concurrent bindings, race, timeout
- futures-combinators-demo — Future::all/any/timeout
- patterns-demo — tuple/struct pattern matching
- data-patterns-demo — ADTs and pattern matching
- java-interop-advanced — collections, time API, static calls
- sparks-demo — immutable smart records
- async-pipeline-demo — parallel fan-out/fan-in with timeout
- spring-boot-demo — REST endpoints, JSON (GET/POST, path/query/body)

## Breaking Changes / Migration Notes
- Remove any local workarounds for async primitive returns and timeout blocks; compiler now unboxes/returns primitives correctly.
- Keep using strict `::` static/instance call syntax.

## Known Issues (with workarounds)
- Parser quirk: multi-arg static calls with `Application.class` may fail to parse.
  - Workaround: pass a single argument or bind to a local (e.g., `let app = Application.class; SpringApplication::run(app);`).
- Generic collections in REST endpoints may require explicit types/casts in some contexts.

## Install / Upgrade
- Build from source:
  - `mvn -q -DskipTests clean install`
- Use Maven plugin in your project:
  - ```xml
    <plugin>
      <groupId>com.firefly</groupId>
      <artifactId>firefly-maven-plugin</artifactId>
      <version>1.0-Alpha</version>
      <executions>
        <execution>
          <goals><goal>compile</goal></goals>
        </execution>
      </executions>
    </plugin>
    ```
- Run verified examples: `bash scripts/smoke-test-examples.sh`

## Credits
Thanks to everyone who contributed testing, fixes, and docs toward the 1.0-Alpha milestone.
