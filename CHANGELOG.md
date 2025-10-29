# Changelog

All notable changes to Flylang will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [1.1-Beta] - 2025-01-XX

### Added
- **Full Generics Support**: Complete implementation of generics for data and spark types
  - Generic type parameters with bounds (e.g., `data Option<T>`, `spark Container<T: Printable>`)
  - Type parameter validation and trait bounds checking
  - Generic signature generation in bytecode
  
- **Enum Support via Data Declarations**: Algebraic data types serve as enums
  - Simple enums: `data Color { RED, GREEN, BLUE }`
  - Enums with payloads: `data Maybe { Some(String), None }`
  - Full pattern matching support
  
- **Trait System**: Complete trait declaration and implementation
  - Trait declarations with method signatures
  - Generic traits with type parameters
  - `impl Trait for Type` syntax for trait implementations
  - Trait bounds checking and validation
  
- **Protocol Support**: Interface declarations for Java interop
  - Full interface codegen with method signatures
  - Support for extending multiple interfaces
  - Annotation support on interfaces
  
- **Structured Concurrency**: New `StructuredScope` runtime primitive
  - Automatic task lifecycle management
  - Fail-fast error handling with automatic cancellation
  - Permissive mode for fault-tolerant scenarios
  - Timeout support for scope operations
  - Helper methods: `StructuredScope.scoped()` for convenient usage
  
- **Standard Library Expansion**: New validation module
  - String validation (notEmpty, notBlank, email, url, pattern matching)
  - Numeric validation (positive, nonNegative, range, min/max)
  - Collection validation (size bounds, emptiness checks)
  - Option validation (required values)
  - Custom validators and combinators (all, any)
  
- **New Example**: `structured-concurrency-demo`
  - Demonstrates parallel task execution
  - Fan-out/fan-in pattern examples
  - Error handling and automatic cancellation

### Changed
- Updated README.md to reflect version 1.1-Beta
- Enhanced roadmap with clearer feature tracking

### Technical Details
- All generics-related AST nodes now properly support `TypeParameter` lists
- `BytecodeGenerator` generates proper JVM generic signatures
- `TraitBoundsChecker` validates all type parameter bounds
- `StructuredScope` uses `CompletableFuture` internally for better composability

## [1.0-Alpha] - 2025-01-XX

### Added
- **Core Language Features**
  - Classes, structs, sparks (immutable smart records)
  - Data types (algebraic data types/sum types)
  - Async/await with Future-based concurrency
  - Pattern matching (tuple, struct, data patterns)
  - Expression-oriented syntax
  - Immutability by default
  - Full generics support for data and spark types
  - Generic type parameters with bounds (e.g., `data Option<T>`, `spark Container<T: Printable>`)
  
- **Trait System**
  - Trait declarations with method signatures
  - Generic traits with type parameters
  - `impl Trait for Type` syntax for trait implementations
  - Trait bounds checking and validation
  
- **Protocol Support**
  - Interface declarations for Java interop
  - Full interface codegen with method signatures
  - Support for extending multiple interfaces
  - Annotation support on interfaces
  
- **Enum Support via Data Declarations**
  - Algebraic data types serve as enums
  - Simple enums: `data Color { RED, GREEN, BLUE }`
  - Enums with payloads: `data Maybe { Some(String), None }`
  - Full pattern matching support
  
- **Concurrency Primitives**
  - Built-in `async` functions
  - `Future<T>` type with combinators (all, any, race)
  - Timeout expressions
  - Concurrent execution blocks
  - Structured concurrency with `StructuredScope` runtime primitive
  - Automatic task lifecycle management
  - Fail-fast error handling with automatic cancellation
  - Permissive mode for fault-tolerant scenarios
  
- **Java Interop**
  - Zero-friction Java interoperability
  - Direct use of Java classes and libraries
  - Spring Boot integration
  - Support for Java annotations
  
- **Type System**
  - Strong static typing
  - Type inference
  - Optional types (`T?`)
  - Tuple types
  - Array types
  - Primitive types aligned with JVM
  - Generic signature generation in bytecode
  
- **Pattern Matching**
  - Exhaustiveness checking
  - Tuple patterns
  - Struct patterns
  - Data variant patterns
  - Wildcard patterns
  
- **Standard Library**
  - `firefly::std::option` - Option<T> type (368 lines)
  - `firefly::std::result` - Result<T, E> type (212 lines)
  - `firefly::std::math` - Mathematical functions (298 lines)
  - `firefly::std::string` - String utilities (330 lines)
  - `firefly::std::concurrent` - Concurrency utilities
  - `firefly::std::validation` - Validation module
    - String validation (notEmpty, notBlank, email, url, pattern matching)
    - Numeric validation (positive, nonNegative, range, min/max)
    - Collection validation (size bounds, emptiness checks)
    - Option validation (required values)
    - Custom validators and combinators (all, any)
  - Placeholder modules for collections, I/O, filesystem, time
  
- **Tooling**
  - `fly` CLI with commands: version, compile, run, check, repl
  - Maven plugin (`firefly-maven-plugin`)
  - Language Server Protocol (LSP) implementation
  - VS Code extension (syntax highlighting, completion, diagnostics)
  - IntelliJ IDEA plugin (syntax highlighting, LSP integration)
  
- **Examples**
  - hello-world
  - async-demo
  - concurrency-demo
  - futures-combinators-demo
  - patterns-demo
  - data-patterns-demo
  - java-interop-advanced
  - sparks-demo
  - async-pipeline-demo
  - spring-boot-demo
  - enum-demo
  - task-manager-cli
  - structured-concurrency-demo

### Technical Implementation
- Compiler written in Java 21
- AST-based compilation pipeline
- Bytecode generation using ASM library
- Type checking and semantic analysis
- Symbol table and scope management
- Diagnostic reporting with source locations
- All generics-related AST nodes support `TypeParameter` lists
- `BytecodeGenerator` generates proper JVM generic signatures
- `TraitBoundsChecker` validates all type parameter bounds
- `StructuredScope` uses `CompletableFuture` internally for better composability

## [Unreleased]

### Planned
- Advanced type inference improvements
- Macro system for code generation
- Native compilation via GraalVM
- IDE plugin enhancements (refactoring, debugging)
- Performance optimizations
- Additional standard library modules

---

For more details, see the [GitHub Issues](https://github.com/firefly-oss/firefly-lang/issues).
