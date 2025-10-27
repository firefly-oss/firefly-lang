# Firefly Examples

Complete, runnable examples demonstrating Firefly language features.

## Available Examples

### 1. [Hello World](hello-world/)
**Difficulty:** Beginner  
**Topics:** Basic syntax, functions, println

The classic "Hello World" program in Firefly. Perfect for getting started.

```firefly
fn main(args: Array<String>) -> Unit {
    println("Hello, Firefly! 🔥");
}
```

[View example →](hello-world/)

---

### 2. [Spring Boot REST API](spring-boot/)
**Difficulty:** Intermediate  
**Topics:** Spring Boot, REST, annotations, dependency injection

A complete Spring Boot microservice with REST endpoints, demonstrating production-ready Firefly code.

Features:
- REST controllers with CRUD operations
- Spring Boot annotations
- Dependency injection
- Maven integration
- Automated testing

```firefly
@RestController
@RequestMapping("/api")
class UserController {
    @GetMapping("/users/{id}")
    fn getUser(@PathVariable id: String) -> User {
        return userService.findById(id);
    }
}
```

[View example →](spring-boot/)

---

### 3. [Basic Syntax](basic-syntax/)
**Difficulty:** Beginner  
**Topics:** Variables, control flow, functions, classes

Comprehensive examples of Firefly syntax including variables, loops, conditionals, and basic OOP.

Topics covered:
- Variables and types
- Control flow (if, for, while)
- Functions
- Classes and methods
- Pattern matching

[View example →](basic-syntax/)

---

## Running Examples

### Prerequisites
- Java 21+
- Maven 3.6+
- Firefly compiler installed

### Quick Start

```bash
# Clone the repository
git clone https://github.com/firefly-oss/firefly-lang.git
cd firefly-lang

# Build the compiler
mvn clean install

# Run an example
cd examples/hello-world
firefly compile hello.fly
java -cp . hello.Main
```

### Using Maven

For examples with `pom.xml` (like Spring Boot):

```bash
cd examples/spring-boot
mvn clean package
java -jar target/*.jar
```

---

## Learning Path

**New to Firefly?** Follow this path:

1. **Start with [Hello World](hello-world/)** - Learn basic syntax
2. **Explore [Basic Syntax](basic-syntax/)** - Master language features  
3. **Build with [Spring Boot](spring-boot/)** - Create real applications

---

## Contributing Examples

Have a great example to share? We'd love to include it!

1. Create a new directory under `examples/`
2. Include a `README.md` explaining the example
3. Add complete, runnable code
4. Submit a pull request

---

## Getting Help

- **Website**: [getfirefly.io](https://getfirefly.io)
- **Documentation**: [Language Guide](../GUIDE.md)
- **Issues**: [GitHub Issues](https://github.com/firefly-oss/firefly-lang/issues)
- **Discussions**: [GitHub Discussions](https://github.com/firefly-oss/firefly-lang/discussions)
