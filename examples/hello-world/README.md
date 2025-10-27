# Hello World Example

The simplest Firefly program - print a message to the console.

## Code

```firefly
package hello

fn main(args: Array<String>) -> Unit {
    println("Hello, Firefly! 🔥");
    println("Welcome to the future of JVM programming.");
}
```

## Running

### Option 1: Using firefly compiler

```bash
firefly compile hello.fly
java -cp . hello.Main
```

### Option 2: Using Maven

```bash
# Coming soon
```

## What This Demonstrates

- **Package declaration**: `package hello`
- **Main function**: Entry point with standard signature
- **String literals**: Double-quoted strings
- **Function calls**: Built-in `println` function
- **Arrays**: `Array<String>` type for arguments

## Expected Output

```
Hello, Firefly! 🔥
Welcome to the future of JVM programming.
```
