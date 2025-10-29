# Flylang Implementation Guide

This document explains how Flylang is implemented: grammar and AST, semantic analysis, bytecode generation, and runtime. It reflects the current codebase (v1.0-Alpha) and recent correctness fixes.

---

## High-Level Architecture

```
┌────────────────────────────────────────────────────────────────┐
│                        Flylang Toolchain                       │
└────────────────────────────────────────────────────────────────┘

  .fly Source Files
        │
        ▼
  ┌─────────────┐
  │   Lexer     │  (ANTLR-generated)
  └──────┬──────┘
         │ tokens
         ▼
  ┌─────────────┐
  │   Parser    │  (ANTLR: Firefly.g4)
  └──────┬──────┘
         │ Parse Tree
         ▼
  ┌─────────────┐
  │ AST Builder │  (com.firefly.compiler.ast.*)
  └──────┬──────┘
         │ AST Nodes
         ▼
  ┌─────────────┐
  │  Semantic   │  • Symbol resolution
  │  Analysis   │  • Type checking
  └──────┬──────┘  • Async validation
         │ Validated AST
         ▼
  ┌─────────────┐
  │  Bytecode   │  (ASM library)
  │  Generator  │  • Emit .class files
  └──────┬──────┘  • Invoke descriptors
         │ JVM Bytecode
         ▼
  ┌─────────────┐
  │   Runtime   │  • Future<T> runtime
  │  (JVM + CP) │  • Executor pools
  └─────────────┘  • Timeout scheduler
```

## Components Overview

| Component | Location | Responsibility |
|-----------|----------|----------------|
| **Grammar** | `firefly-compiler/src/main/antlr4/` | ANTLR4 grammar (Firefly.g4) |
| **AST** | `firefly-compiler/.../ast/` | In-memory representation of source |
| **Semantic** | `firefly-compiler/.../semantic/` | Type resolution, validation |
| **Codegen** | `firefly-compiler/.../codegen/` | ASM-based bytecode emission |
| **Runtime** | `firefly-runtime/` | Future, async executor, helpers |
| **Maven Plugin** | `firefly-maven-plugin/` | Build integration |
| **CLI** | `firefly-cli/` | Command-line driver |
| **LSP** | `firefly-lsp/` | Language Server Protocol |
| **IDE Plugins** | `ide-plugins/` | VS Code & IntelliJ extensions |

---

## Compiler Pipeline in Detail

### Phase 1: Parsing and AST Construction

**Input:** `.fly` source files  
**Output:** Abstract Syntax Tree (AST)

1. **Lexical Analysis (Lexer)**
   - ANTLR4-generated lexer tokenizes the input
   - Produces tokens: keywords (`fn`, `class`, `async`), identifiers, literals, operators
   - Handles comments (`//`, `/* */`) and doc comments (`///`, `/** */`)

2. **Syntax Analysis (Parser)**
   - ANTLR4 parser (`Firefly.g4`) builds a parse tree
   - Grammar rules: `compilationUnit`, `classDeclaration`, `functionDeclaration`, etc.
   - Enforces mandatory `module` declaration at file start

3. **AST Building**
   - Visitor pattern transforms parse tree into typed AST nodes
   - Key node types:
     - `ModuleNode`, `ClassNode`, `FunctionNode`
     - `StructNode`, `SparkNode`, `DataNode`
     - `ExpressionNode` (if, match, lambda, call, etc.)
     - `PatternNode` (literal, variable, struct, tuple, wildcard)

### Phase 2: Semantic Analysis

**Input:** AST  
**Output:** Validated, type-resolved AST

1. **Symbol Resolution (TypeResolver)**
   - Resolves `use` imports (Java and Flylang types)
   - Builds symbol table for classes, functions, fields
   - Validates module paths match directory structure

2. **Type Checking**
   - Ensures `let` bindings have correct types
   - Validates function return types match body expressions
   - Checks pattern matching exhaustiveness (basic heuristic)

3. **Method Resolution (MethodResolver)**
   - Resolves `Type::method(args)` and `expr::method(args)` calls
   - Handles Java method overloading and varargs
   - Inserts type conversions (boxing, widening) as needed

4. **Async Validation**
   - Ensures `.await` only appears in `async fn` bodies
   - Validates `Future<T>` types for async returns
   - Checks timeout expressions have valid signatures

### Phase 3: Bytecode Generation (ASM)

**Input:** Validated AST  
**Output:** JVM `.class` files

