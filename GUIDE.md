# Firefly Language Guide

**Complete reference and tutorial for the Firefly programming language**

## Table of Contents

1. [Introduction](#introduction)
2. [Getting Started](#getting-started)
3. [Basic Syntax](#basic-syntax)
4. [Type System](#type-system)
5. [Functions](#functions)
6. [Classes and Objects](#classes-and-objects)
7. [Pattern Matching](#pattern-matching)
8. [Java Interoperability](#java-interoperability)
9. [Spring Boot Integration](#spring-boot-integration)
10. [Advanced Topics](#advanced-topics)

---

## Introduction

### What Makes Firefly Different?

Firefly is designed to bring modern language features to the JVM while maintaining 100% compatibility with the Java ecosystem. Unlike other JVM languages:

- **No runtime dependency** - Compiles directly to standard JVM bytecode
- **Native Spring Boot support** - Use Spring annotations and features directly
- **Zero overhead** - Performance identical to Java
- **Gradual adoption** - Mix Firefly and Java in the same project

### Value Proposition

**For Java Developers:**
- Modern syntax without leaving the JVM
- Type inference reduces boilerplate
- Better expressiveness for common patterns
- Seamless integration with existing Java code

**For Kotlin/Scala Developers:**
- Simpler, more focused language
- No complex type system to learn
- Faster compilation times
- Direct bytecode generation

**For Backend Engineers:**
- First-class Spring Boot support
- Production-ready from day one
- Familiar tooling (Maven, Gradle)
- Enterprise-friendly

---

## Getting Started

### Installation

#### Prerequisites
- Java 21 or later
- Maven 3.6 or later

#### Build from Source

```bash
git clone https://github.com/firefly-oss/firefly-lang.git
cd firefly-lang
mvn clean install
```

#### Verify Installation

```bash
firefly --version
# Firefly 0.1.0-SNAPSHOT
```

### Your First Program

Create `hello.fly`:

```firefly
package hello

fn main(args: Array<String>) -> Unit {
    println("Hello, Firefly! 🔥");
}
```

Compile and run:

```bash
firefly compile hello.fly
java -cp . hello.Main
```

---

## Basic Syntax

### Variables

```firefly
// Immutable variables (default)
let name = "Firefly";
let count = 42;

// Mutable variables
let mut counter = 0;
counter = counter + 1;

// Type annotations (optional)
let message: String = "Hello";
let numbers: Array<Int> = [1, 2, 3];
```

### Primitive Types

```firefly
let integer: Int = 42;
let floating: Float = 3.14;
let boolean: Boolean = true;
let text: String = "Hello";
let nothing: Unit = ();
```

### Operators

```firefly
// Arithmetic
let sum = 10 + 5;
let diff = 10 - 5;
let product = 10 * 5;
let quotient = 10 / 5;
let remainder = 10 % 3;

// Comparison
let equal = (10 == 10);
let notEqual = (10 != 5);
let greater = (10 > 5);
let less = (5 < 10);

// Logical
let and = true && false;
let or = true || false;
let not = !true;
```

### Control Flow

#### If Expressions

```firefly
let age = 18;

if (age >= 18) {
    println("Adult");
} else {
    println("Minor");
}

// If as expression
let status = if (age >= 18) "adult" else "minor";
```

#### For Loops

```firefly
// Iterate over ranges
for (i in 0..10) {
    println(i);
}

// Iterate over collections
let names = ["Alice", "Bob", "Charlie"];
for (name in names) {
    println(name);
}
```

#### While Loops

```firefly
let mut count = 0;
while (count < 10) {
    println(count);
    count = count + 1;
}
```

---

## Type System

### Type Inference

Firefly infers types when possible:

```firefly
let name = "Firefly";        // inferred as String
let count = 42;              // inferred as Int
let list = [1, 2, 3];        // inferred as Array<Int>
```

### Arrays

```firefly
// Array literal
let numbers = [1, 2, 3, 4, 5];

// Array with type annotation
let names: Array<String> = ["Alice", "Bob"];

// Access elements
let first = numbers[0];

// Iterate
for (num in numbers) {
    println(num);
}
```

### Optional Types

```firefly
// Optional values (planned feature)
let maybeValue: String? = None;
let definiteValue: String? = Some("Hello");

// Pattern matching on optionals
match maybeValue {
    None -> println("No value"),
    Some(value) -> println("Value: " + value)
}
```

---

## Functions

### Basic Functions

```firefly
// Simple function
fn greet(name: String) -> String {
    return "Hello, " + name;
}

// Function with multiple parameters
fn add(a: Int, b: Int) -> Int {
    return a + b;
}

// Unit return type (void)
fn logMessage(message: String) -> Unit {
    println(message);
}
```

### Function Expressions

```firefly
// Functions as values
let adder = fn(a: Int, b: Int) -> Int {
    return a + b;
};

let result = adder(10, 20);
```

### Higher-Order Functions

```firefly
// Function that takes a function
fn applyTwice(value: Int, fn: (Int) -> Int) -> Int {
    return fn(fn(value));
}

let double = fn(x: Int) -> Int { return x * 2; };
let result = applyTwice(5, double); // 20
```

---

## Classes and Objects

### Basic Classes

```firefly
class Person {
    name: String,
    age: Int
    
    fn greet() -> String {
        return "Hello, I'm " + self.name;
    }
}

// Create instance
let person = Person { name: "Alice", age: 30 };
println(person.greet());
```

### Constructors

```firefly
class Rectangle {
    width: Int,
    height: Int
    
    // Constructor
    fn new(width: Int, height: Int) -> Rectangle {
        return Rectangle { width: width, height: height };
    }
    
    fn area() -> Int {
        return self.width * self.height;
    }
}

let rect = Rectangle.new(10, 20);
```

### Data Classes

```firefly
// Data classes with automatic equality and toString
data class Point {
    x: Int,
    y: Int
}

let p1 = Point { x: 10, y: 20 };
let p2 = Point { x: 10, y: 20 };
println(p1 == p2); // true
```

### Annotations

```firefly
// Classes can have annotations
@Component
class UserService {
    fn findUser(id: String) -> User {
        // ...
    }
}

// Methods can have annotations
@GetMapping("/users")
fn getUsers() -> List<User> {
    // ...
}

// Parameters can have annotations
fn updateUser(@PathVariable id: String, @RequestBody user: User) -> User {
    // ...
}
```

---

## Pattern Matching

### Basic Match Expressions

```firefly
let number = 42;

match number {
    0 -> println("Zero"),
    1 -> println("One"),
    42 -> println("The answer"),
    _ -> println("Something else")
}
```

### Matching with Values

```firefly
fn describe(value: Int) -> String {
    return match value {
        0 -> "zero",
        1 -> "one",
        2 -> "two",
        _ -> "many"
    };
}
```

### Pattern Matching on Types

```firefly
data class Result<T> {
    value: T?,
    error: String?
}

fn handleResult<T>(result: Result<T>) -> String {
    return match (result.error, result.value) {
        (None, Some(v)) -> "Success: " + v,
        (Some(e), None) -> "Error: " + e,
        _ -> "Invalid result"
    };
}
```

---

## Java Interoperability

### Calling Java Code

```firefly
import java::util::ArrayList
import java::util::HashMap

fn useJavaCollections() -> Unit {
    // Use Java ArrayList
    let list = ArrayList<String>();
    list.add("Hello");
    list.add("World");
    
    // Use Java HashMap
    let map = HashMap<String, Int>();
    map.put("age", 30);
    map.put("year", 2024);
}
```

### Using Java Libraries

```firefly
import java::time::LocalDateTime
import java::time::format::DateTimeFormatter

fn formatCurrentTime() -> String {
    let now = LocalDateTime.now();
    let formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    return now.format(formatter);
}
```

### Calling Static Methods

```firefly
import java::lang::Math

fn calculateDistance(x: Float, y: Float) -> Float {
    return Math.sqrt(x * x + y * y);
}
```

---

## Spring Boot Integration

### Basic Spring Boot Application

```firefly
package com.example.demo

import org::springframework::boot::SpringApplication
import org::springframework::boot::autoconfigure::SpringBootApplication

@SpringBootApplication
class Application {
    fn main(args: Array<String>) -> Unit {
        SpringApplication.run(Application.class, args);
    }
}
```

### REST Controllers

```firefly
import org::springframework::web::bind::annotation::*
import org::springframework::stereotype::*

@RestController
@RequestMapping("/api")
class UserController {
    
    @GetMapping("/users")
    fn getAllUsers() -> List<User> {
        return userService.findAll();
    }
    
    @GetMapping("/users/{id}")
    fn getUser(@PathVariable id: String) -> User {
        return userService.findById(id);
    }
    
    @PostMapping("/users")
    fn createUser(@RequestBody user: User) -> User {
        return userService.save(user);
    }
    
    @PutMapping("/users/{id}")
    fn updateUser(
        @PathVariable id: String,
        @RequestBody user: User
    ) -> User {
        return userService.update(id, user);
    }
    
    @DeleteMapping("/users/{id}")
    fn deleteUser(@PathVariable id: String) -> Unit {
        userService.delete(id);
    }
}
```

### Dependency Injection

```firefly
@Service
class UserService {
    userRepository: UserRepository
    
    @Autowired
    fn new(userRepository: UserRepository) -> UserService {
        return UserService { userRepository: userRepository };
    }
    
    fn findAll() -> List<User> {
        return userRepository.findAll();
    }
}
```

### Configuration

```firefly
@Configuration
class AppConfig {
    
    @Bean
    fn objectMapper() -> ObjectMapper {
        let mapper = ObjectMapper();
        mapper.configure(SerializationFeature.INDENT_OUTPUT, true);
        return mapper;
    }
}
```

---

## Advanced Topics

### Generics (Planned)

```firefly
// Generic functions
fn identity<T>(value: T) -> T {
    return value;
}

// Generic classes
class Box<T> {
    value: T
    
    fn get() -> T {
        return self.value;
    }
}
```

### Traits (Planned)

```firefly
// Define a trait
trait Drawable {
    fn draw() -> Unit;
}

// Implement trait
impl Drawable for Circle {
    fn draw() -> Unit {
        println("Drawing circle");
    }
}
```

### Error Handling

```firefly
// Result type for error handling
fn divide(a: Int, b: Int) -> Result<Int, String> {
    if (b == 0) {
        return Error("Division by zero");
    } else {
        return Ok(a / b);
    }
}

// Handle results
let result = divide(10, 2);
match result {
    Ok(value) -> println("Result: " + value),
    Error(msg) -> println("Error: " + msg)
}
```

---

## Best Practices

### Code Organization

```firefly
// One package per file
package com.example.users

// Import at the top
import org::springframework::web::bind::annotation::*
import com.example.common::*

// Classes and functions below
class UserController {
    // ...
}
```

### Naming Conventions

```firefly
// Classes: PascalCase
class UserService { }

// Functions and variables: camelCase
fn getUserById(id: String) -> User { }
let userName = "Alice";

// Constants: UPPER_SNAKE_CASE
let MAX_RETRY_COUNT = 3;
```

### Type Annotations

```firefly
// Use explicit types for public APIs
fn processUser(user: User) -> Result<User, String> {
    // ...
}

// Type inference is fine for local variables
let name = user.getName();
let age = calculateAge(user.getBirthDate());
```

---

## Tooling

### Maven Plugin

Configure in `pom.xml`:

```xml
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
```

Place `.fly` files in `src/main/firefly/`.

### Command Line Compiler

```bash
# Compile single file
firefly compile myfile.fly

# Compile with output directory
firefly compile myfile.fly -o target/classes

# Compile multiple files
firefly compile src/**/*.fly -o target/classes
```

---

## What's Implemented

### ✅ Currently Available

- **Complete syntax**: variables, functions, classes, control flow
- **Type system**: primitives, arrays, type inference
- **JVM bytecode generation**: optimized, production-ready
- **Spring Boot**: full annotation support, REST controllers
- **Java interop**: seamless Java library integration
- **Maven plugin**: automatic compilation in build pipeline
- **Static methods**: correct resolution and invocation
- **Annotations**: full support with proper FQN resolution

### 🚧 In Development

- **Generics**: Full generic type support
- **Pattern matching**: Comprehensive pattern matching
- **Traits**: Trait system similar to Rust
- **Standard library**: Growing collection of utilities

### 🔮 Planned

- **Null safety**: Compile-time null checking
- **Async/await**: Coroutine support
- **Module system**: Better code organization
- **REPL**: Interactive development
- **IDE support**: VS Code, IntelliJ plugins

---

## Examples

For complete, working examples, see:

- [Hello World](examples/hello-world/) - Basic syntax and compilation
- [Spring Boot REST API](examples/spring-boot/) - Production microservice
- [Java Interop](examples/java-interop/) - Using Java libraries

---

## Getting Help

- **Website**: [getfirefly.io](https://getfirefly.io)
- **Documentation**: This guide and [README.md](README.md)
- **Issues**: [GitHub Issues](https://github.com/firefly-oss/firefly-lang/issues)
- **Discussions**: [GitHub Discussions](https://github.com/firefly-oss/firefly-lang/discussions)

---

**Next Steps**: Check out the [examples](examples/) directory for complete, runnable code samples.
