#!/bin/bash

echo "=========================================="
echo "Testing Firefly REPL - Quick Test Suite"
echo "=========================================="
echo

# Test 1: Simple expressions via pipe
echo "Test 1: Simple expressions (piped input)"
echo -e "1 + 2\n3 * 4\n:quit" | java -jar target/firefly-repl.jar
echo

# Test 2: Variable declarations
echo "Test 2: Variable declarations and usage"
echo -e "let x = 42\nx + 10\n:quit" | java -jar target/firefly-repl.jar
echo

# Test 3: Function definition and call
echo "Test 3: Function definition and call"
echo -e "fn add(a: Int, b: Int) -> Int { return a + b; }\nadd(5, 3)\n:quit" | java -jar target/firefly-repl.jar
echo

# Test 4: REPL commands
echo "Test 4: REPL commands"
echo -e ":help\n:imports\n:quit" | java -jar target/firefly-repl.jar
echo

# Test 5: Load file (non-interactive mode)
echo "Test 5: Loading test file (if syntax is compatible)"
echo -e ":load test-repl.fly\n:quit" | java -jar target/firefly-repl.jar 2>&1 | head -50
echo

echo "=========================================="
echo "Tests completed!"
echo "For interactive mode testing, run:"
echo "  java -jar target/firefly-repl.jar"
echo "=========================================="
