# Flylang REPL Status

## ✅ Implemented Features

### Core Functionality
- **Expression evaluation**: Arithmetic, strings, function calls
- **Function definitions**: Define and persist functions across sessions
- **Class definitions**: Global class/struct/data declarations  
- **Import management**: Add use statements dynamically
- **Type inference**: Compiler-backed `:type` command for expressions

### IDE-like Experience
- **Syntax highlighting**: Keywords, strings, numbers, operators colored
- **Smart completion**: Tab-complete keywords, commands, functions, classes, variables
- **Dynamic prompt**: Shows context (`[N imp | M fn | K cls | L var]`)
- **Context panel**: `:context` displays full REPL state with statistics

### Commands
- `:help` - Show all commands and examples
- `:quit` / `:exit` - Exit REPL
- `:reset` - Clear all definitions
- `:context` - Show full context panel
- `:imports` - List current imports
- `:definitions` - List defined functions/classes
- `:clear` - Clear screen
- `:load <file>` - Load and execute Flylang file (multi-line aware)
- `:save <file>` - Save session history
- `:history [n]` - Show last n commands (default 20)
- `:edit` - Open $EDITOR to write multi-line code
- `:type <expr>` - Show inferred type without execution

### Quality of Life
- Multi-line input support (auto-detects unbalanced braces/parens)
- Persistent command history (~/.firefly_repl_history)
- Rich error diagnostics with suggestions
- Beautiful ANSI colored output

## ⚠️ Known Limitations

### Piped Input Issue
When using piped input (`echo "1+2" | fly repl`), JLine's dumb terminal mode accumulates all stdin lines together before parsing. This causes syntax errors.

**Workaround**: Use the REPL interactively by running `fly repl` directly.

### Testing the REPL

**Interactive mode** (recommended):
```bash
fly repl
```

Then try:
```
1 + 2
fn add(a: Int, b: Int) -> Int { return a + b; }
add(2, 3)
:type add(10, 20)
:context
:help
:quit
```

**File loading**:
```bash
fly repl
:load examples/hello-world/src/main/firefly/examples/hello_world/Main.fly
```

## 🎯 Architecture

The REPL wraps all code in a `Main` class with `fly(args: [String])` as the entry point, following Flylang's grammar requirements:
- Functions defined with `fn` become instance methods in Main
- Expressions are wrapped in auto-generated snippet methods
- Global class/struct/data declarations go at module level
- Each evaluation uses a fresh ClassLoader to avoid conflicts

## 🔧 Future Improvements
- [ ] Fix piped input for scripted testing
- [ ] Add `:doc` command for inline documentation
- [ ] Implement variable inspection (`:vars`)
- [ ] Add breakpoint/step debugging
- [ ] Support for actor REPL sessions
- [ ] Persistent session save/restore
