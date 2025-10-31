# Firefly REPL - Feature Documentation

## Overview

The Firefly REPL (Read-Eval-Print Loop) is a professional, CLI IDE-like interactive environment for the Firefly programming language.

## Architecture

### Core Components

1. **FireflyRepl** (`FireflyRepl.java`) - Main entry point
   - Initializes all components
   - Manages main REPL loop
   - Routes input to commands or evaluation

2. **ReplEngine** (`ReplEngine.java`) - Compilation and execution engine
   - Incremental compilation
   - Bytecode generation and loading
   - Dynamic type inference
   - State management (imports, functions, classes, variables)
   - Multi-layered error handling

3. **ReplUI** (`ReplUI.java`) - Terminal user interface
   - Syntax highlighting (JLine3)
   - Tab completion
   - Command history with persistence
   - Multi-line input support
   - Beautiful colored output
   - External editor integration
   - Automatic terminal detection (interactive vs piped)

4. **ReplCommand** (`ReplCommand.java`) - Command processor
   - Handles all `:command` style directives
   - File loading with multi-line awareness
   - History management

## Features

### ✅ Interactive Mode Features

#### 1. **Syntax Highlighting**
- Keywords: `fn`, `class`, `let`, `if`, `for`, `while`, etc. (magenta/bold)
- Strings: Yellow
- Numbers: Cyan
- Comments: Green/italic
- Operators: Bright

#### 2. **Smart Tab Completion**
Auto-completes:
- **REPL commands**: `:help`, `:quit`, `:load`, `:type`, etc.
- **Language keywords**: `fn`, `class`, `let`, `if`, `match`, etc.
- **User-defined symbols**:
  - Functions (shows with `(` appended)
  - Classes
  - Variables

Context-aware: completion adapts based on what you've defined.

#### 3. **Multi-line Input**
Automatically detects incomplete expressions:
```firefly
fn factorial(n: Int) -> Int {
    if (n <= 1) {
        return 1;
    }
    return n * factorial(n - 1);
}
```
Press Enter at incomplete lines → continues with `...` prompt.

#### 4. **Dynamic Prompt**
Shows current REPL state:
```
fly [3 imp | 2 fn | 1 cls | 5 var]>
```
- `imp` = imports
- `fn` = defined functions  
- `cls` = defined classes
- `var` = variables in scope

#### 5. **Command History**
- Persistent across sessions (saved to `~/.firefly_repl_history`)
- Navigate with ↑/↓ arrow keys
- View with `:history [n]` command
- Save to file with `:save <file>`

#### 6. **External Editor Integration**
```
:edit
```
- Opens `$EDITOR` (defaults to `vi`)
- Edit multi-line code comfortably
- Executed on save/exit

#### 7. **Rich Error Reporting**
Four error types with detailed feedback:
- **Syntax errors**: Line/column, suggestion
- **Semantic errors**: Type mismatches, undefined symbols
- **Compilation errors**: Bytecode issues
- **Runtime errors**: Exception details with stack

Example:
```
  ╭─ Syntax Error
  │  at line 1, column 5
  │
  │  Unexpected token '='
  │
  │  💡 Use 'let' or 'mut' to declare variables: 'let x = 42'
  ╰─
```

### ✅ Non-Interactive Mode (Piped Input)

Automatically detected when stdin is not a TTY:
```bash
echo "1 + 2" | java -jar firefly-repl.jar
cat script.fly | java -jar firefly-repl.jar
```

- No JLine overhead
- Direct BufferedReader for line-by-line processing
- Prevents input accumulation bugs
- Simple `flylang>` prompt

### ✅ REPL Commands

All commands start with `:`:

| Command | Aliases | Description |
|---------|---------|-------------|
| `:help` | `:h`, `:?` | Show help message |
| `:quit` | `:exit`, `:q` | Exit REPL |
| `:reset` | - | Clear all state |
| `:context` | `:ctx` | Show full context |
| `:imports` | - | List imports |
| `:definitions` | `:defs` | List functions and types |
| `:clear` | `:cls` | Clear screen |
| `:load <file>` | - | Load and execute file |
| `:save <file>` | - | Save history to file |
| `:history [n]` | - | Show last n commands (default 20) |
| `:edit` | - | Open external editor |
| `:type <expr>` | - | Show inferred type |

### ✅ Type Inference

Best-effort dynamic type inference:
```firefly
fly> :type 1 + 2
Int

fly> :type "hello"
String

fly> :type add(5, 3)
Int
```

