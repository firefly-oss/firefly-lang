# 🔥 Firefly REPL

**Professional CLI IDE for the Firefly Programming Language**

Version: 1.0-Alpha

## Quick Start

### Build
```bash
mvn clean install
```

### Run
```bash
java -jar target/firefly-repl.jar
```

## Features

✅ **Syntax Highlighting** - Beautiful, colored code  
✅ **Tab Completion** - Keywords, commands, your symbols  
✅ **Multi-line Input** - Smart brace/paren detection  
✅ **Command History** - Persistent across sessions  
✅ **External Editor** - Edit code in your favorite editor  
✅ **Type Inference** - See types with `:type <expr>`  
✅ **Rich Errors** - Detailed errors with suggestions  
✅ **Dynamic Prompt** - Shows current state (imports, functions, vars)  
✅ **Non-interactive Mode** - Pipe scripts directly  

## CLI IDE Experience

The REPL provides an IDE-like experience in your terminal:

### Smart Autocompletion
Press TAB to complete:
- REPL commands (`:help`, `:load`, `:type`, etc.)
- Language keywords (`fn`, `class`, `let`, `match`, etc.)
- Your defined functions, classes, and variables

### Syntax Highlighting
Code is highlighted as you type:
- **Keywords**: Magenta & bold
- **Strings**: Yellow
- **Numbers**: Cyan
- **Comments**: Green & italic

### Intelligent Multi-line
No need for backslash continuations - just type naturally:
```firefly
fly> fn factorial(n: Int) -> Int {
...     if (n <= 1) { return 1; }
...     return n * factorial(n - 1);
... }
  ✓ Function 'factorial' defined
```

### Dynamic Context Awareness
The prompt shows your current REPL state:
```
fly [3 imp | 2 fn | 1 cls | 5 var]>
```

### Rich Error Messages
Beautiful error output with suggestions:
```
  ╭─ Syntax Error
  │  at line 1, column 5
  │  Unexpected token '='
  │  💡 Use 'let' or 'mut' to declare variables: 'let x = 42'
  ╰─
```

## Essential Commands

| Command | Description |
|---------|-------------|
| `:help` | Show all commands |
| `:quit` | Exit REPL |
| `:load <file>` | Load and execute a file |
| `:edit` | Open external editor ($EDITOR) |
| `:type <expr>` | Show inferred type |
| `:history [n]` | Show command history |
| `:save <file>` | Save history to file |
| `:reset` | Clear all state |
| `:context` | Show current context |

## Examples

### Basic Usage
```firefly
fly> 1 + 2
  => 3 : Int

fly> let name = "Firefly"
  ✓ Variable 'name' defined

fly> name
  => "Firefly" : String
```

### Define Functions
```firefly
fly> fn add(a: Int, b: Int) -> Int { return a + b; }
  ✓ Function 'add' defined

fly> add(5, 3)
  => 8 : Int
```

### Type Inference
```firefly
fly> :type 1 + 2
Int

fly> :type add(10, 20)
Int
```

### Load Files
```firefly
fly> :load my-code.fly
  ✓ Function 'factorial' defined
  ✓ Function 'fibonacci' defined
  => "All tests passed!" : String
```

### Use External Editor
```firefly
fly> :edit
# Opens $EDITOR (vi/vim/nano/etc.)
# Write your code, save, and it executes automatically
```

## Non-Interactive Mode

Pipe scripts or commands:

```bash
# Single command
echo "1 + 2" | java -jar firefly-repl.jar

# Script file
cat script.fly | java -jar firefly-repl.jar

# Multi-line
echo -e "let x = 42\nx + 10\n:quit" | java -jar firefly-repl.jar
```

## Testing

Run the quick test suite:
```bash
./quick-test.sh
```

Or test manually:
```bash
java -jar target/firefly-repl.jar
```

Then try:
1. Type `1 + 2` and press Enter
2. Type `let x = 42` and press Enter
3. Type `x` and press TAB (should complete if you defined variables)
4. Press ↑ to see history
5. Type `:h` and press TAB (shows `:help`, `:history`)
6. Type `:quit` to exit

## Code Autocompletion Details

The REPL provides **context-aware autocompletion** similar to modern IDEs:

### How it Works
1. **Start typing** any identifier
2. **Press TAB** to see completions
3. **Keep typing** to narrow down
4. **Press TAB again** to cycle through options

### What Gets Completed

#### 1. REPL Commands
```firefly
fly> :h<TAB>
:help  :history

fly> :qu<TAB>
:quit
```

#### 2. Language Keywords
```firefly
fly> f<TAB>
fn  for  false

fly> cla<TAB>
class
```

#### 3. Your Functions
```firefly
fly> fn add(a: Int, b: Int) -> Int { return a + b; }
fly> fn average(a: Int, b: Int) -> Int { return (a + b) / 2; }

fly> a<TAB>
add(  average(  async  await

fly> add<TAB>
add(
```
Note: Functions show with `(` appended to indicate they're callable.

#### 4. Your Classes
```firefly
fly> class Point { x: Int; y: Int; }
fly> class Circle { radius: Float; }

fly> P<TAB>
Point

fly> C<TAB>
Circle
```

#### 5. Your Variables
```firefly
fly> let userName = "Alice"
fly> let userAge = 30

fly> use<TAB>
use  userName

fly> userN<TAB>
userName
```

### Case Sensitivity
Completion is **case-insensitive** - typing lowercase matches CamelCase:
```firefly
fly> myFunctionName<TAB>  # Works
fly> myfunctionname<TAB>  # Also works
```

### Command Context
When you type `:`, only commands are shown:
```firefly
fly> :<TAB>
:help  :quit  :reset  :load  :save  :edit  :type  ...
```

## Architecture

### Components
- **FireflyRepl**: Main loop
- **ReplEngine**: Compilation & execution
- **ReplUI**: Terminal interface (JLine3)
- **ReplCommand**: Command handler

### Technologies
- **JLine3**: Terminal UI framework
- **ANTLR4**: Parsing
- **ASM**: Bytecode generation
- **Java Reflection**: Dynamic execution

## Documentation

See [REPL-FEATURES.md](./REPL-FEATURES.md) for comprehensive documentation.

## License

Part of the Firefly Language Project

---

**Happy Coding! 🔥**
