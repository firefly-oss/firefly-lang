# Contributing to Firefly

Thank you for your interest in contributing to Firefly! This document provides guidelines and information for contributors.

---

## 🎯 Ways to Contribute

### 1. Report Bugs
- Use [GitHub Issues](https://github.com/firefly-oss/firefly-lang/issues)
- Include minimal reproduction case
- Provide Firefly version and Java version
- Describe expected vs actual behavior

### 2. Suggest Features
- Open a [GitHub Discussion](https://github.com/firefly-oss/firefly-lang/discussions)
- Describe the use case
- Provide examples if possible
- Explain why this would benefit Firefly users

### 3. Improve Documentation
- Fix typos and clarify explanations
- Add examples and tutorials
- Improve code comments
- Translate documentation

### 4. Write Code
- Fix bugs
- Implement features
- Improve performance
- Add tests

---

## 🚀 Getting Started

### Prerequisites

- **Java 21+** (JDK)
- **Maven 3.6+**
- **Git**
- Familiarity with Java, ANTLR, and bytecode generation (for compiler work)

### Setup Development Environment

```bash
# Clone the repository
git clone https://github.com/firefly-oss/firefly-lang.git
cd firefly-lang

# Build the project
mvn clean install

# Run tests
mvn test

# Install locally
./install.sh
```

---

## 📁 Project Structure

```
firefly-lang/
├── firefly-compiler/       # Core compiler
│   ├── src/main/antlr4/   # ANTLR grammar
│   ├── src/main/java/     # Compiler implementation
│   │   ├── ast/           # AST nodes
│   │   ├── codegen/       # Bytecode generation
│   │   ├── parser/        # Parser utilities
│   │   └── semantics/     # Type checking
│   └── src/test/java/     # Compiler tests
├── firefly-maven-plugin/   # Maven integration
├── firefly-runtime/        # Runtime library
├── firefly-cli/           # Command-line interface
├── firefly-stdlib/        # Standard library
├── examples/              # Example programs
└── docs/                  # Documentation
```

---

## 🔧 Development Workflow

### 1. Create a Branch

```bash
# Create feature branch
git checkout -b feature/my-feature

# Or bugfix branch
git checkout -b fix/issue-123
```

### 2. Make Changes

Follow the coding standards below and ensure:
- Code compiles without errors
- Tests pass
- New features have tests
- Documentation is updated

### 3. Test Your Changes

```bash
# Run all tests
mvn clean test

# Run specific test
mvn test -Dtest=MyTest

# Build everything
mvn clean package
```

### 4. Commit Changes

Use clear, descriptive commit messages:

```bash
git commit -m "feat: add support for lambda expressions"
git commit -m "fix: resolve null pointer in type resolver"
git commit -m "docs: update Spring Boot integration guide"
```

**Commit Message Format**:
- `feat:` New feature
- `fix:` Bug fix
- `docs:` Documentation changes
- `test:` Test additions/changes
- `refactor:` Code refactoring
- `perf:` Performance improvements
- `chore:` Build/tooling changes

### 5. Push and Create Pull Request

```bash
# Push to your fork
git push origin feature/my-feature

# Create pull request on GitHub
```

---

## 📝 Coding Standards

### Java Code Style

- **Indentation**: 4 spaces (no tabs)
- **Line length**: 120 characters max
- **Naming**:
  - Classes: `PascalCase`
  - Methods/variables: `camelCase`
  - Constants: `UPPER_SNAKE_CASE`
  - Packages: `lowercase`

### Code Quality

- Write self-documenting code
- Add comments for complex logic
- Follow existing patterns in the codebase
- Keep methods focused and concise
- Avoid unnecessary complexity

### Example

```java
public class TypeResolver {
    private final Map<String, Type> typeCache = new HashMap<>();
    
    /**
     * Resolves a type by its fully qualified name.
     * 
     * @param typeName The fully qualified type name
     * @return The resolved type, or null if not found
     */
    public Type resolveType(String typeName) {
        if (typeCache.containsKey(typeName)) {
            return typeCache.get(typeName);
        }
        
        Type resolved = lookupType(typeName);
        if (resolved != null) {
            typeCache.put(typeName, resolved);
        }
        
        return resolved;
    }
}
```

---

## 🧪 Testing Guidelines

### Writing Tests

- Every feature needs tests
- Test both success and error cases
- Use descriptive test names
- Keep tests focused and independent

### Test Structure

```java
@Test
public void testVariableDeclaration() {
    // Given
    String source = "let x = 42;";
    
    // When
    CompilationUnit ast = parse(source);
    
    // Then
    assertNotNull(ast);
    assertEquals(1, ast.getDeclarations().size());
    assertTrue(ast.getDeclarations().get(0) instanceof VariableDecl);
}
```

### Running Tests

```bash
# All tests
mvn test

# Specific module
mvn test -pl firefly-compiler

# With coverage
mvn test jacoco:report
```

---

## 📚 Documentation Guidelines

### Code Documentation

- Document all public APIs
- Include usage examples
- Explain non-obvious logic
- Keep docs up to date with code

### Markdown Documentation

- Use clear, concise language
- Include code examples
- Add table of contents for long docs
- Use proper formatting and structure

---

## 🎯 Focus Areas

We especially welcome contributions in these areas:

### 1. Type System
- Generic type resolution
- Type inference improvements
- Type parameter bounds
- Complex nested types

### 2. Standard Library
- Collection utilities
- I/O operations
- String manipulation
- Date/time handling

### 3. IDE Support
- Language Server Protocol implementation
- VS Code extension
- Syntax highlighting
- Code completion

### 4. Documentation
- Tutorials and guides
- Code examples
- Best practices
- API documentation

### 5. Testing
- More comprehensive tests
- Integration tests
- Performance benchmarks
- Example validation

---

## 🐛 Bug Report Template

When reporting bugs, include:

```markdown
**Firefly Version**: 0.4.0
**Java Version**: OpenJDK 21
**OS**: macOS 14.0

**Description**:
Brief description of the bug

**Reproduction**:
```firefly
// Minimal code to reproduce
let x = 42;
```

**Expected Behavior**:
What should happen

**Actual Behavior**:
What actually happens

**Error Output**:
```
[paste any error messages]
```
```

---

## ✨ Feature Request Template

When suggesting features:

```markdown
**Feature Name**: Lambda Expressions

**Use Case**:
Describe why this feature is needed

**Proposed Syntax**:
```firefly
let add = (x, y) => x + y;
```

**Examples**:
Show how it would be used

**Alternatives Considered**:
Other approaches you've thought about

**Additional Context**:
Any other relevant information
```

---

## 🔍 Code Review Process

### What We Look For

1. **Correctness**: Does it work as intended?
2. **Tests**: Are there adequate tests?
3. **Documentation**: Is it well documented?
4. **Code Quality**: Is it clean and maintainable?
5. **Consistency**: Does it follow project conventions?

### Review Timeline

- Small changes: 1-3 days
- Medium changes: 3-7 days
- Large changes: 1-2 weeks

### Addressing Feedback

- Respond to all comments
- Make requested changes
- Push updates to same branch
- Re-request review when ready

---

## 📞 Getting Help

### Communication Channels

- **GitHub Issues**: Bug reports and feature requests
- **GitHub Discussions**: Questions and ideas
- **Pull Request Comments**: Code review discussions

### Questions?

Don't hesitate to ask! We're here to help:
- Open a [Discussion](https://github.com/firefly-oss/firefly-lang/discussions)
- Comment on relevant issues
- Reach out to maintainers

---

## 📜 License

By contributing to Firefly, you agree that your contributions will be licensed under the [MIT License](LICENSE).

---

## 🙏 Recognition

All contributors will be recognized in:
- GitHub contributors page
- Release notes
- Project documentation

Thank you for making Firefly better! 🔥

---

**Happy Contributing!**

For more information, see:
- [README.md](README.md) - Project overview
- [GUIDE.md](GUIDE.md) - Language guide
- [STATUS.md](STATUS.md) - Implementation status