Uses the compiler's `TypeInference` pass by embedding expressions into probe functions.

## Testing the REPL

### Quick Test Suite

Run the automated tests:
```bash
cd firefly-repl
./quick-test.sh
```

### Manual Interactive Testing

1. **Start REPL**:
```bash
java -jar target/firefly-repl.jar
```

2. **Test basic expressions**:
```firefly
fly> 1 + 2
fly> "Hello, " + "World!"
```

3. **Test variables**:
```firefly
fly> let x = 42
fly> x + 10
```

4. **Test functions**:
```firefly
fly> fn add(a: Int, b: Int) -> Int { return a + b; }
fly> add(5, 3)
```

5. **Test tab completion**:
- Type `ad` and press TAB → completes to `add(`
- Type `:h` and press TAB → shows `:help`, `:history`

6. **Test multi-line**:
```firefly
fly> fn factorial(n: Int) -> Int {
...     if (n <= 1) { return 1; }
...     return n * factorial(n - 1);
... }
fly> factorial(5)
```

7. **Test history**:
- Press ↑ to see previous commands
- `:history 10` to see last 10 commands

8. **Test editor**:
```firefly
fly> :edit
```
(Opens editor, write code, save, code executes)

9. **Test error handling**:
```firefly
fly> x = 42  # Should suggest "Use 'let'"
fly> let y = "string" + 1  # Type error
```

10. **Test file loading**:
```firefly
fly> :load test-repl.fly
```

## Known Limitations

### Current State
- ✅ Full terminal UI with JLine3
- ✅ Syntax highlighting
- ✅ Tab completion
- ✅ Multi-line input
- ✅ History persistence
- ✅ Error handling with suggestions
- ✅ Type inference
- ✅ Non-interactive mode
- ✅ External editor support

### Potential Improvements
- Add code formatting (`:format`)
- Add documentation lookup (`:doc <symbol>`)
- Add benchmark command (`:bench <expr>`)
- Add AST visualization (`:ast <expr>`)
- Add breakpoint/debug support
- Import completion from classpath
- Method/field completion for objects

## Performance Considerations

- **Incremental compilation**: Each snippet compiled independently
- **Class loading**: Dynamic class loader per snippet
- **Memory**: Efficient state management, old classes can be GC'd
- **Startup time**: ~1-2 seconds (JVM + JLine initialization)

## Build & Run

### Build
```bash
mvn clean install -pl firefly-repl -am
```

### Run
```bash
java -jar firefly-repl/target/firefly-repl.jar
```

### Run with debug
```bash
DEBUG=1 java -jar firefly-repl/target/firefly-repl.jar
```
(Shows generated source code for debugging)

## Troubleshooting

### JLine warnings
```
WARNING: Unable to create a system terminal, creating a dumb terminal
```
**Cause**: Non-interactive input (piped)  
**Solution**: Ignore - this is expected and handled automatically

### Syntax errors on simple expressions
**Cause**: Generated code may not match Firefly grammar exactly  
**Solution**: Check `firefly-compiler` grammar and ensure REPL code generation aligns

### Tab completion not working
**Cause**: Not in interactive mode  
**Solution**: Run directly (not piped) and ensure terminal supports JLine

### History not saved
**Cause**: Non-interactive mode or permissions  
**Solution**: Check `~/.firefly_repl_history` file permissions

## Architecture Decisions

### Why JLine3?
- Industry standard for Java CLI applications
- Rich feature set (completion, highlighting, history)
- Well maintained and documented
- Used by popular tools (Kotlin REPL, Groovy console, etc.)

### Why dumb terminal fallback?
- Enables piped input (`cat file.fly | repl`)
- Prevents JLine accumulation bugs
- Better testing support
- CI/CD friendly

### Why embedded type inference?
- Reuses compiler's type inference pass
- No duplicate logic
- Stays in sync with language evolution
- Provides accurate types

## Future Enhancements

1. **Smart code suggestions**: AI-powered based on context
2. **Inline documentation**: Hover/help for symbols
3. **Visual debugging**: Step through execution
4. **Performance profiling**: Built-in profiler
5. **Package management**: Install/import packages from REPL
6. **Cloud sync**: Sync history across machines
7. **Collaborative REPL**: Multiple users in same session

---

**Version**: 1.0-Alpha  
**Last Updated**: October 30, 2025  
**Maintainer**: Firefly Language Team