**Class Generation:**
- Each `class`, `struct`, `data`, `spark` becomes a JVM class
- Package name derived from module path: `module a::b::c` → `a/b/c.class`

**Method Generation:**
- `pub fn fly(args: [String]) -> Void` becomes:
  - Instance method: `fly([Ljava/lang/String;)V`
  - Static `main`: constructs instance, calls `fly(args)`

**Dispatch Semantics:**
- `Type::method(args)` → `INVOKESTATIC`
- `expr::method(args)` → `INVOKEVIRTUAL` (with `CHECKCAST` if needed)
- Field access `expr.field` → `GETFIELD` / `PUTFIELD`

**Expression Compilation:**
- Blocks: compile statements sequentially; last expression is stack result
- `if`: branch with `IFEQ` / `IFNE`; both arms pushed to stack
- `match`: nested branching with `instanceof` checks for data variants
- `async fn`: emit helper + `invokedynamic` to wrap in `Future.async(...)`

## Notable Implementations

### Async Functions
- `async fn name(params) -> T { body }` becomes a public instance method returning `Future<T>`
- A private static helper is emitted with the exact declared return descriptor for `T`
- The public method builds a Runnable or Callable via `invokedynamic` (LambdaMetafactory) and passes it to `Future.async(...)`
- Primitive returns are emitted with typed IRETURN/LRETURN/DRETURN; objects with ARETURN
- When needed, unboxing is inserted at return sites to satisfy the JVM verifier

### Await
- `.await` compiles to `Future#get()` at call sites

### Timeout Blocks
- Syntax: `timeout(ms) { body }`
- Codegen builds `Future.timeout(ms, () -> body)` using `invokedynamic`
- The compiler now captures all in-scope local variables (including `self`) in a stable slot order, generates a capture-aware synthetic method, and calls it through a typed Callable factory
- The result is obtained via `get()` and unboxed when the body yields a primitive

### Pattern Matching
- Structs and tuples pattern matching: emits nested label/branching and local binds
- Data (sum) types: base abstract class + variant classes; nullary variants are static singletons; payload variants have factories and fields

### Java Interop
- Static method calls: resolved via MethodResolver (overloads/varargs); emits INVOKESTATIC with proper conversions
- Instance calls: receiver type is inferred or provided by declared types; a CHECKCAST is inserted when needed to satisfy the verifier before INVOKEVIRTUAL
- Numeric widening and boxing/unboxing are applied as required

## Data Definitions

- struct → final fields, constructor, getters (JavaBean), equals/hashCode/toString
- spark → immutable record with validation and computed properties; plus ergonomic helpers
- data → abstract base + variant classes; factories for payload variants and static fields for nullary ones

## Runtime: Future<T>

- Backed by `CompletableFuture<T>` and an executor pool for async computation
- Combinators: `map`, `flatMap`, `zip`, `all`, `any`, `withTimeout` and `timeout`
- Timeouts: a dedicated daemon `ScheduledExecutorService` guarantees timer delivery and avoids starvation of the compute pool

### Signatures in use
- `Future.async(Callable<T>)` and `Future.async(Callable<T>, Executor)`
- `Future.async(Runnable)` and `Future.async(Runnable, Executor)` return `Future<Void>`
- `Future.all(Future<?>...)` → `Future<Void>`
- `Future.any(Future<T>...)` → `Future<Object>` (first result)
- `Future.timeout(long, Callable<T>)` → `Future<T>` (throws TimeoutException)

## Maven Plugin

- `firefly-maven-plugin` scans `src/main/firefly` for `.fly` files and compiles them into `${project.build.outputDirectory}`
- Uses the project compile classpath to resolve Java types and annotations
- Goal: `compile` in the standard `compile` phase

## Verifier and Correctness Fixes (recent)

- Async helpers return the declared type descriptor (not raw `Object`)
- Insert unboxing at return sites when expression type is `Object` but method return is primitive
- Insert receiver `CHECKCAST` before INVOKEVIRTUAL when a declared type is known
- Timeout lambdas support capturing all locals; descriptors are built from actual JVM slot types
- Runtime `Future.timeout` uses a dedicated scheduler to guarantee firing and avoid deadlocks

## Testing Strategy

- All examples under `examples/` are build-and-run verified
- Documentation snippets are taken directly from those examples

## Roadmap

- Full enum/impl/protocol codegen
- Better generics across data/spark and method resolution
- More comprehensive standard library and structured concurrency primitives
