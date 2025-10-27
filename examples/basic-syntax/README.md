# Basic Syntax Example

Comprehensive demonstration of Firefly's core syntax features.

## Features Demonstrated

### Variables
- Immutable variables (`let`)
- Mutable variables (`let mut`)
- Type inference
- Primitive types (Int, Float, String)

### Functions
- Function declarations
- Parameters and return types
- Expression-oriented functions

### Control Flow
- If/else expressions
- Multiple conditions
- Expression results

### Arrays
- Array literals
- For-in loops
- Mutable accumulation

### Classes
- Class declarations
- Fields (mutable and immutable)
- Constructors with `init`
- Instance methods
- The `self` keyword

## Running

```bash
# Compile
firefly compile syntax_demo.fly

# Run
java -cp . examples.Main
```

## Expected Output

```
Language: Firefly
Version: 1
Hello, World!
2 + 3 = 5
5 is positive
-3 is negative
Sum of [1,2,3,4,5] = 15
Person: Alice, age 30
```

## Learning Points

1. **Variables**: Use `let` for immutability, `let mut` when you need to change values
2. **Functions**: Last expression is the return value (no explicit `return` needed)
3. **Control Flow**: if/else can be used as expressions
4. **Arrays**: Built on ArrayList, supports iteration
5. **Classes**: Use `self` to reference instance fields and methods

## Next Steps

After mastering basic syntax, check out:
- [Spring Boot Example](../spring-boot/) - Build REST APIs
- [Full Language Guide](../../GUIDE.md) - Complete reference
