# Firefly Syntax Reference

A comprehensive guide to Firefly's modern, developer-friendly syntax.

## Table of Contents

- [Basic Types](#basic-types)
- [Functions](#functions)
- [Data Types](#data-types)
- [Pattern Matching](#pattern-matching)
- [Expressions](#expressions)
- [Operators](#operators)
- [Collections](#collections)
- [Error Handling](#error-handling)
- [Actors](#actors)
- [Spring Boot Integration](#spring-boot-integration)

## Basic Types

```firefly
// Primitive types
Int          // 32-bit integer
Float        // 64-bit floating point
String       // UTF-8 string
Bool         // true or false
Unit         // void/empty type ()

// Optional types (null-safe)
String?      // Optional string
Int?         // Optional integer

// Array types
[Int]        // Array of integers
[String]     // Array of strings

// Map types
[String: Int]     // Map from String to Int
[Int: User]       // Map from Int to User

// Tuple types
(Int, String)           // 2-tuple
(String, Int, Bool)     // 3-tuple

// Function types
(Int, Int) -> Int       // Function taking two Ints, returning Int
() -> String            // Function taking no args, returning String
```

## Functions

### Function Declaration

```firefly
// Simple function
fn add(a: Int, b: Int) -> Int {
    a + b
}

// Single expression (no braces needed)
fn multiply(a: Int, b: Int) -> Int = a * b

// Function with default parameters
fn greet(name: String, greeting: String = "Hello") -> String {
    "{greeting}, {name}!"
}

// Generic function
fn identity<T>(value: T) -> T = value

fn map<T, R>(list: [T], f: (T) -> R) -> [R] {
    // Implementation
}

// Mutable parameter
fn increment(mut count: Int) {
    count = count + 1
}
```

### Lambda Expressions

```firefly
// Lambda syntax: |params| expression
|x| x * 2
|x, y| x + y
|x| {
    let result = x * 2;
    result + 1
}

// Usage with methods
numbers.map(|x| x * 2)
users.filter(|u| u.age >= 18)
```

## Data Types

### Structs (Product Types)

```firefly
// Basic struct
struct Point {
    x: Int,
    y: Int,
}

// Struct with default values
struct Config {
    host: String = "localhost",
    port: Int = 8080,
}

// Creating instances
let p1 = Point { x: 10, y: 20 }

// Shorthand (when variable names match)
let x = 5;
let y = 10;
let p2 = Point { x, y }

// Struct update syntax
let p3 = Point { x: 100, ..p1 }  // Copy p1, change x
```

### Data Types (Sum Types / ADTs)

```firefly
// Simple enum
data Status {
    Pending,
    Active,
    Inactive,
}

// Enum with data
data Option<T> {
    Some(T),
    None,
}

data Result<T, E> {
    Ok(T),
    Err(E),
}

// Complex enum
data Message {
    Text(String),
    Image(url: String, width: Int, height: Int),
    Video(url: String, duration: Int),
}

// Creating instances
let msg = Message::Text("Hello")
let img = Message::Image { 
    url: "pic.jpg", 
    width: 800, 
    height: 600 
}
```

### Traits and Implementations

```firefly
// Define a trait (interface)
trait Drawable {
    fn draw(self) -> String
    fn area(self) -> Float
}

// Implement trait for a type
impl Drawable for Circle {
    fn draw(self) -> String {
        "Drawing circle at ({self.x}, {self.y})"
    }
    
    fn area(self) -> Float {
        3.14159 * self.radius * self.radius
    }
}

// Implement methods without trait
impl User {
    fn new(username: String) -> User {
        User {
            id: uuid::generate(),
            username,
            created_at: now(),
        }
    }
    
    fn is_admin(self) -> Bool {
        self.role == Role::Admin
    }
}
```

## Pattern Matching

### Match Expression

```firefly
// Basic match
match status {
    Status::Pending => "Waiting",
    Status::Active => "Running",
    Status::Inactive => "Stopped",
}

// Match with binding
match result {
    Ok(value) => println("Success: {value}"),
    Err(error) => println("Error: {error}"),
}

// Match with destructuring
match message {
    Message::Text(content) => send_text(content),
    Message::Image { url, width, height } => {
        resize_image(url, width, height);
        send_image(url)
    },
    Message::Video { url, .. } => send_video(url),
}

// Match with guards
match user.age {
    age if age < 18 => "Minor",
    age if age < 65 => "Adult",
    _ => "Senior",
}

// Match tuples
match (x, y) {
    (0, 0) => "Origin",
    (0, _) => "Y-axis",
    (_, 0) => "X-axis",
    (x, y) => "Point at ({x}, {y})",
}

// Match arrays
match numbers {
    [] => "Empty",
    [x] => "Single: {x}",
    [x, y] => "Pair: {x}, {y}",
    [head, ..] => "Starts with {head}",
}
```

### Let Pattern Matching

```firefly
// Destructure struct
let Point { x, y } = point;

// Destructure tuple
let (first, second, third) = tuple;

// Destructure enum
let Some(value) = optional else {
    return;
};

// Mutable binding
let mut count = 0;
count = count + 1;
```

## Expressions

Everything in Firefly is an expression that returns a value.

### If Expression

```firefly
// If as expression
let status = if user.is_active() {
    "Active"
} else {
    "Inactive"
}

// Multiple conditions
let category = if score >= 90 {
    "A"
} else if score >= 80 {
    "B"
} else if score >= 70 {
    "C"
} else {
    "F"
}
```

### Block Expression

```firefly
// Block returns last expression
let result = {
    let x = compute_x();
    let y = compute_y();
    x + y  // No semicolon = return value
};

// Statements end with semicolon
let value = {
    println("Computing...");  // Statement
    42  // Expression (returned)
};
```

### Loop Expressions

```firefly
// For loop
for item in collection {
    process(item);
}

// With pattern matching
for (key, value) in map {
    println("{key}: {value}");
}

// While loop
while condition {
    do_work();
}

// Range iteration
for i in 0..10 {
    println(i);
}

for i in 0..=100 {  // Inclusive range
    println(i);
}
```

## Operators

### Standard Operators

```firefly
// Arithmetic
a + b        // Addition
a - b        // Subtraction
a * b        // Multiplication
a / b        // Division
a % b        // Modulo
-a           // Negation

// Comparison
a == b       // Equal
a != b       // Not equal
a < b        // Less than
a > b        // Greater than
a <= b       // Less or equal
a >= b       // Greater or equal

// Logical
!a           // Not
a && b       // And
a || b       // Or

// Bitwise
a & b        // Bitwise AND
a | b        // Bitwise OR
a ^ b        // Bitwise XOR
a << b       // Left shift
a >> b       // Right shift (also actor send)
```

### Special Operators

```firefly
// Null-safety operators
user?.email              // Safe navigation (returns String?)
user?.email ?? "unknown" // Null coalescing
user?.email!!            // Force unwrap (unsafe)

// Elvis operator
let name = user?.name ?: "Guest"

// Range operators
0..10       // Range 0 to 9 (exclusive end)
0..=10      // Range 0 to 10 (inclusive end)

// Actor message send
actor >> Message::New    // Send message to actor

// Method chaining
list.map(|x| x * 2)
    .filter(|x| x > 10)
    .sum()
```

## Collections

### Arrays

```firefly
// Array literals
let numbers = [1, 2, 3, 4, 5];
let empty: [Int] = [];

// Array access
let first = numbers[0];
let second = numbers[1];

// Array methods
numbers.length()
numbers.is_empty()
numbers.contains(3)
numbers.first()
numbers.last()

// Functional operations
numbers.map(|x| x * 2)
numbers.filter(|x| x > 10)
numbers.fold(0, |acc, x| acc + x)
numbers.reduce(|a, b| a + b)

// Array slicing
numbers[1..4]     // Elements 1, 2, 3
numbers[..5]      // First 5 elements
numbers[3..]      // From index 3 to end
```

### Maps

```firefly
// Map literals
let scores = [
    "Alice": 95,
    "Bob": 87,
    "Charlie": 92,
];

let empty: [String: Int] = [:];

// Map access
let alice_score = scores["Alice"]?;  // Returns Int?

// Map methods
scores.get("Alice")
scores.contains_key("Bob")
scores.keys()
scores.values()
scores.entries()

// Functional operations
scores.map(|k, v| v * 2)
scores.filter(|k, v| v >= 90)
```

### Tuples

```firefly
// Tuple literals
let pair = (1, "hello");
let triple = (42, true, "world");

// Tuple access
let (x, y) = pair;
let first = triple.0;
let second = triple.1;
```

## Error Handling

### Result Type

```firefly
// Define Result
data Result<T, E> {
    Ok(T),
    Err(E),
}

// Return Result
fn divide(a: Int, b: Int) -> Result<Int, String> {
    if b == 0 {
        Err("Division by zero")
    } else {
        Ok(a / b)
    }
}

// Handle Result
match divide(10, 2) {
    Ok(result) => println("Result: {result}"),
    Err(error) => println("Error: {error}"),
}

// ? operator for early return
fn calculate() -> Result<Int, String> {
    let a = divide(10, 2)?;  // Returns Err if division fails
    let b = divide(20, 4)?;
    Ok(a + b)
}
```

### Optional Type

```firefly
// Optional values
let name: String? = Some("Alice");
let empty: String? = None;

// Safe unwrapping
let length = name?.length();  // Returns Int?

// Unwrap with default
let n = name ?? "Unknown";

// Pattern matching
match name {
    Some(n) => println("Hello, {n}"),
    None => println("No name"),
}
```

## Actors

### Actor Definition and Usage

```firefly
// Define message type
data CounterMsg {
    Increment,
    Decrement,
    Get,
    Reset,
}

// Define actor state
struct CounterState {
    count: Int,
}

// Define actor trait
trait Counter {
    fn init(initial: Int) -> CounterState
    fn handle(msg: CounterMsg, state: CounterState) -> CounterState
}

// Implement actor
impl Counter {
    fn init(initial: Int) -> CounterState {
        CounterState { count: initial }
    }
    
    fn handle(msg: CounterMsg, state: CounterState) -> CounterState {
        match msg {
            Increment => CounterState { count: state.count + 1 },
            Decrement => CounterState { count: state.count - 1 },
            Get => {
                println("Count: {state.count}");
                state
            },
            Reset => CounterState { count: 0 },
        }
    }
}

// Use actor
fn main() {
    let system = ActorSystem::new();
    let counter = system.spawn(Counter::init(0));
    
    // Send messages
    counter >> CounterMsg::Increment;
    counter >> CounterMsg::Get;
    
    system.shutdown();
}
```

## Spring Boot Integration

### REST Controller

```firefly
@RestController("/api/users")
struct UserController {}

impl UserController {
    
    @GetMapping("/{id}")
    fn get_user(self, @PathVariable id: String) -> Result<User, ApiError> {
        match UserRepository::find_by_id(id) {
            Some(user) => Ok(user),
            None => Err(ApiError { 
                message: "User not found", 
                code: 404 
            }),
        }
    }
    
    @PostMapping
    fn create_user(self, @RequestBody request: CreateUserRequest) -> Result<User, ApiError> {
        let user = User {
            id: uuid::generate(),
            username: request.username,
            email: request.email,
        };
        
        UserRepository::save(user);
        Ok(user)
    }
}
```

### Service Layer

```firefly
@Service
struct UserService {}

impl UserService {
    fn find_active_users(self) -> [User] {
        UserRepository::find_all()
            .filter(|u| u.age >= 18)
            .sort_by(|u| u.username)
    }
}
```

## String Interpolation

```firefly
// Basic interpolation
let name = "Alice";
let greeting = "Hello, {name}!";

// Expression interpolation
let result = "2 + 2 = {2 + 2}";

// Property access
let user = User { name: "Bob", age: 30 };
let info = "User {user.name} is {user.age} years old";
```

## Comments

```firefly
// Single-line comment

/*
   Multi-line comment
   can span multiple lines
*/
```

## Imports and Packages

```firefly
// Package declaration
package com.example.app

// Import entire module
import std::collections

// Import specific items
import std::io::{println, read_line}

// Import with wildcard
import std::math::*

// Nested imports
import spring::web::{RestController, GetMapping, PostMapping}
```

## Type Aliases

```firefly
// Simple alias
type UserId = String
type Timestamp = Int

// Generic alias
type Result<T> = Result<T, String>
type HashMap<K, V> = [K: V]
```

This syntax is designed for **developer experience** - it's familiar (if you know Kotlin/Rust/Swift), safe (null-safety, exhaustive matching), and expressive (everything is an expression).
