# Architecture — Compiler, Runtime, and Tooling

A deeper look at Flylang’s internals and how pieces fit together.

---

## Overview
- Front-end: Lexer/Parser → AST → Semantic Analysis
- Back-end: Bytecode generation (ASM) → Class files
- Runtime: Futures, scheduler, timeouts
- Tooling: CLI, Maven Plugin, LSP, Editor plugins

### Big picture (ASCII diagram)
```
          Source (.fly)
               |
         +-----+-----+
         |  Parser   |  (ANTLR)
         +-----+-----+
               |
            AST (typed)
               |
     +---------+---------+
     |  Semantic Analysis |
     +---------+---------+
               |
         +-----+-----+
         |  Codegen  |  (ASM)
         +-----+-----+
               |
           .class files
               |
        +------+------+
        |   Runtime   |  (Futures, scheduler)
        +------+------+
               |
        +------+------+
        |   Tooling   |  (CLI, LSP, IDEs)
        +-------------+
```

## Front‑end pipeline
- Parser: grammar produces a concrete parse tree; errors are surfaced early with line/column info.
- AST Builder: converts the parse tree into a compact, strongly‑typed AST.
- Semantic Analysis: resolves types and symbols, validates arity/visibility, and prepares metadata for codegen.

## Bytecode generation
- Uses ASM for direct control over descriptors and stack discipline.
- Enforces explicit dispatch via `::` — encoded as INVOKESTATIC/INVOKEVIRTUAL appropriately.
- Lambdas: generated with `invokedynamic` for async/timeout closures; in‑scope locals are captured safely (including primitives).
- JVM verifier safety: receiver `CHECKCAST` inserted at call sites when needed (notably inside lambdas).

### Async and timeout details
- `async fn` emits a helper and a closure fed to `Future.async(...)`.
- `timeout(ms){ ... }` desugars to a closure passed to `Future.timeout(...)`.
- Primitive returns are explicitly unboxed (e.g., `intValue()`, `booleanValue()`) prior to return when the expression is `Object`‑typed, removing earlier workarounds.

## Runtime
- Futures with combinators: `all`, `any`, `timeout` built over an executor/scheduler.
- Timeouts use daemon threads to prevent JVM hang on shutdown for servers/REPL.
- Designed to be small: straightforward to debug with standard tools (`jstack`, profilers).

## Tooling
- CLI: compile single files or Maven projects; `fly run` wires to Maven exec for examples.
- Maven plugin: compiles `.fly` during the standard `compile` phase (works in multi‑module builds).
- LSP: diagnostics, completion, hover, definition, references, signature help, document symbols.
- IDEs: VS Code grammar/snippets + LSP; IntelliJ syntax + LSP client + templates.

## Spring Boot interop
- Annotations mapped 1:1 to Java; mapping annotations wrap single strings into `String[]` automatically.
- Structs generate JavaBean getters; constructor parameter names are emitted to enable Jackson.

## Packaging
- All modules version `1.0-Alpha`.
- LSP shaded JAR at `firefly-lsp/target/firefly-lsp.jar`.
- IDE artifacts: VSIX and IntelliJ ZIP under each plugin.

See also: docs/IMPLEMENTATION_GUIDE.md (deep‑dive), docs/SPRING_BOOT_GUIDE.md (integration), docs/RELEASE_NOTES_1.0-Alpha.md.
