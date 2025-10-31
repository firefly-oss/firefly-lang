# Flylang Documentation Index

A curated navigation guide for all Flylang documentation. Start here to find the right resource for your task.

---

## 📖 Getting Started

New to Flylang? Start here:

1. **[README.md](../README.md)** — Project overview, value proposition, quick start
2. **[INTRODUCTION.md](INTRODUCTION.md)** — Philosophy, comparison to other JVM languages, learning paths
3. **[GETTING_STARTED.md](GETTING_STARTED.md)** — Installation, first program, Maven setup, editor configuration

---

## 📚 Language Reference

Learn Flylang syntax and semantics:

- **[LANGUAGE_GUIDE.md](LANGUAGE_GUIDE.md)** — Complete language guide:
  - Modules, types, functions, classes
  - Structs, sparks, data types (ADTs)
  - Pattern matching, async/await
  - Advanced topics (nested patterns, guards, timeouts)
  - Troubleshooting by module

- **[TYPE_SYSTEM.md](TYPE_SYSTEM.md)** — Native type system:
  - Primitive types (Int, Float, Bool, String)
  - Date/Time types (Date, DateTime, Instant, Duration)
  - Collections, UUID, BigDecimal
  - Compiler integration and JVM bytecode mapping

- **[MIGRATION_GUIDE.md](MIGRATION_GUIDE.md)** — Migrate from java.time to native types:
  - Side-by-side examples
  - Method name mapping
  - Complete migration checklist
  - Gradual migration strategy

- **[RECIPES.md](RECIPES.md)** — Copy-paste code snippets for common tasks:
  - HTTP requests, JSON parsing
  - Async patterns, error handling
  - Collections, validation

- **[EXAMPLES.md](EXAMPLES.md)** — Index of runnable examples with descriptions and commands

---

## 🌐 Frameworks & Integration

Building web apps and microservices:

- **[SPRING_BOOT_GUIDE.md](SPRING_BOOT_GUIDE.md)** — Complete Spring Boot integration:
  - REST controllers, dependency injection
  - JSON mapping, validation, error handling
  - JPA, transactions, testing
  - Production deployment

- **[SPRING_BOOT_IMPLEMENTATION_NOTES.md](SPRING_BOOT_IMPLEMENTATION_NOTES.md)** — Technical implementation details for Spring support

**Note:** For concurrency patterns (async/await, futures, timeouts), see **LANGUAGE_GUIDE.md** sections on "Async, Futures, and Timeouts" and "Advanced Async Patterns". Java interop is covered throughout **LANGUAGE_GUIDE.md** and **SPRING_BOOT_GUIDE.md**.

---

## ⚙️ Implementation & Architecture

Understand how Flylang works internally:

- **[ARCHITECTURE.md](ARCHITECTURE.md)** — System design overview:
  - Compiler pipeline diagram
  - AST, semantic analysis, bytecode generation
  - Runtime design, tooling architecture

- **[IMPLEMENTATION_GUIDE.md](IMPLEMENTATION_GUIDE.md)** — Deep dive into compiler internals:
  - Parsing, AST construction
  - Semantic passes (symbol resolution, type checking)
  - Bytecode emission (ASM, invoke descriptors)
  - Async/await implementation
  - Future runtime and executors

---

## 📦 Release & Operations

Release management and project status:

- **[RELEASE_NOTES_1.0-Alpha.md](RELEASE_NOTES_1.0-Alpha.md)** — What's new in v1.0-Alpha
- **[PUBLISH_CHECKLIST_1.0-Alpha.md](PUBLISH_CHECKLIST_1.0-Alpha.md)** — Pre-release verification checklist

---

## 👥 Contributing

Contribute to Flylang development:

- **GitHub Issues** — Report bugs, request features
- **Pull Requests** — Submit improvements (follow existing code style)
- Development setup: See **GETTING_STARTED.md** (build from source)

---

## 🗺️ Documentation Map by Audience

### For Backend Engineers (Spring Boot)
1. [INTRODUCTION.md](INTRODUCTION.md) → Why Flylang?
2. [GETTING_STARTED.md](GETTING_STARTED.md) → Install and run
3. [LANGUAGE_GUIDE.md](LANGUAGE_GUIDE.md) → Sections 1–9 (basics)
4. [SPRING_BOOT_GUIDE.md](SPRING_BOOT_GUIDE.md) → Build REST APIs
5. [RECIPES.md](RECIPES.md) → Quick solutions
6. [EXAMPLES.md](EXAMPLES.md) → Hands-on code

### For Language Enthusiasts (Rust/Kotlin/Scala Developers)
1. [INTRODUCTION.md](INTRODUCTION.md) → Philosophy and comparison
2. [LANGUAGE_GUIDE.md](LANGUAGE_GUIDE.md) → Advanced topics (patterns, async)
3. [ARCHITECTURE.md](ARCHITECTURE.md) → Design decisions
4. [IMPLEMENTATION_GUIDE.md](IMPLEMENTATION_GUIDE.md) → Compiler internals
5. [EXAMPLES.md](EXAMPLES.md) → Study patterns and idioms

### For Contributors & Tool Builders
1. [GETTING_STARTED.md](GETTING_STARTED.md) → Build from source
2. [ARCHITECTURE.md](ARCHITECTURE.md) → System overview
3. [IMPLEMENTATION_GUIDE.md](IMPLEMENTATION_GUIDE.md) → Compiler pipeline
4. Source code in `firefly-compiler/`, `firefly-runtime/`, `firefly-lsp/`
5. IDE plugin code in `ide-plugins/vscode-firefly/` and `ide-plugins/intellij-firefly/`

---

## ❓ Quick Links

| I want to... | Read this |
|--------------|----------|
| Understand Flylang's design | [INTRODUCTION.md](INTRODUCTION.md) |
| Install and run my first program | [GETTING_STARTED.md](GETTING_STARTED.md) |
| Learn the syntax | [LANGUAGE_GUIDE.md](LANGUAGE_GUIDE.md) |
| Understand the type system | [TYPE_SYSTEM.md](TYPE_SYSTEM.md) |
| Migrate from java.time | [MIGRATION_GUIDE.md](MIGRATION_GUIDE.md) |
| Build a REST API | [SPRING_BOOT_GUIDE.md](SPRING_BOOT_GUIDE.md) |
| See example code | [EXAMPLES.md](EXAMPLES.md) |
| Copy-paste solutions | [RECIPES.md](RECIPES.md) |
| Understand the compiler | [ARCHITECTURE.md](ARCHITECTURE.md) + [IMPLEMENTATION_GUIDE.md](IMPLEMENTATION_GUIDE.md) |
| Troubleshoot issues | [LANGUAGE_GUIDE.md](LANGUAGE_GUIDE.md) (Troubleshooting section) |

---

*Flylang v1.0-Alpha — Documentation last updated: 2025-10-31*
