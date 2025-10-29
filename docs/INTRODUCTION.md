# Flylang — Introduction

Flylang is a modern, expression-oriented JVM language focused on developer productivity and predictability. It blends a concise syntax and strong typing with async-first concurrency and seamless Java interoperability.

---

## Why Flylang?

### The Problem
JVM languages often force a choice: power with complexity (Scala) or simplicity with verbosity (Java). Many modern alternatives sacrifice Java interoperability or introduce runtime overhead that complicates debugging and deployment.

### The Solution
Flylang targets the pragmatic developer who wants:
- **Expressive syntax** without arcane symbols or implicit magic
- **Predictable semantics** through immutability-by-default and explicit dispatch
- **First-class async** that feels natural, not bolted-on
- **Zero-friction Java interop** with full annotation support for Spring Boot, Jakarta, and more
- **Understandable implementation** with a small compiler and runtime you can reason about

### Who is it for?
- Backend engineers building Spring Boot microservices
- Teams migrating from Java who want modern syntax without rewriting the world
- Developers who value clarity over cleverness
- Anyone tired of boilerplate but skeptical of macro magic

---

## Design Philosophy

### 1. Explicit Where It Matters
- Method dispatch uses `::` for both static and instance calls—no guessing
- Types are annotated in declarations; inference is conservative
- Imports are namespaced with `::`; no wildcard pollution by default

### 2. Expressions, Not Statements
- `if`, `match`, `try`, and blocks return values
- Last expression in a block is the result—no `return` needed
- Encourages functional composition without ceremony

### 3. Immutability by Default
- Structs and sparks are immutable; "updates" create new instances
- Variables are `let` (immutable) unless marked `let mut`
- Side effects are localized and obvious

### 4. Interop Without Compromise
- Call Java libraries as if they were native Flylang
- Annotations work seamlessly: `@RestController`, `@Autowired`, `@Transactional`
- Construct objects with `new`, invoke methods with `::`
- No wrapper types, no reflection gymnastics

### 5. Async as a First-Class Citizen
- `async fn` returns typed `Future<T>`
- `.await` inside async, `::get()` outside
- Combinators (`Future::all`, `Future::any`) and `timeout` for coordination
- No callback hell, no explicit thread pools

---

## Core Concepts (At a Glance)

### Modules and Organization
- Every file starts with `module path::to::module`
- Import Java and Flylang symbols with `use java::util::List`
- Visibility: `pub` (public), `priv` (package-private), or default (internal)

### Data Structures
- **Structs:** Immutable product types with fields
- **Sparks:** Smart records with validation and computed properties
- **Data (ADTs):** Sum types for modeling variants (like Rust enums or Haskell ADTs)
- **Classes:** For Java interop and Spring Boot controllers

### Functions and Methods
- Functions are expressions; last line is the return value
- Entry point: `pub fn fly(args: [String]) -> Void`
- Instance methods: `receiver::method(args)`
- Static methods: `Type::method(args)`

### Pattern Matching
- Structural matching on structs, tuples, data variants
- Guards (`when`), range patterns (`..`, `..=`)
- Exhaustiveness checking for data types

### Concurrency
- `async fn` for asynchronous functions
- `Future<T>` for deferred computations
- Combinators: `all`, `any`, `timeout`
- No explicit executors or thread management

---

## Comparison to Other JVM Languages

| Feature | Flylang | Kotlin | Scala | Java |
|---------|---------|--------|-------|------|
| **Syntax** | Concise, Rust-inspired | Concise, pragmatic | Dense, academic | Verbose |
| **Java Interop** | Seamless, zero-cost | Seamless | Good, some friction | Native |
| **Immutability** | Default | Opt-in | Opt-in | Opt-in |
| **Async Model** | Built-in futures | Coroutines | Scala 3 async | Virtual threads |
| **Pattern Matching** | Structural, ADTs | Sealed classes | Powerful, complex | Switch (limited) |
| **Learning Curve** | Gentle | Gentle | Steep | Flat |
| **Tooling Maturity** | Alpha (LSP, plugins) | Mature | Mature | Mature |
| **Spring Boot Support** | First-class | First-class | Good | Native |

---

## Learning Path

### For Java Developers
1. Start with **GETTING_STARTED.md**: Install and run examples
2. Read **LANGUAGE_GUIDE.md** sections 1–8 (syntax, types, functions)
3. Try **RECIPES.md** for common patterns
4. Build a REST API with **SPRING_BOOT_GUIDE.md**
5. Explore **EXAMPLES.md** for runnable projects

### For Rust/Kotlin Developers
1. Skim **INTRODUCTION.md** (you're here!)
2. Jump to **LANGUAGE_GUIDE.md** Advanced Topics (patterns, async)
3. Check **ARCHITECTURE.md** for bytecode generation details
4. Review **SPRING_BOOT_GUIDE.md** for JVM ecosystem integration

### For Language Designers
1. Read **ARCHITECTURE.md** for system design
2. Study **IMPLEMENTATION_GUIDE.md** for compiler internals
3. Explore `firefly-compiler/` source for AST, codegen, and semantic passes
4. Review `firefly-runtime/` for Future and concurrency primitives

---

## Next Steps
- **Install:** Follow [GETTING_STARTED.md](GETTING_STARTED.md)
- **Learn:** Read [LANGUAGE_GUIDE.md](LANGUAGE_GUIDE.md)
- **Explore:** Run examples from [EXAMPLES.md](EXAMPLES.md)
- **Build:** Create a REST API with [SPRING_BOOT_GUIDE.md](SPRING_BOOT_GUIDE.md)
- **Understand:** Dive into [ARCHITECTURE.md](ARCHITECTURE.md)

---

*Flylang v1.0-Alpha — A pragmatic JVM language for the modern backend engineer.*
