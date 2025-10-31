# Flylang Language Guide

This guide is grounded in real examples under `examples/` and the current compiler/runtime. It introduces the language from first principles and grows into advanced topics.

---

## Table of Contents
- Overview and Philosophy
- Modules and Imports
- Values, Types, and Bindings
- Functions, Methods, and Visibility
- Classes and Instances
- Structs and Sparks
- Data Types (ADTs) and Pattern Matching
- Expressions and Control Flow
- Async, Futures, and Timeouts
- Java Interop
- Source Rules and Conventions
- Tooling
- Best Practices
- Known Limitations

## Overview and Philosophy
Flylang is an expression‑oriented, strongly‑typed JVM language with a focus on predictable semantics and pragmatic interoperability with Java. Immutability by default, explicitness where it matters, and a small runtime make it easy to reason about programs.

## Modules and Imports
- A file begins with a `module` declaration using `::` separators.
- Imports use `use` with `::` to reference Java or Flylang symbols.
- Static and instance calls use `::` (mandatory) — this keeps dispatch explicit.

Example:
```fly
module examples::hello_world

class Main {
  pub fn fly(args: [String]) -> Void {
    println("Hello, Flylang!");
  }
}
```

## Values, Types, and Bindings
- Bind with `let name: Type = expr;`
- Values are immutable by default — create a new value to "update".

### Primitive Types
Firefly provides native support for all JVM primitive types:

| Type | Description | Example |
|------|-------------|----------|
| `Int` | 32-bit integer | `let x: Int = 42` |
| `Long` | 64-bit integer | `let big: Long = 1000000` |
| `Float` | 64-bit floating point (double) | `let price: Float = 99.99` |
| `Double` | Alias for Float | `let d: Double = 3.14` |
| `Bool` | Boolean value | `let ok: Bool = true` |
| `String` | Text | `let name: String = "Alice"` |
| `Char` | Single character | `let c: Char = 'a'` |
| `Byte` | 8-bit integer | `let b: Byte = 127` |
| `Short` | 16-bit integer | `let s: Short = 1000` |

### Type Operations

**Arithmetic** (Int, Long, Float, Double):
```fly
let sum: Int = 10 + 20        // 30
let product: Float = 3.14 * 2.0  // 6.28
let neg: Int = -42              // Unary minus
```

**Type Conversions** (automatic widening):
```fly
let i: Int = 10
let f: Float = 3.5
let mixed: Float = i + f  // Int automatically converts to Float
```

**Comparisons**:
```fly
if (x < 100) { println("small") }
if (price >= 50.0) { println("expensive") }
```

### Standard Types

**UUID**:
```fly
use java::util::UUID

let id: UUID = UUID::randomUUID()
let parsed: UUID = UUID::fromString("550e8400-e29b-41d4-a716-446655440000")
```

**BigDecimal** (for precise decimal arithmetic):
```fly
use java::math::BigDecimal

let amount: BigDecimal = new BigDecimal("999.99")
let tax: BigDecimal = new BigDecimal("0.21")
let total: BigDecimal = amount::multiply(tax)
```

**Date and Time** (java.time integration):
```fly
use java::time::{LocalDate, LocalDateTime, Instant, Duration}

let today: LocalDate = LocalDate::now()
let timestamp: Instant = Instant::now()
let oneHour: Duration = Duration::ofHours(1)
```

Basic examples:
```fly
let x: Int = 42
let ok: Bool = true
let who: String = "Alice"
let pair = (1, 2)
```

## Functions, Methods, and Visibility
- Functions live in classes, modules, or sparks. Visibility: `pub` to export.
- Method invocation is `expr::method(args)`; static invocation is `Type::method(args)`.
- The entry point is `pub fn fly(args: [String]) -> Void`.

```fly
class Greeter {
  pub fn hello(name: String) -> String {
    "Hello, " + name
  }
}
```

## Classes and Instances
- Construct with `new Type(args)`; access fields with `expr.field`.
- Call instance methods via `instance::method(...)`.

```fly
class Demo {
  pub fn squared(x: Int) -> Int { x * x }
}

let d: Demo = new Demo();
let n: Int = d::squared(7);
```

