# Spring Boot REST API Example

Complete User Management REST API built with Firefly and Spring Boot.

## Overview

This example demonstrates a production-ready REST API with:
- **Domain Model** - User entity
- **Repository Interface** - Data access layer
- **Service Layer** - Business logic with `@Service`
- **REST Controller** - HTTP endpoints with Spring MVC annotations
- **Dependency Injection** - Using `@Autowired`

## Architecture

```
┌─────────────────┐
│  UserController │ @RestController
│   (REST Layer)  │
└────────┬────────┘
         │ @Autowired
         ▼
┌─────────────────┐
│   UserService   │ @Service
│ (Business Layer)│
└────────┬────────┘
         │ @Autowired
         ▼
┌─────────────────┐
│ UserRepository  │ interface
│  (Data Layer)   │
└─────────────────┘
```

## API Endpoints

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/users` | Get all users |
| GET | `/api/users/{id}` | Get user by ID |
| POST | `/api/users` | Create new user |
| PUT | `/api/users/{id}` | Update user |
| DELETE | `/api/users/{id}` | Delete user |

## Spring Boot Annotations Used

### Class-Level
- `@SpringBootApplication` - Main application class
- `@RestController` - Marks class as REST controller
- `@Service` - Marks class as service component
- `@RequestMapping("/api/users")` - Base path for all endpoints

### Method-Level
- `@GetMapping("")` - HTTP GET handler
- `@PostMapping("")` - HTTP POST handler
- `@PutMapping("/{id}")` - HTTP PUT handler
- `@DeleteMapping("/{id}")` - HTTP DELETE handler

### Parameter-Level
- `@PathVariable` - Binds URL path variable
- `@RequestBody` - Binds request body to object
- `@Autowired` - Dependency injection

## Compiling

```bash
# Compile Firefly source
firefly compile user_api.fly

# This generates:
# - User.class
# - UserRepository.class
# - UserService.class
# - UserController.class
# - Application.class
# - Main.class
```

## Running with Spring Boot

### Option 1: Add to Spring Boot Project

1. Copy generated `.class` files to your Spring Boot project's `target/classes` directory
2. Ensure Spring Boot dependencies are on classpath
3. Run Spring Boot application:

```bash
mvn spring-boot:run
```

### Option 2: Create Maven Project

Create `pom.xml`:

```xml
<project>
    <modelVersion>4.0.0</modelVersion>
    <groupId>com.example</groupId>
    <artifactId>firefly-user-api</artifactId>
    <version>1.0.0</version>
    
    <parent>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-parent</artifactId>
        <version>3.2.0</version>
    </parent>
    
    <dependencies>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
        </dependency>
    </dependencies>
    
    <build>
        <plugins>
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
            </plugin>
        </plugins>
    </build>
</project>
```

Then:

```bash
mvn clean package
java -jar target/firefly-user-api-1.0.0.jar
```

## Testing the API

### Create User
```bash
curl -X POST http://localhost:8080/api/users \
  -H "Content-Type: application/json" \
  -d '{"name":"Alice","email":"alice@example.com"}'
```

### Get All Users
```bash
curl http://localhost:8080/api/users
```

### Get User by ID
```bash
curl http://localhost:8080/api/users/123
```

### Update User
```bash
curl -X PUT http://localhost:8080/api/users/123 \
  -H "Content-Type: application/json" \
  -d '{"name":"Alice Smith","email":"alice.smith@example.com"}'
```

### Delete User
```bash
curl -X DELETE http://localhost:8080/api/users/123
```

## What Makes This Work

### 1. Annotations in Bytecode
Firefly compiler emits Spring annotations directly into the `.class` files, so Spring Boot can discover and configure components at runtime.

### 2. Standard JVM Bytecode
Generated classes are 100% Java-compatible, allowing seamless integration with the Spring framework.

### 3. Dependency Injection
Spring automatically wires dependencies using `@Autowired`, injecting `UserRepository` into `UserService` and `UserService` into `UserController`.

### 4. Component Scanning
Spring Boot's `@SpringBootApplication` enables component scanning, automatically discovering `@RestController` and `@Service` classes.

## Production Considerations

For production use, you'll need to:

1. **Implement Repository** - Add JPA/JDBC implementation of `UserRepository`
2. **Add Validation** - Validate request bodies
3. **Error Handling** - Add `@ControllerAdvice` for global error handling
4. **Security** - Add Spring Security for authentication/authorization
5. **Database** - Configure datasource and JPA
6. **Logging** - Add proper logging
7. **Testing** - Add unit and integration tests

## Learning Points

1. **Layered Architecture** - Separation of concerns (Controller → Service → Repository)
2. **Dependency Injection** - Loose coupling via `@Autowired`
3. **REST Conventions** - Standard HTTP methods and status codes
4. **Spring Boot Magic** - Annotation-driven configuration
5. **Firefly Integration** - Native JVM compilation enables seamless Spring Boot integration

## Next Steps

- Add JPA repository implementation
- Implement authentication with Spring Security
- Add validation and error handling
- Write integration tests
- Deploy to production

## See Also

- [Spring Boot Integration Guide](../../SPRING_BOOT_INTEGRATION.md) - Technical details
- [Full Language Guide](../../GUIDE.md) - Complete Firefly reference
- [Implementation Status](../../STATUS.md) - Current feature status
