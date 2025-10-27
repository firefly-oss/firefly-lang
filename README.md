# 🔥 Firefly Programming Language

**A modern, expressive programming language that compiles to JVM bytecode with seamless Java ecosystem integration.**

[![Java](https://img.shields.io/badge/Java-21+-orange.svg)](https://www.oracle.com/java/)
[![Maven](https://img.shields.io/badge/Maven-3.6+-blue.svg)](https://maven.apache.org/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.2-brightgreen.svg)](https://spring.io/projects/spring-boot)
[![License](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)

## 🎯 What is Firefly?

Firefly is a statically-typed programming language designed for developers who want:

- **Modern, clean syntax** inspired by Rust, Kotlin, and Swift
- **100% JVM compatibility** - runs on any JVM and integrates seamlessly with Java libraries
- **First-class Spring Boot support** - build production-ready microservices with native Spring annotations
- **Type safety without verbosity** - expressive type system with inference
- **Zero runtime overhead** - compiles directly to optimized JVM bytecode

## ⚡ Quick Start

### Installation

#### Quick Install (Recommended)

Install Firefly with a single command:

```bash
curl -fsSL https://raw.githubusercontent.com/firefly-oss/firefly-lang/main/install.sh | bash
```

Or download and inspect first:

```bash
curl -fsSL https://raw.githubusercontent.com/firefly-oss/firefly-lang/main/install.sh -o install.sh
chmod +x install.sh
./install.sh
```

#### Custom Installation

```bash
# Install to custom location (no sudo required)
curl -fsSL https://raw.githubusercontent.com/firefly-oss/firefly-lang/main/install.sh | bash -s -- --prefix ~/.local

# Install specific version
curl -fsSL https://raw.githubusercontent.com/firefly-oss/firefly-lang/main/install.sh | bash -s -- --branch v0.1.0
```

#### Manual Build

```bash
# Clone the repository
git clone https://github.com/firefly-oss/firefly-lang.git
cd firefly-lang

# Build and install
mvn clean install
./install.sh --clone-dir .
```

#### Requirements

- Java 21+ (JDK)
- Maven 3.6+
- Git

#### Uninstall

```bash
# Uninstall from default location
curl -fsSL https://raw.githubusercontent.com/firefly-oss/firefly-lang/main/uninstall.sh | bash

# Or if installed to custom prefix
curl -fsSL https://raw.githubusercontent.com/firefly-oss/firefly-lang/main/uninstall.sh | bash -s -- ~/.local
```

### Hello World

Create `hello.fly`:

```firefly
package hello

fn main(args: Array<String>) -> Unit {
    println("Hello, Firefly! 🔥");
}
```

Compile and run:

```bash
# Compile
firefly compile hello.fly

# Run
java -cp . hello.Main
```

Output:
```
Hello, Firefly! 🔥
```

#### Verify Installation

```bash
# Check version
firefly --version

# Get help
firefly --help
```

## 🚀 Key Features

### Modern Syntax

```firefly
// Type inference
let name = "Firefly";
let count = 42;

// Pattern matching
match status {
    Ok(value) -> println("Success: " + value),
    Error(msg) -> println("Error: " + msg)
}

// Functions as first-class citizens
fn map<T, R>(list: List<T>, fn: (T) -> R) -> List<R> {
    // ...
}
```

### Spring Boot Integration

```firefly
package com.example

import org::springframework::boot::SpringApplication
import org::springframework::boot::autoconfigure::SpringBootApplication
import org::springframework::web::bind::annotation::*

@SpringBootApplication
class Application {
    fn main(args: Array<String>) -> Unit {
        SpringApplication.run(Application.class, args);
    }
}

@RestController
@RequestMapping("/api")
class UserController {
    @GetMapping("/users/{id}")
    fn getUser(@PathVariable id: String) -> User {
        return User(id, "John Doe");
    }
}
```

### Java Interop

```firefly
// Use any Java library seamlessly
import java::util::ArrayList
import java::util::stream::Collectors

fn processData(items: List<String>) -> List<String> {
    let list = ArrayList<String>();
    items.forEach(item -> list.add(item.toUpperCase()));
    return list;
}
```

## 📚 Documentation

### Getting Started

- **[Quick Start](#-quick-start)** - Install and run your first program
- **[Hello World Example](#hello-world)** - Your first Firefly program
- **[Full Language Guide](GUIDE.md)** - Complete tutorial and reference (recommended start)

### Language Reference

- **[Language Guide](GUIDE.md)** - Comprehensive language tutorial
  - Variables, functions, and control flow
  - Classes, interfaces, and OOP
  - Pattern matching and advanced features
  - Best practices and idioms
  
- **[Syntax Reference](SYNTAX.md)** - Quick lookup for syntax
  - Variables and types
  - Functions and methods
  - Classes and interfaces
  - Control flow
  - Annotations

- **[Implementation Status](STATUS.md)** - Current feature status
  - What's implemented and working
  - What's in progress
  - Roadmap and future plans

### Integration Guides

- **[Spring Boot Integration](docs/SPRING_BOOT_INTEGRATION.md)** - Technical deep dive
  - Architecture and design
  - Classloader integration
  - Method resolution
  - Maven plugin usage
  - Testing procedures

- **[Git Setup](docs/GIT_SETUP.md)** - Version control configuration
  - Repository setup
  - Branch strategy
  - CI/CD integration

### Examples & Tutorials

- **[Examples Directory](examples/)** - Working code samples
  - [Hello World](examples/hello-world/) - Basic syntax
  - [Basic Syntax](examples/basic-syntax/) - Language features
  - [Spring Boot Demo](examples/spring-boot/) - Complete REST API
  - [More examples...](examples/)

### Project Information

- **[Documentation Summary](docs/DOCUMENTATION_SUMMARY.md)** - Documentation overview
- **[Contributing Guide](CONTRIBUTING.md)** - How to contribute

## 🏗️ Project Structure

```
firefly-lang/
├── firefly-compiler/       # Compiler implementation
│   ├── lexer & parser     # ANTLR-based parsing
│   ├── AST                # Abstract Syntax Tree
│   ├── semantic analysis  # Type checking & validation
│   └── codegen            # JVM bytecode generation
├── firefly-maven-plugin/  # Maven integration
├── firefly-runtime/       # Runtime library
└── examples/              # Code examples
    ├── hello-world/
    ├── basic-syntax/
    └── spring-boot/
```

## 🎓 Examples

### Hello World

```firefly
package examples

fn main(args: Array<String>) -> Unit {
    println("Hello, Firefly! 🔥");
}
```

[Full example →](examples/hello-world/)

### REST API with Spring Boot

```firefly
@RestController
@RequestMapping("/api")
class ProductController {
    @GetMapping("/products")
    fn getAllProducts() -> List<Product> {
        return productService.findAll();
    }
    
    @PostMapping("/products")
    fn createProduct(@RequestBody product: Product) -> Product {
        return productService.save(product);
    }
}
```

[Full example →](examples/spring-boot/)

### Data Classes and Pattern Matching

```firefly
data class Result<T> {
    value: T,
    error: String?
}

fn processResult<T>(result: Result<T>) -> String {
    match result.error {
        None -> "Success: " + result.value,
        Some(err) -> "Error: " + err
    }
}
```

[More examples →](examples/)

## 🔧 Maven Integration

Add Firefly compilation to your Maven project:

```xml
<build>
    <plugins>
        <plugin>
            <groupId>com.firefly</groupId>
            <artifactId>firefly-maven-plugin</artifactId>
            <version>0.4.0</version>
            <executions>
                <execution>
                    <phase>process-classes</phase>
                    <goals>
                        <goal>compile</goal>
                    </goals>
                </execution>
            </executions>
        </plugin>
    </plugins>
</build>
```

Place your `.fly` files in `src/main/firefly/` and they'll be compiled automatically.

## 🧪 Testing

Run the test suite:

```bash
mvn clean test
```

## 🗺️ Roadmap

### ✅ Completed (v0.1)

- [x] Complete lexer and parser
- [x] AST construction and visitor pattern
- [x] Semantic analysis with type checking
- [x] JVM bytecode generation
- [x] Spring Boot integration
- [x] Maven plugin
- [x] Static method resolution
- [x] Annotation support

### 🚧 In Progress (v0.2)

- [ ] Generics support
- [ ] Pattern matching implementation
- [ ] Trait system
- [ ] Standard library expansion
- [ ] IDE support (VS Code extension)

### 🔮 Future (v0.3+)

- [ ] Null safety
- [ ] Coroutines/async-await
- [ ] Module system
- [ ] Package manager
- [ ] REPL

## 🤝 Contributing

We welcome contributions! Please see our [Contributing Guide](CONTRIBUTING.md) for details.

### Development Setup

```bash
# Clone and build
git clone https://github.com/firefly-oss/firefly-lang.git
cd firefly-lang
mvn clean install

# Run tests
mvn test

# Build documentation
mvn site
```

## 📖 Learn More

- [Language Guide](GUIDE.md) - Complete language documentation
- [Spring Boot Tutorial](examples/spring-boot/README.md) - Build microservices
- [API Reference](docs/api/) - Runtime API documentation

## 📄 License

Firefly is released under the [MIT License](LICENSE).

## 🙏 Acknowledgments

Firefly draws inspiration from:
- **Rust** - Modern syntax and type system
- **Kotlin** - JVM integration patterns
- **Swift** - Clean, expressive syntax
- **Scala** - Functional programming on the JVM

## 💬 Community

- **Website**: [getfirefly.io](https://getfirefly.io)
- **GitHub**: [firefly-oss](https://github.com/firefly-oss)
- **Issues**: [GitHub Issues](https://github.com/firefly-oss/firefly-lang/issues)
- **Discussions**: [GitHub Discussions](https://github.com/firefly-oss/firefly-lang/discussions)

---

**Developed by [Firefly Software Solutions Inc.](https://getfirefly.io) 🔥**