## Structs and Sparks
### Structs (immutable product types)
- Define with fields; instances are constructed with record syntax.
- The compiler generates equality, hash, toString, and JavaBean getters.

```fly
struct Point { x: Int, y: Int }
let p: Point = Point { x: 0, y: 0 };
```

### Sparks (validated smart records)
- Support `validate { ... }` and `computed` properties.

```fly
spark Account {
  id: String,
  balance: Int,
  owner: String,

  validate { self.balance >= 0 }
  computed isActive: Bool { self.balance > 0 }
}
```

## Data Types (ADTs) and Pattern Matching
- Define ADTs with `data`; construct variants via `Type::Variant(...)`.
- Pattern match on structs, tuples, and data variants; `_` is a wildcard; name binds a value.

```fly
data Result { Ok(String), Err(Int) }

let r1: Result = Result::Ok("done");
let r2: Result = Result::Err(404);

let a: String = match r1 { Ok(s) => s, _ => "unknown" };
let b: String = match r2 { Err(code) => "error=" + code, _ => "ok" };
```

Tuple and struct patterns:
```fly
struct P { x: Int, y: Int }
let p0: P = P { x: 0, y: 0 };
let m: String = match p0 {
  P { x: 0, y: 0 } => "origin",
  P { x, y } => "(" + x + "," + y + ")",
  _ => "other"
};

let s: String = match (1, 2, 3) {
  (1, _, _) => "starts-with-1",
  (_, 2, _) => "middle-2",
  _ => "other"
};
```

## Expressions and Control Flow
- Everything is an expression; the last expression in a block is the value.
- `if` and `match` are expressions; use them inline.

```fly
let sign: String = if n > 0 { "pos" } else { "neg" };
```

## Async, Futures, and Timeouts
- `async fn` returns `Future<T>`.
- Inside async bodies use `.await`; outside, use `Future::get()`.
- Combinators: `Future::all(...)`, `Future::any(...)`, and `timeout(ms) { ... }`.

```fly
use com::firefly::runtime::async::Future

class Work {
  pub async fn compute() -> Int { 40 + 2 }
}

let f: Future = Work::compute();
let value: Int = timeout(50) { f.await };
```

## Java Interop
- Import Java types with `use java::...`.
- Construct with `new`, call instance/static with `::`.
- Annotations are supported and emitted correctly in bytecode.

```fly
use java::util::{ArrayList, Collections}
use java::lang::Math

let items: ArrayList = new ArrayList();
items::add("banana");
Collections::sort(items);
let m: Int = Math::max(10, 42);
```

## Source Rules and Conventions
- `::` is mandatory for both static and instance calls.
- Field access is dot: `expr.field`.
- Last expression returns (no `return` needed).
- Prefer immutable values; create new records instead of mutating.

## Tooling
- CLI: `fly compile` / `fly run`
- Maven: `com.firefly:firefly-maven-plugin` compiles `src/main/firefly`
- LSP provides diagnostics, completion, hover, navigation

## Best Practices
- Organize modules by domain: `com::example::api::{controllers,models,services}`
- Use sparks for validated domain models; derive computed properties
- Keep async boundaries narrow; prefer `Future::all/any` for coordination

## Known Limitations
- In some contexts, complex expressions like `Application.class` in multi‑arg static calls may require temporaries:
  ```fly
  let appClass = Application.class;
  SpringApplication::run(appClass);
  ```
- Generic collections interop may require explicit types/casts in some frameworks.

---

## Advanced Topics

### Nested Pattern Matching and Exhaustiveness
Flylang's pattern matching is structural and supports nesting. The compiler aims for exhaustiveness checks when feasible.

**Nested Data Patterns:**
```fly
data Payload { Json(String), Binary([Int]), None }
data Response { Ok(Payload), Err(Int) }

let r: Response = Response::Ok(Payload::Json("{}"));

let msg: String = match r {
  Ok(Json(s))      => "json: " + s,
  Ok(Binary(bytes)) => "binary (" + bytes.length + " bytes)",
  Ok(None)          => "empty",
  Err(code)         => "error=" + code
};
```

