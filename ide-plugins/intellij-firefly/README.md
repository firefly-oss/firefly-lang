# Firefly Language Support for IntelliJ IDEA

Official IntelliJ IDEA plugin for the Firefly programming language (.fly files).

<p align="center">
  <img src="https://img.shields.io/badge/version-1.0--Alpha-blue.svg" alt="Version">
  <img src="https://img.shields.io/badge/IntelliJ-2023.2+-orange.svg" alt="IntelliJ 2023.2+">
  <img src="https://img.shields.io/badge/license-Apache%202.0-green.svg" alt="License">
</p>

## Features

### 🎨 Syntax Highlighting
- Full syntax highlighting for `.fly` files
- Customizable color scheme in Editor → Color Scheme → Firefly
- Support for all Flylang keywords, operators, and constructs
- Semantic highlighting for types, functions, and variables

### 🧠 Language Server Protocol (LSP) Integration
- **Real-time Diagnostics**: Syntax and semantic errors highlighted as you type
- **Code Completion**: Intelligent, context-aware suggestions (Ctrl+Space / Cmd+Space)
- **Hover Documentation**: View type information and docs on hover (Ctrl+Q / Cmd+Q)
- **Go to Definition**: Jump to symbol definitions (Ctrl+B / Cmd+B)
- **Find Usages**: Find all references to a symbol (Alt+F7 / Cmd+F7)
- **Parameter Info**: Show function parameter hints (Ctrl+P / Cmd+P)
- **Code Formatting**: Format code with LSP-based formatter (Ctrl+Alt+L / Cmd+Option+L)

### ✏️ Editing Features
- **Code Commenter**: Quick comment/uncomment with Cmd+/ or Ctrl+/
- **Brace Matching**: Automatic matching and highlighting of brackets, braces, and parentheses
- **Structure View**: Navigate code structure in the Structure tool window

### 📝 File Templates
Quick file creation with 8 built-in templates:
1. **Firefly File**: Basic empty module file
2. **Firefly Main**: Main class with `fly()` entry point
3. **Firefly Class**: Java-interop class template
4. **Firefly Interface**: Interface declaration template
5. **Firefly Struct**: Product type (struct) template
6. **Firefly Data**: Sum type (algebraic data type) template
7. **Firefly Test**: Test file template
8. **Firefly Actor**: Actor system template

### ▶️ Run Configurations
- Create run configurations for Flylang programs
- Run and debug Flylang applications directly from IDE
- Integrated console output

## Installation

### From JetBrains Marketplace (Recommended)
1. Open IntelliJ IDEA
2. Go to **Settings/Preferences** → **Plugins** → **Marketplace**
3. Search for **"Firefly Language Support"**
4. Click **Install**
5. Restart IntelliJ IDEA

