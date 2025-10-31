# Flylang

<p align="center">
  <strong>A pragmatic JVM language for the modern backend engineer</strong>
</p>

<p align="center">
  Expression-oriented · Async-first · Seamless Java Interop
</p>

<p align="center">
  <img src="https://img.shields.io/badge/version-1.0--Alpha-blue.svg" alt="Version 1.0-Alpha">
  <img src="https://img.shields.io/badge/Java-21+-orange.svg" alt="Java 21+">
  <img src="https://img.shields.io/badge/Maven-3.8+-red.svg" alt="Maven 3.8+">
  <img src="https://img.shields.io/badge/license-Apache%202.0-green.svg" alt="License">
</p>

---

Flylang combines **concise, Rust-inspired syntax** with **first-class Java interoperability** to deliver a productive, type-safe language for building microservices, REST APIs, and concurrent systems on the JVM.

**Write less code. Reason more clearly. Ship faster.**

---

## Table of Contents
- [Why Flylang?](#why-flylang)
- [Quick Start](#quick-start)
- [Language Highlights](#language-highlights)
  - [Native Type System](#native-type-system)
  - [Async/Await](#asyncawait-built-in)
  - [Pattern Matching](#pattern-matching)
  - [Sparks](#sparks-smart-records)
  - [Java Interop](#java-interop-zero-ceremony)
- [Spring Boot Integration](#spring-boot-integration)
- [Tooling](#tooling)
- [Examples](#examples)
- [Documentation](#documentation)
- [Contributing](#contributing)
- [License](#license)

---

## Why Flylang?

### 🎯 The Problem
JVM languages often force trade-offs:
- **Java:** Verbose, boilerplate-heavy, weak concurrency primitives
- **Kotlin:** Good interop but still JVM-centric idioms, nullable types by default
- **Scala:** Powerful but complex, steep learning curve, slow compilation

### ✨ The Solution
Flylang delivers:
- **📦 Zero-friction Java interop:** Use Spring Boot, Jackson, JPA—no wrappers, no runtime overhead
- **⚡ Async-first concurrency:** Built-in `async`/`await`, typed `Future<T>`, timeouts, and combinators
- **🔒 Immutability by default:** Structs and sparks are immutable; mutation is opt-in and explicit
- **🧩 Expression-oriented:** Everything returns a value; no need for explicit `return`
- **📚 Predictable semantics:** Explicit dispatch (`::` for methods, `.` for fields), strong typing
- **🔧 Small, understandable toolchain:** Compiler and runtime you can reason about

### 👥 Who Should Use Flylang?
- Backend engineers building Spring Boot microservices
- Teams migrating from Java who want modern syntax without rewriting everything
- Developers who value clarity, immutability, and async-first design
- Anyone tired of `NullPointerException` and callback hell

---

## Quick Start

### Installation

**Prerequisites:** Java 21+, Maven 3.8+

```bash
# Option 1: Quick install from GitHub (recommended)
curl -fsSL https://raw.githubusercontent.com/firefly-oss/firefly-lang/main/scripts/install.sh | bash

# Option 2: Install to custom location (e.g., user home)
curl -fsSL https://raw.githubusercontent.com/firefly-oss/firefly-lang/main/scripts/install.sh | bash -s -- --prefix "$HOME/.local"
export PATH="$HOME/.local/bin:$PATH"

# Option 3: Build from source
git clone https://github.com/firefly-oss/firefly-lang.git
cd firefly-lang
mvn clean install -DskipTests
bash scripts/install.sh --from-source --prefix "$HOME/.local"

fly version  # Should print: Flylang CLI 1.0-Alpha
```

### Hello World

Create `Main.fly`:

```fly path=null start=null
module examples::hello_world

class Main {
  pub fn fly(args: [String]) -> Void {
    println("Hello, Flylang!");
  }
}
```

Run it:
```bash
fly run examples/hello-world
# Output: Hello, Flylang!
```

**Next steps:** See [GETTING_STARTED.md](docs/GETTING_STARTED.md) for Maven project setup and editor configuration.

---

## Language Highlights

### 🎯 Native Type System

Firefly has a comprehensive native type system with 50+ built-in types that provide clean APIs while compiling efficiently to JVM bytecode.

**Primitives:**
```fly path=null start=null
let count: Int = 42
let price: Float = 99.99
let name: String = "Firefly"
let active: Bool = true
```

**Native Date/Time Types:**
```fly path=null start=null
use firefly::std::time::{Date, DateTime, Instant, Duration}

// Clean, idiomatic APIs
let today: Date = Date.now()
let tomorrow: Date = today.plusDays(1)

let meeting: DateTime = DateTime.of(2025, 10, 31, 14, 30, 0)
let later: DateTime = meeting.plusHours(2)

let timestamp: Instant = Instant.now()
let oneHour: Duration = Duration.ofHours(1)
```

**Why Native Types?**
- 🎨 **Idiomatic APIs**: Write `Date.now()` not `LocalDate.now()`
- 🔧 **Consistent**: All types follow Firefly naming conventions
- 🚀 **Efficient**: Compiles to optimal JVM bytecode
- 🔒 **Type-safe**: Full compiler integration
- 📦 **Future-proof**: Can evolve independently of Java

See [docs/TYPE_SYSTEM.md](docs/TYPE_SYSTEM.md) for complete reference.

### 🚀 Async/Await (Built-in)

```fly path=null start=null
use com::firefly::runtime::async::Future

class Service {
  pub async fn fetchData(id: Int) -> String {
    // Simulated async work
    "data-" + id
  }

  pub fn processAll() -> Void {
    let f1: Future = self::fetchData(1);
    let f2: Future = self::fetchData(2);
    
    // Wait for all to complete
    Future::all(f1, f2)::get();
    
    println(f1::get() + ", " + f2::get());
  }
}
```

### 🔄 Pattern Matching

```fly path=null start=null
data Result { Ok(String), Err(Int) }

let r: Result = Result::Ok("success");

let msg: String = match r {
  Ok(value) => "Got: " + value,
  Err(code) => "Error " + code,
  _ => "unknown"
};
```

### ✨ Sparks (Smart Records)

```fly path=null start=null
spark Account {
  id: String,
  balance: Int,
  owner: String,

  // Validation enforced at construction
  validate { self.balance >= 0 }

  // Computed properties
  computed isActive: Bool { self.balance > 0 }
}

let acc: Account = Account { 
  id: "ACC001", 
  balance: 100, 
  owner: "Alice" 
};
```

### 🔗 Java Interop (Zero Ceremony)

```fly path=null start=null
use java::util::{ArrayList, Collections}
use java::lang::Math

class Demo {
  pub fn run() -> Void {
    let list: ArrayList = new ArrayList();
    list::add("banana");
    list::add("apple");
    Collections::sort(list);
    
    let max: Int = Math::max(10, 42);
    println("Max: " + max);
  }
}
```

### ⏱️ Timeouts

```fly path=null start=null
pub async fn slowTask() -> Int {
  Thread::sleep(500);
  42
}

pub fn withTimeout() -> Void {
  let result: Int = timeout(100) {
    slowTask().await
  };
  println("Result: " + result);
}
```

---

## Spring Boot Integration

Flylang has **first-class Spring Boot support** with zero compromises:

```fly path=null start=null
use org::springframework::boot::SpringApplication
use org::springframework::boot::autoconfigure::SpringBootApplication
use org::springframework::web::bind::annotation::{RestController, GetMapping}

@SpringBootApplication
class Application {
  pub fn fly(args: [String]) -> Void {
    SpringApplication::run(Application.class);
  }
}

@RestController
class HelloController {
  @GetMapping("/hello")
  pub fn hello() -> String {
    "Hello from Flylang + Spring Boot"
  }
}
```

**Features:**
- ✅ All standard annotations (`@RestController`, `@Autowired`, `@Transactional`)
- ✅ Jackson JSON mapping for structs and sparks
- ✅ JPA, Spring Data, Spring Security
- ✅ Native JVM performance (no reflection overhead)

📚 **Learn more:** [SPRING_BOOT_GUIDE.md](docs/SPRING_BOOT_GUIDE.md)

---

## Tooling

### CLI
```bash
fly version              # Show version and build info
fly compile <file.fly>   # Compile a single .fly file
fly run <file.fly>       # Compile and run a single .fly file
fly run <project-dir>    # Build and run a Maven project (auto-resolves runtime classpath)
fly test <project-dir>    # Run tests, prints concise summary (add --verbose for logs)
# Flags:
#   --verbose    Show full compiler logs / diagnostics
#   --no-clear   Do not clear the screen before running program output

# Utilities:
fly doctor               # Validate Java/Maven/Gradle availability and environment
fly check <file.fly>     # Validate syntax and types
fly repl                 # Start interactive REPL
```

### Maven Plugin
```xml
<plugin>
  <groupId>com.firefly</groupId>
  <artifactId>firefly-maven-plugin</artifactId>
  <version>1.0-Alpha</version>
  <executions>
    <execution>
      <goals>
        <goal>compile</goal>
      </goals>
    </execution>
  </executions>
</plugin>
```

### Editor Support

| Editor | Features | Installation |
|--------|----------|-------------|
| **VS Code** | Syntax highlighting, LSP (completion, hover, diagnostics), snippets | Build from `ide-plugins/vscode-firefly/` |
| **IntelliJ IDEA** | Syntax highlighting, LSP, templates | Build from `ide-plugins/intellij-firefly/` |

📚 **Setup guide:** [GETTING_STARTED.md → Editor Setup](docs/GETTING_STARTED.md#editor-setup)

---

## Examples

Each `examples/` folder is a standalone Maven project:

| Example | Description |
|---------|-------------|
| **hello-world** | Basic "Hello, World!" |
| **async-demo** | Async functions and `Future::get()` |
| **concurrency-demo** | Concurrent execution, race, timeouts |
| **futures-combinators-demo** | `Future::all`, `Future::any` |
| **patterns-demo** | Tuple and struct pattern matching |
| **data-patterns-demo** | Algebraic data types (ADTs) |
| **java-interop-advanced** | Collections, Time API, static calls |
| **sparks-demo** | Immutable smart records with validation |
| **async-pipeline-demo** | Parallel fan-out/fan-in with timeout |
| **spring-boot-demo** | REST API with Spring Boot integration |
| **enum-demo** | Enum declarations and usage |
| **task-manager-cli** | Command-line task manager app |
| **structured-concurrency-demo** | Structured concurrency with StructuredScope |

**Run any example:**
```bash
fly run examples/<example-name>
```

📚 **Full index:** [EXAMPLES.md](docs/EXAMPLES.md)

---

## Documentation

### 📘 For Users
- **[INTRODUCTION.md](docs/INTRODUCTION.md)** — Philosophy, comparison, learning paths
- **[GETTING_STARTED.md](docs/GETTING_STARTED.md)** — Install, first project, editors
- **[LANGUAGE_GUIDE.md](docs/LANGUAGE_GUIDE.md)** — Complete syntax and semantics
- **[RECIPES.md](docs/RECIPES.md)** — Copy-paste code snippets
- **[SPRING_BOOT_GUIDE.md](docs/SPRING_BOOT_GUIDE.md)** — Build REST APIs

### 📙 For Contributors
- **[ARCHITECTURE.md](docs/ARCHITECTURE.md)** — System design overview
- **[IMPLEMENTATION_GUIDE.md](docs/IMPLEMENTATION_GUIDE.md)** — Compiler internals

### 📚 Full Index
➡️ **[DOCS_INDEX.md](docs/DOCS_INDEX.md)** — Complete documentation map

---

## Roadmap

**v1.0-Alpha (Current):**
- ✅ Core language (classes, structs, sparks, data, async)
- ✅ Pattern matching, timeouts, futures
- ✅ Java interop, Spring Boot support
- ✅ Maven plugin, CLI, LSP, IDE plugins
- ✅ Full generics support for data/spark types
- ✅ Enum, trait, protocol codegen (via data declarations)
- ✅ Standard library expansion (validation module added)
- ✅ Structured concurrency primitives (StructuredScope)

**Upcoming:**
- 🔄 Advanced type inference improvements
- 🔄 Macro system for code generation
- 🔄 Native compilation via GraalVM
- 🔄 IDE plugin enhancements (refactoring, debugging)

📌 See [GitHub Issues](https://github.com/firefly-oss/firefly-lang/issues) for details.

---

## Contributing

We welcome contributions! Here's how to get started:

1. **Report bugs or request features:** Open a [GitHub Issue](https://github.com/firefly-oss/firefly-lang/issues)
2. **Submit improvements:** Fork, create a branch, submit a PR
3. **Discuss major changes:** Open a discussion before large refactors

**Development setup:** See [GETTING_STARTED.md → Build from Source](docs/GETTING_STARTED.md#installation)

---

## License

Apache License 2.0 © 2025 Firefly Software Solutions Inc.

See [LICENSE](LICENSE) for details.

---

<p align="center">
  <strong>Built with ❤️ for the JVM community</strong>
</p>

<p align="center">
  <a href="docs/GETTING_STARTED.md">Get Started</a> ·
  <a href="docs/LANGUAGE_GUIDE.md">Language Guide</a> ·
  <a href="docs/EXAMPLES.md">Examples</a> ·
  <a href="https://github.com/firefly-oss/firefly-lang">GitHub</a>
</p>