**Nested Struct Patterns:**
```fly
struct Inner { value: Int }
struct Outer { inner: Inner, tag: String }

let obj: Outer = Outer { inner: Inner { value: 42 }, tag: "test" };

let result: String = match obj {
  Outer { inner: Inner { value: 0 }, tag } => "zero: " + tag,
  Outer { inner, tag: "test" } => "test: " + inner.value,
  _ => "other"
};
```

**Array and Tuple Destructuring:**
```fly
// Array pattern
let nums: [Int] = [1, 2, 3];
let desc: String = match nums {
  [] => "empty",
  [x] => "singleton: " + x,
  [x, y, ..] => "at least two: " + x + "," + y,
  _ => "many"
};

// Tuple pattern
let triple = (10, 20, 30);
let sum: Int = match triple {
  (a, b, c) => a + b + c
};
```

**Exhaustiveness:**  
If your match covers all cases of a data type explicitly, the compiler recognizes completeness. Always include a `_` arm for safety:
```fly
data Color { RED, GREEN, BLUE }
let c: Color = Color::RED;

let name: String = match c {
  RED => "red",
  GREEN => "green",
  BLUE => "blue"
  // No `_` needed if all variants covered; still recommended for maintainability
};
```

### Guards and Range Patterns
You can refine patterns with guard clauses (`when`) and range patterns:

**Guard patterns:**
```fly
let age: Int = 25;
let category: String = match age {
  x when x < 13 => "child",
  x when x >= 13 && x < 20 => "teen",
  x when x >= 20 && x < 65 => "adult",
  _ => "senior"
};
```

**Range patterns (inclusive and exclusive):**
```fly
let score: Int = 85;
let grade: String = match score {
  0..60   => "F",   // exclusive upper bound
  60..70  => "D",
  70..80  => "C",
  80..90  => "B",
  90..=100 => "A",   // inclusive
  _ => "out of range"
};
```

### Advanced Async Patterns

#### Combining Futures
**All futures:**
```fly
use com::firefly::runtime::async::Future

pub async fn fetch(id: Int) -> String { "data" + id }

pub fn process() -> Void {
  let f1: Future = fetch(1);
  let f2: Future = fetch(2);
  let f3: Future = fetch(3);

  // Wait for all to complete
  Future::all(f1, f2, f3)::get();

  // Now all are ready
  let d1: String = f1::get();
  let d2: String = f2::get();
  let d3: String = f3::get();
  println(d1 + " " + d2 + " " + d3);
}
```

**Any future (race to first complete):**
```fly
pub async fn taskA() -> Int { 42 }
pub async fn taskB() -> Int { 99 }

pub fn raceExample() -> Void {
  let fA: Future = taskA();
  let fB: Future = taskB();
  let winner: Int = Future::any(fA, fB)::get();
  println("First finished: " + winner);
}
```

**Timeout wrapper:**
```fly
pub async fn slowCompute() -> Int {
  Thread::sleep(500);
  100
}

pub fn withTimeout() -> Void {
  let result: Int = timeout(200) {
    slowCompute().await
  };
  // If slowCompute takes >200ms, may throw or return fallback depending on runtime semantics
  println("result=" + result);
}
```

#### Async Composition
Chain async work manually by calling `.await` in async functions:
```fly
pub async fn step1() -> Int { 10 }
pub async fn step2(x: Int) -> Int { x + 20 }

pub async fn pipeline() -> Int {
  let a: Int = step1().await;
  let b: Int = step2(a).await;
  b
}

let final: Int = pipeline()::get();
```

### Style Conventions
**Naming:**
- Types: `PascalCase` (`Account`, `Result`)
- Functions and variables: `snake_case` or `camelCase` (prefer `snake_case` for consistency with Rust-like aesthetics, but `camelCase` also accepted)
- Modules: `lowercase::separated::by::colons`

**Formatting:**
- Use 2‑space indentation
- Open braces on same line for blocks `{ ... }`
- No semicolons in match arms (expression result is implicit)

