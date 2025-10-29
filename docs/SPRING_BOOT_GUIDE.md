# Spring Boot Guide for Flylang

A comprehensive, practical guide to building REST APIs with Spring Boot using Flylang. All examples are validated in this repository.

---

## Table of Contents
- Overview and Prerequisites
- Project Setup (Maven)
- Application Entry Point
- Controllers and Mappings
- Structs and JSON Serialization
- Path Variables and Query Parameters
- Complete Example (CRUD)
- Build, Run, and Packaging
- Troubleshooting
- Known Limitations
- See Also

## Overview and Prerequisites
Flylang integrates cleanly with Spring Boot via native Java annotations and JVM bytecode that matches Spring’s expectations. You’ll need:
- Java 21+
- Maven 3.8+
- Dependencies: `spring-boot-starter-web`, `jackson-module-parameter-names`

## Project Setup (Maven)
Directory layout:
```
src/main/firefly/
  com/example/
    Application.fly
pom.xml
```
POM essentials:
```xml
<dependencies>
  <dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-web</artifactId>
    <version>3.2.0</version>
  </dependency>
  <dependency>
    <groupId>com.fasterxml.jackson.module</groupId>
    <artifactId>jackson-module-parameter-names</artifactId>
  </dependency>
</dependencies>

<build>
  <plugins>
    <plugin>
      <groupId>com.firefly</groupId>
      <artifactId>firefly-maven-plugin</artifactId>
      <version>1.0-Alpha</version>
      <executions>
        <execution>
          <goals><goal>compile</goal></goals>
        </execution>
      </executions>
    </plugin>
    <plugin>
      <groupId>org.springframework.boot</groupId>
      <artifactId>spring-boot-maven-plugin</artifactId>
    </plugin>
  </plugins>
</build>
```

## Application Entry Point
```fly
module com::example

use org::springframework::boot::SpringApplication
use org::springframework::boot::autoconfigure::SpringBootApplication

@SpringBootApplication
class Application {
  pub fn fly(args: [String]) -> Void {
    SpringApplication::run(Application.class);
  }
}
```
Notes:
- `fly()` is the entry point (like Java `main`).
- Keep `args` reserved in `fly()`; avoid reusing it for other values.

## Controllers and Mappings
Controllers are regular classes with Spring annotations.
```fly
use org::springframework::web::bind::annotation::{RestController, GetMapping}

@RestController
class HelloController {
  @GetMapping("/hello")
  pub fn hello() -> String { "Hello from Flylang!" }
}
```
Supported annotations include:
- Class: `@SpringBootApplication`, `@RestController`, `@Controller`, `@Service`, `@Repository`, `@Component`, `@Configuration`
- Method: `@GetMapping`, `@PostMapping`, `@PutMapping`, `@DeleteMapping`, `@PatchMapping`, `@RequestMapping`
- Parameter: `@PathVariable`, `@RequestParam`, `@RequestBody`, `@RequestHeader`

Compiler detail: single mapping values are emitted as `String[]` automatically (e.g., `value=["/hello"]`).

## Structs and JSON Serialization
Structs generate Jackson-friendly POJOs: final fields, constructor parameter names in bytecode, and JavaBean getters.
```fly
struct User { id: String, name: String, email: String }
```
Using structs in endpoints:
```fly
@RestController
class UserController {
  @GetMapping("/users/{id}")
  pub fn getUser(@PathVariable("id") userId: String) -> User {
    User { id: userId, name: "John Doe", email: "john@example.com" }
  }

  @PostMapping("/users")
  pub fn createUser(@RequestBody user: User) -> User {
    User { id: user.id, name: user.name, email: user.email }
  }
}
```

## Path Variables and Query Parameters
Path variables:
```fly
@GetMapping("/users/{userId}/posts/{postId}")
pub fn getPost(
  @PathVariable("userId") userId: String,
  @PathVariable("postId") postId: String
) -> Post {
  Post { userId: userId, postId: postId }
}
```
Query parameters:
```fly
@GetMapping("/search")
pub fn search(
  @RequestParam("q") query: String,
  @RequestParam("page") page: Int
) -> SearchResults {
  SearchResults { query: query, page: page }
}
```
Default values:
```fly
@GetMapping("/items")
pub fn listItems(
  @RequestParam(value = "limit", defaultValue = "10") limit: Int
) -> [Item] {
  []
}
```

## Complete Example (CRUD)
```fly
module com::example::api

use org::springframework::boot::SpringApplication
use org::springframework::boot::autoconfigure::SpringBootApplication
use org::springframework::web::bind::annotation::{ RestController, GetMapping, PostMapping, PutMapping, DeleteMapping, PathVariable, RequestBody, RequestParam }

@SpringBootApplication
class Application {
  pub fn fly(args: [String]) -> Void {
    SpringApplication::run(Application.class);
  }
}

struct Todo { id: String, title: String, completed: Bool }

@RestController
class TodoController {
  @GetMapping("/todos")
  pub fn listTodos() -> [Todo] { [] }

  @GetMapping("/todos/{id}")
  pub fn getTodo(@PathVariable("id") id: String) -> Todo {
    Todo { id: id, title: "Sample", completed: false }
  }

  @PostMapping("/todos")
  pub fn createTodo(@RequestBody todo: Todo) -> Todo { todo }

  @PutMapping("/todos/{id}")
  pub fn updateTodo(@PathVariable("id") id: String, @RequestBody todo: Todo) -> Todo {
    Todo { id: id, title: todo.title, completed: todo.completed }
  }

  @DeleteMapping("/todos/{id}")
  pub fn deleteTodo(@PathVariable("id") id: String) -> Void { }
}
```

## Build, Run, and Packaging
Build and run during development:
```bash
mvn clean compile
mvn spring-boot:run
```
Package a runnable JAR:
```bash
mvn -q -DskipTests clean package
mvn spring-boot:repackage
java -jar target/*-SNAPSHOT.jar
```
Test endpoints:
```bash
curl http://localhost:8080/hello
curl -X POST http://localhost:8080/users -H "Content-Type: application/json" -d '{"id":"123","name":"Alice","email":"alice@example.com"}'
```

## Troubleshooting
- 404 for controllers: ensure controllers are in the same package or a subpackage of the `@SpringBootApplication` class.
- JSON deserialization errors: add `jackson-module-parameter-names` and verify struct fields/constructor match.
- Mapping values: if you see type mismatch for mapping `value`, update to Flylang 1.0‑Alpha+.

## Known Limitations
- Multi‑argument static calls with `Application.class` may require assigning the class to a temp:
  ```fly
  let appClass = Application.class;
  SpringApplication::run(appClass);
  ```
- Some generic collections may require explicit types/casts in Spring APIs.

## See Also
- Spring Boot Demo: `examples/spring-boot-demo/`
- Language Guide: `docs/LANGUAGE_GUIDE.md`
- Java Interop: `docs/JAVA_INTEROP.md`
