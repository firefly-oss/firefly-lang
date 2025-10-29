# Firefly Language Support for Visual Studio Code

Official Visual Studio Code extension for the Firefly programming language (.fly files).

<p align="center">
  <img src="https://img.shields.io/badge/version-1.0--Alpha-blue.svg" alt="Version">
  <img src="https://img.shields.io/badge/VS%20Code-1.75+-blue.svg" alt="VS Code 1.75+">
  <img src="https://img.shields.io/badge/license-Apache%202.0-green.svg" alt="License">
</p>

## Features

### 🎨 Syntax Highlighting
- Rich syntax highlighting for `.fly` files
- Support for all Flylang language constructs:
  - Keywords (`spark`, `data`, `struct`, `async`, `match`, etc.)
  - Types and type annotations
  - String interpolation
  - Comments (line, block, and doc comments)
  - Operators and punctuation

### 🔌 Language Server Protocol (LSP) Integration
The extension includes full LSP support powered by the Firefly Language Server:

- **🔍 Code Completion**: Intelligent, context-aware suggestions (Ctrl+Space)
- **⚠️ Diagnostics**: Real-time error and warning messages
- **📖 Hover Information**: Type information and documentation on hover
- **🔗 Go to Definition**: Jump to symbol definitions (F12)
- **🔎 Find All References**: Find all uses of a symbol (Shift+F12)
- **✏️ Rename Symbol**: Rename symbols across the project (F2)
- **💡 Code Actions**: Quick fixes and refactorings
- **📝 Signature Help**: Parameter hints while typing function calls

### 📐 Code Snippets
Pre-built code snippets for common patterns:

| Prefix | Description |
|--------|-------------|
| `module` | Module declaration |
| `class` | Class with main fly entry point |
| `spark` | Spark (immutable smart record) |
| `struct` | Struct (product type) |
| `data` | Data (sum type / ADT) |
| `fn` | Function declaration |
| `async` | Async function |
| `match` | Match expression |
| `for` | For loop |
| `if` | If expression |

### 🎯 Other Features
- **Bracket Matching**: Automatic highlighting of matching brackets
- **Comment Toggling**: Quick comment/uncomment with Ctrl+/
- **Auto-Indentation**: Smart indentation for Flylang code
- **Folding**: Code folding for functions, classes, and blocks

## Installation

### From VS Code Marketplace (Recommended)
1. Open **Visual Studio Code**
2. Press `Ctrl+Shift+X` (or `Cmd+Shift+X` on macOS) to open Extensions view
3. Search for **"Firefly Language Support"**
4. Click **Install**