**Best Practices:**
- **Explicit dispatch:** Always use `::` for method calls (instance or static) to avoid ambiguity.
- **Immutability first:** Prefer `let` over `let mut`; create new values instead of mutating.
- **Pattern exhaustiveness:** Always include a catch-all `_` arm in match expressions to handle future-proofing.
- **Async boundaries:** Keep async boundaries clear; prefer `Future::all` / `Future::any` over manual coordination.
- **Validation:** Use sparks for domain models that require invariants (e.g., non-negative balances, email format).

### Type System Deep Dive
Flylang supports:
- **Primitives:** `Int`, `String`, `Bool`, `Float`, `Void` (and `Unit` as alias)
- **Optionals:** `T?` for nullable types
- **Union and intersection:** `T | U` (union), `T & U` (intersection) — experimental/advanced
- **Function types:** `(T, U) -> R`
- **Tuples:** `(Int, String, Bool)`
- **Arrays/Lists:** `[T]`
- **Maps:** `[K: V]`
- **References:** `&T`, `&mut T` (for advanced memory semantics; mostly internal)

Type inference is limited; always annotate in `let`, function parameters, and return types for clarity.

### Error Handling Patterns
**Try-catch (Java interop):**
```fly
use java::io::IOException

pub fn readFile(path: String) -> String {
  try {
    // file read logic
    "content"
  } catch (IOException e) {
    "error: " + e.getMessage()
  } finally {
    // cleanup
  }
}
```

**Result types (ADT style):**
```fly
data Result<T> { Ok(T), Err(String) }

pub fn divide(a: Int, b: Int) -> Result {
  if b == 0 {
    Result::Err("division by zero")
  } else {
    Result::Ok(a / b)
  }
}

let outcome: Result = divide(10, 2);
let msg: String = match outcome {
  Ok(val) => "result=" + val,
  Err(e) => "error: " + e
};
```

---

## Troubleshooting by Module

### Compiler Errors
**"Undefined symbol `X`"**  
→ Ensure `use` declaration is present; check spelling and module path.

**"Type mismatch: expected `T`, found `U`"**  
→ Add explicit casts or conversions; check that method return types match variable annotations.

**"Pattern match not exhaustive"**  
→ Add a catch-all `_` arm in your match expression.

**"Cannot resolve method `foo` on `Type`"**  
→ Verify the method exists; use `::` for instance/static calls, not `.` (dot is for field access only).

### Runtime Issues
**"NullPointerException in generated code"**  
→ Flylang is null-safe by design; if you interop with Java and receive null, wrap in `Option` or use `?` operator.

**"ClassNotFoundException" or "NoClassDefFoundError"**  
→ Ensure `firefly-runtime.jar` is on the classpath; Maven plugin should auto-configure; for CLI, use `-cp`.

**"ValidationException" from Spark**  
→ Spark's `validate` block failed; check constructor arguments satisfy invariants.

### LSP and IDE
**"No syntax highlighting"**  
→ Verify VS Code or IntelliJ plugin is installed and enabled; check file extension is `.fly`.

**"LSP not starting"**  
→ Check `settings.json` for `firefly.lspPath` (VS Code) or plugin configuration (IntelliJ); ensure Java 17+ is available.

**"Autocomplete not working"**  
→ LSP needs the project compiled at least once; run `mvn compile` or `fly compile` first.

### Maven Plugin
**"Plugin execution not found"**  
→ Add `<plugin>` entry in `<build><plugins>` with correct `groupId` and `artifactId`.

**"Source directory not recognized"**  
→ Default is `src/main/firefly`; override with `<sourceDirectory>` in plugin config.

**"Compilation fails with cryptic errors"**  
→ Run `mvn clean compile -X` for debug output; check `.fly` files for syntax errors first.

### Spring Boot Integration
**"Controller methods not mapped"**  
→ Use `@RestController` or `@Controller` on class; ensure methods have `@GetMapping`, `@PostMapping`, etc.

**"Dependency injection fails"**  
→ Mark constructor parameters with `@Autowired` or use field injection; ensure Spring context scans your package.

**"Application doesn't start"**  
→ Check `fly` method calls `SpringApplication::run(...)` with correct arguments; verify `@SpringBootApplication` is present.

---

**Next Steps:**  
Explore `examples/` for runnable code; read `ARCHITECTURE.md` for compiler internals; see `SPRING_BOOT_GUIDE.md` for web app patterns.