### Manual Installation
1. Download the latest plugin ZIP from [GitHub Releases](https://github.com/firefly-oss/firefly-lang/releases)
2. In IntelliJ IDEA, go to **Settings/Preferences** → **Plugins**
3. Click the gear icon ⚙️ → **Install Plugin from Disk...**
4. Select the downloaded ZIP file
5. Restart IntelliJ IDEA

## Getting Started

### Creating Your First Flylang File

1. **New File via Template**:
   - Right-click in the Project view
   - Select **New** → **Firefly File**
   - Choose a template (e.g., "Firefly Main")
   - Enter the file name

2. **Manual Creation**:
   - Create a new file with `.fly` extension
   - Add module declaration:
   ```fly
   module com::example::hello
   
   class Main {
       pub fn fly(args: [String]) -> Void {
           println("Hello, Flylang!");
       }
   }
   ```

### Configuring the Language Server

The plugin automatically starts the Firefly Language Server when you open a `.fly` file. The LSP provides:
- Type checking
- Code completion
- Diagnostics
- Hover information
- Code navigation

### Keyboard Shortcuts

| Action | Windows/Linux | macOS |
|--------|--------------|-------|
| Code Completion | Ctrl+Space | Cmd+Space |
| Quick Documentation | Ctrl+Q | Cmd+Q |
| Go to Declaration | Ctrl+B | Cmd+B |
| Find Usages | Alt+F7 | Cmd+F7 |
| Parameter Info | Ctrl+P | Cmd+P |
| Comment/Uncomment | Ctrl+/ | Cmd+/ |
| Reformat Code | Ctrl+Alt+L | Cmd+Option+L |

## Customization

### Color Scheme
Customize syntax highlighting colors:
1. Go to **Settings/Preferences** → **Editor** → **Color Scheme** → **Firefly**
2. Modify colors for:
   - Keywords
   - Identifiers
   - Types
   - Strings
   - Numbers
   - Comments
   - Operators

### Code Style
Configure code formatting preferences:
1. Go to **Settings/Preferences** → **Editor** → **Code Style** → **Firefly**
2. Adjust indentation, spacing, and other formatting options

## Features in Detail

### LSP-Powered Code Intelligence

The plugin uses the Firefly Language Server Protocol implementation for advanced code intelligence:

#### Code Completion
```fly
module test::example

spark User {
    name: String,
    age: Int,
    
    fn // <-- Trigger completion here
}
```
Suggestions include:
- Methods from parent types
- Built-in methods
- Type names
- Keywords

#### Diagnostics
Errors and warnings are shown inline:
```fly
module test::example

class Main {
    pub fn fly(args: [String]) -> Void {
        let x = "hello";
        x = 42;  // ❌ Error: Type mismatch
    }
}
```

#### Hover Information
Hover over any symbol to see:
- Type information
- Documentation
- Signature details

### File Templates

#### Firefly Main Template
```fly
module ${MODULE_NAME}

class Main {
    pub fn fly(args: [String]) -> Void {
        println("Hello, Flylang!");
    }
}
```

#### Firefly Spark Template
```fly
module ${MODULE_NAME}

spark ${NAME} {
    // Add fields here
    
    validate {
        // Add validation logic
        true
    }
}
```

## Troubleshooting

### Language Server Not Starting

**Problem**: LSP features not working (no completion, diagnostics, etc.)

**Solutions**:
1. Check IntelliJ IDEA logs: **Help** → **Show Log in Finder/Explorer**
2. Restart the IDE
3. Invalidate caches: **File** → **Invalidate Caches / Restart**
4. Ensure Java 21+ is installed

### Syntax Highlighting Not Working

**Problem**: `.fly` files shown as plain text

**Solutions**:
1. Verify file extension is `.fly` (not `.fly.txt`)
2. Right-click file → **Associate with File Type** → **Firefly**
3. Reinstall the plugin

### Performance Issues

**Problem**: IDE becomes slow when editing `.fly` files

**Solutions**:
1. Increase IDE memory: **Help** → **Edit Custom VM Options**
   ```
   -Xmx4096m
   -Xms512m
   ```
2. Disable unused plugins
3. Close unnecessary tool windows

## Development

### Building from Source

Prerequisites:
- IntelliJ IDEA 2023.2+
- Java 21+
- Gradle 8.0+

Build steps:
```bash
cd ide-plugins/intellij-firefly
./gradlew build
```

The plugin ZIP will be in `build/distributions/`.

### Testing
```bash
./gradlew test
```

### Running Plugin in Development Mode
```bash
./gradlew runIde
```

## Contributing

We welcome contributions! To contribute:

1. Fork the [repository](https://github.com/firefly-oss/firefly-lang)
2. Create a feature branch
3. Make your changes
4. Add tests
5. Submit a pull request

## Support

- **Issues**: [GitHub Issues](https://github.com/firefly-oss/firefly-lang/issues)
- **Discussions**: [GitHub Discussions](https://github.com/firefly-oss/firefly-lang/discussions)
- **Documentation**: [Official Docs](https://docs.fireflyframework.com/flylang)

## License

Copyright 2025 Firefly Software Solutions Inc.

Licensed under the Apache License, Version 2.0. See [LICENSE](../../LICENSE) for details.

## Changelog

### Version 1.0-Alpha (Current)
- ✨ Initial release
- ✅ Full syntax highlighting
- ✅ LSP integration
- ✅ Code completion
- ✅ Real-time diagnostics
- ✅ Hover documentation
- ✅ 8 file templates
- ✅ Run configurations

---

**Made with ❤️ by Firefly Software Solutions Inc.**