### From VSIX File
1. Download the `.vsix` file from [GitHub Releases](https://github.com/firefly-oss/firefly-lang/releases)
2. In VS Code, press `Ctrl+Shift+P` (or `Cmd+Shift+P` on macOS)
3. Type **"Extensions: Install from VSIX..."**
4. Select the downloaded `.vsix` file

## Getting Started

### Your First Flylang File

1. Create a new file with `.fly` extension (e.g., `hello.fly`)
2. The extension will automatically activate
3. Start coding:

```fly
module examples::hello

class Main {
    pub fn fly(args: [String]) -> Void {
        println("Hello, Flylang!");
    }
}
```

### Using Code Snippets

Type a snippet prefix and press `Tab`:

```fly
spark↹

// Expands to:
spark Name {
    field: Type,
    
    validate {
        true
    }
}
```

### LSP Features

#### Code Completion
Start typing and press `Ctrl+Space` to trigger completions:
```fly
let user = User {
    na↹  // Suggests: name
}
```

#### Diagnostics
Errors are highlighted in real-time:
```fly
module test::example

class Main {
    pub fn fly(args: [String]) -> Void {
        let x: Int = "hello";  // ❌ Type mismatch error
    }
}
```

#### Hover Information
Hover over any symbol to see its type and documentation:
```fly
spark User {
    name: String  // 💡 Hover to see field information
}
```

## Configuration

### Extension Settings

Open settings (`Ctrl+,`) and search for "Firefly":

- **`firefly.languageServer.path`**: Path to custom Firefly Language Server JAR
  - Default: Uses bundled server
  - Set this to use a specific version

- **`firefly.trace.server`**: LSP communication traces
  - Options: `off` (default), `messages`, `verbose`
  - Useful for debugging LSP issues

### Example Configuration

Add to your `settings.json`:
```json
{
  "firefly.languageServer.path": "/custom/path/to/firefly-lsp.jar",
  "firefly.trace.server": "verbose",
  "editor.formatOnSave": true,
  "[firefly]": {
    "editor.tabSize": 4,
    "editor.insertSpaces": true
  }
}
```

## Keyboard Shortcuts

| Action | Windows/Linux | macOS |
|--------|--------------|-------|
| Trigger Completion | Ctrl+Space | Cmd+Space |
| Go to Definition | F12 | F12 |
| Find All References | Shift+F12 | Shift+F12 |
| Rename Symbol | F2 | F2 |
| Show Hover | Ctrl+K Ctrl+I | Cmd+K Cmd+I |
| Toggle Comment | Ctrl+/ | Cmd+/ |
| Format Document | Shift+Alt+F | Shift+Option+F |
| Command Palette | Ctrl+Shift+P | Cmd+Shift+P |

## Advanced Features

### Working with the Language Server

The extension automatically starts the Firefly Language Server when you open a `.fly` file. You can:

- View LSP output: **View** → **Output** → Select "Firefly Language Server"
- Restart server: Command Palette → "Firefly: Restart Language Server"
- Check server status in the status bar

### Debugging LSP Issues

If LSP features aren't working:

1. **Check Output Panel**:
   - View → Output → "Firefly Language Server"
   - Look for error messages

2. **Enable Verbose Tracing**:
   ```json
   {
     "firefly.trace.server": "verbose"
   }
   ```

3. **Restart Language Server**:
   - Command Palette → "Firefly: Restart Language Server"

4. **Check Prerequisites**:
   - Java 21+ installed
   - Firefly compiler installed
   - Extension activated (check status bar)

### Custom Language Server

To use a custom LSP server build:

1. Build the Firefly LSP server:
   ```bash
   cd firefly-lang/firefly-lsp
   mvn package
   ```

2. Configure in settings:
   ```json
   {
     "firefly.languageServer.path": "/path/to/firefly-lsp.jar"
   }
   ```

3. Restart VS Code

## Snippets Reference

### Module
```fly
module ${1:package}::${2:name}
```

### Spark (Immutable Smart Record)
```fly
spark ${1:Name} {
    ${2:field}: ${3:Type},
    
    validate {
        ${4:true}
    }
    
    computed ${5:property}: ${6:Type} {
        ${7:self.field}
    }
    
    fn ${8:method}() -> ${9:Type} {
        ${10:// implementation}
    }
}
```

### Data (Sum Type)
```fly
data ${1:Name}<${2:T}> {
    ${3:Variant1}(${4:field}: ${5:Type}),
    ${6:Variant2}
}
```

### Match Expression
```fly
match ${1:value} {
    ${2:pattern} => ${3:expression},
    _ => ${4:default}
}
```

### Async Function
```fly
async fn ${1:name}(${2:params}) -> ${3:Result}<${4:T}, ${5:E}> {
    let ${6:result} = ${7:operation}.await;
    Ok(${6:result})
}
```

## Troubleshooting

### Extension Not Activating
**Problem**: Syntax highlighting or LSP not working

**Solutions**:
- Ensure file has `.fly` extension
- Check VS Code version (requires 1.75+)
- Reload window: Command Palette → "Developer: Reload Window"

### Language Server Not Starting
**Problem**: No code completion or diagnostics

**Solutions**:
- Check Java installation: `java -version` (requires Java 21+)
- View LSP output panel for error messages
- Verify extension is enabled: Extensions view → search "Firefly"
- Restart Language Server via Command Palette

### Performance Issues
**Problem**: Slow completions or high CPU usage

**Solutions**:
- Disable unused extensions
- Increase VS Code memory limit in settings
- Close unnecessary files/folders
- Check LSP output for errors

### Syntax Highlighting Issues
**Problem**: Incorrect colors or missing highlighting

**Solutions**:
- Verify file extension is `.fly`
- Try different color theme
- Reload window: Command Palette → "Developer: Reload Window"
- Report issue with code sample

## Development

### Building from Source

Prerequisites:
- Node.js 18+
- npm 9+

Build steps:
```bash
cd ide-plugins/vscode-firefly
npm install
npm run compile
```

Package extension:
```bash
npm install -g @vscode/vsce
vsce package
```

This creates a `.vsix` file you can install.

### Testing
```bash
npm test
```

### Debugging
1. Open `vscode-firefly` folder in VS Code
2. Press F5 to launch Extension Development Host
3. Open a `.fly` file to test

## Contributing

We welcome contributions!

1. Fork the [repository](https://github.com/firefly-oss/firefly-lang)
2. Create a feature branch
3. Make your changes
4. Add tests if applicable
5. Submit a pull request

### Areas for Contribution
- Additional code snippets
- Improved syntax highlighting
- Better LSP integration
- Documentation improvements
- Bug fixes

## Support

- **Issues**: [GitHub Issues](https://github.com/firefly-oss/firefly-lang/issues)
- **Discussions**: [GitHub Discussions](https://github.com/firefly-oss/firefly-lang/discussions)
- **Documentation**: [Official Docs](https://docs.fireflyframework.com/flylang)
- **Stack Overflow**: Tag with `flylang`

## Roadmap

### Version 0.5.0 (Planned)
- [ ] Improved code completion
- [ ] Semantic highlighting
- [ ] Inlay hints for types
- [ ] Code lens for references
- [ ] Integrated debugger support

### Version 0.6.0 (Planned)
- [ ] Refactoring support
- [ ] Extract method/variable
- [ ] Organize imports
- [ ] Code folding improvements

## License

Copyright 2025 Firefly Software Solutions Inc.

Licensed under the Apache License, Version 2.0. See [LICENSE](../../LICENSE) for details.

## Changelog

### Version 1.0-Alpha (Current)
- ✨ Initial release
- ✅ Syntax highlighting
- ✅ LSP integration
- ✅ Code completion
- ✅ Diagnostics
- ✅ Hover information
- ✅ Code snippets
- ✅ Go to definition
- ✅ Find references

---

**Made with ❤️ by Firefly Software Solutions Inc.**
