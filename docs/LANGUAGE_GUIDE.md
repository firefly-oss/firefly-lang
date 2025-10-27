# Firefly Language Guide

## Table of Contents
1. [Introduction](#introduction)
2. [Getting Started](#getting-started)
3. [Basic Syntax](#basic-syntax)
4. [Functions](#functions)
5. [Variables](#variables)
6. [Types](#types)
7. [Operators](#operators)
8. [Control Flow](#control-flow)
9. [Data Structures](#data-structures)
10. [Concurrency](#concurrency)
11. [Error Handling](#error-handling)
12. [Advanced Features](#advanced-features)

---

## Introduction

Firefly is a modern, statically-typed programming language that compiles to JVM bytecode. It combines the best features of functional and imperative programming with first-class concurrency support.

### Key Features
- **Type Safety**: Strong static typing with type inference
- **Concurrency**: Built-in concurrent, race, and timeout constructs
- **Pattern Matching**: Powerful pattern matching for control flow
- **Immutability**: Immutable by default, explicit mutability
- **JVM Integration**: Seamless Java interoperability

---

## Getting Started

### Installation

```bash
# Clone the repository
git clone https://github.com/yourusername/firefly-lang.git
cd firefly-lang

# Build the compiler
mvn clean install

# Add to PATH (optional)
export PATH=$PATH:$(pwd)/firefly-cli/target
```

### Your First Program

Create a file `hello.fly`:

```firefly
fn main() {
    let message = "Hello, Firefly!";
}
```

Compile and run:

```bash
firefly compile hello.fly
java -cp . Main
```

---

## Basic Syntax

### Comments

```firefly
// Single-line comment

/*
Multi-line comment
spanning multiple lines
*/
```

### Statements and Expressions

Firefly distinguishes between statements (no value) and expressions (produce value):

```firefly
// Statement (requires semicolon)
let x = 42;

// Expression (last value in block)
fn getValue() -> Int {
    42  // No semicolon - this is returned
}
```

---

## Functions

### Function Declaration

```firefly
fn functionName(param1: Type1, param2: Type2) -> ReturnType {
    // Function body
    result
}
```

### Examples

```firefly
// Simple function
fn add(a: Int, b: Int) -> Int {
    a + b
}

// Function without parameters
fn getPI() -> Float {
    3.14159
}

// Function without return value (returns Unit)
fn printMessage(msg: String) {
    println(msg);
}

// Function with multiple statements
fn calculate(x: Int, y: Int) -> Int {
    let sum = x + y;
    let product = x * y;
    sum + product
}
```

### Calling Functions

```firefly
let result = add(5, 3);
let pi = getPI();
printMessage("Hello");
```

---

## Variables

### Immutable Variables (let)

```firefly
let name = "Alice";
let age = 30;
let price = 99.99;
```

### Mutable Variables (let mut)

```firefly
let mut counter = 0;
counter = counter + 1;
counter = counter + 1;
```

### Type Annotations

```firefly
let x: Int = 42;
let name: String = "Bob";
let isActive: Bool = true;
```

### Shadowing

```firefly
let x = 5;
let x = x + 1;  // New variable, shadows previous
let x = "now a string";  // Different type allowed
```

---

## Types

### Primitive Types

```firefly
let integer: Int = 42;
let floating: Float = 3.14;
let text: String = "Hello";
let flag: Bool = true;
let character: Char = 'A';
```

### Optional Types

```firefly
let maybeNumber: Int? = Some(42);
let nothing: String? = None;

// Unwrap with ?
let value = maybeNumber?;
```

### Array Types

```firefly
let numbers: [Int] = [1, 2, 3, 4, 5];
let names: [String] = ["Alice", "Bob", "Charlie"];
```

### Function Types

```firefly
let operation: (Int, Int) -> Int = add;
let result = operation(5, 3);
```

---

## Operators

### Arithmetic

```firefly
let sum = 10 + 5;        // 15
let difference = 10 - 5;  // 5
let product = 10 * 5;     // 50
let quotient = 10 / 5;    // 2
let remainder = 10 % 3;   // 1
```

### Comparison

```firefly
let equal = 5 == 5;           // true
let notEqual = 5 != 3;        // true
let lessThan = 3 < 5;         // true
let lessOrEqual = 5 <= 5;     // true
let greaterThan = 5 > 3;      // true
let greaterOrEqual = 5 >= 5;  // true
```

### Logical

```firefly
let and = true && false;   // false
let or = true || false;    // true
let not = !true;           // false
```

### Precedence

```firefly
let result = 2 + 3 * 4;      // 14 (not 20)
let result2 = (2 + 3) * 4;   // 20
```

---

## Control Flow

### if/else

```firefly
if condition {
    // Code if true
} else {
    // Code if false
}

// if as expression
let value = if x > 0 {
    1
} else {
    -1
};
```

### Nested Conditionals

```firefly
if score >= 90 {
    "A"
} else {
    if score >= 80 {
        "B"
    } else {
        if score >= 70 {
            "C"
        } else {
            "F"
        }
    }
}
```

### while Loop

```firefly
let mut counter = 0;
while counter < 10 {
    counter = counter + 1;
}
```

### for Loop

```firefly
for i in 0..10 {
    // Process i
}

for item in array {
    // Process item
}
```

### break and continue

```firefly
while true {
    if condition {
        break;  // Exit loop
    }
    if skipThis {
        continue;  // Skip to next iteration
    }
}
```

---

## Data Structures

### Structs

```firefly
struct Person {
    name: String,
    age: Int,
    email: String
}

// Create instance
let person = Person {
    name: "Alice",
    age: 30,
    email: "alice@example.com"
};

// Access fields
let name = person.name;
```

### Data Types (Enums)

```firefly
data Result<T, E> {
    Ok(T),
    Err(E)
}

data Option<T> {
    Some(T),
    None
}

// Usage
let success: Result<Int, String> = Result::Ok(42);
let failure: Result<Int, String> = Result::Err("error");
```

### Pattern Matching

```firefly
match value {
    0 => "zero",
    1 => "one",
    2 => "two",
    _ => "other"
}

match result {
    Result::Ok(value) => handleSuccess(value),
    Result::Err(error) => handleError(error)
}
```

---

## Concurrency

### Async Functions

```firefly
async fn fetchData(id: Int) -> String {
    let response = httpGet("/api/data/{id}").await;
    response.body
}
```

### Concurrent Execution

```firefly
concurrent {
    let result1 = fetchData(1).await,
    let result2 = fetchData(2).await,
    let result3 = fetchData(3).await
}
```

### Race for First Result

```firefly
race {
    fetchFromCache().await,
    fetchFromDB().await,
    fetchFromAPI().await
}
```

### Timeout Protection

```firefly
timeout(5000) {
    slowOperation().await
}
```

---

## Error Handling

### Result Type

```firefly
fn divide(a: Int, b: Int) -> Result<Int, String> {
    if b == 0 {
        Result::Err("Division by zero")
    } else {
        Result::Ok(a / b)
    }
}
```

### Error Propagation

```firefly
fn process() -> Result<Int, String> {
    let value = divide(10, 2)?;  // Early return on error
    Result::Ok(value * 2)
}
```

### Option Type

```firefly
fn findUser(id: Int) -> Option<User> {
    if userExists(id) {
        Option::Some(getUser(id))
    } else {
        Option::None
    }
}
```

---

## Advanced Features

### Traits

```firefly
trait Printable {
    fn print(self);
}

impl Printable for Person {
    fn print(self) {
        println("Name: {self.name}, Age: {self.age}");
    }
}
```

### Generic Functions

```firefly
fn identity<T>(value: T) -> T {
    value
}

fn swap<T>(a: T, b: T) -> (T, T) {
    (b, a)
}
```

### Lambda Expressions

```firefly
let add = |a, b| a + b;
let result = add(5, 3);

// With explicit types
let multiply: (Int, Int) -> Int = |a, b| a * b;
```

### Method Chaining

```firefly
let result = numbers
    .filter(|n| n > 0)
    .map(|n| n * 2)
    .reduce(0, |acc, n| acc + n);
```

---

## Best Practices

### 1. Use Immutability by Default

```firefly
// Good
let x = 42;

// Only when necessary
let mut counter = 0;
```

### 2. Prefer Expressions over Statements

```firefly
// Good - expression
let value = if condition { 1 } else { 0 };

// Less idiomatic - statement
let mut value = 0;
if condition {
    value = 1;
}
```

### 3. Use Pattern Matching

```firefly
// Good
match result {
    Result::Ok(value) => process(value),
    Result::Err(error) => handleError(error)
}

// Less idiomatic
if result.isOk() {
    process(result.unwrap());
} else {
    handleError(result.error());
}
```

### 4. Handle Errors Explicitly

```firefly
// Good
match divide(10, 0) {
    Result::Ok(value) => println(value),
    Result::Err(error) => println("Error: {error}")
}

// Avoid
let value = divide(10, 0).unwrap();  // May panic!
```

---

## Common Patterns

### Builder Pattern

```firefly
struct UserBuilder {
    name: String?,
    age: Int?,
    email: String?
}

impl UserBuilder {
    fn name(mut self, name: String) -> UserBuilder {
        self.name = Some(name);
        self
    }
    
    fn build(self) -> Result<User, String> {
        // Validate and build
    }
}
```

### Visitor Pattern

```firefly
trait Visitor {
    fn visit_node(self, node: Node);
}

fn accept<V: Visitor>(visitor: V) {
    visitor.visit_node(self);
}
```

---

## Next Steps

- Read the [Concurrency Guide](CONCURRENCY.md)
- Explore [Example Programs](../examples/)
- Check out [Spring Boot Integration](SPRING_BOOT.md)
- Join the [Community](https://github.com/yourusername/firefly-lang)

---

**Version**: 0.1.0  
**Last Updated**: October 26, 2025
