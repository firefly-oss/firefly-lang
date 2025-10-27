# Firefly Language Support for VS Code

Official Visual Studio Code extension for the Firefly programming language.

## Features

- **Syntax Highlighting** - Full syntax highlighting for `.fly` files
- **Auto-Completion** - Bracket matching and auto-closing
- **Comment Toggling** - Quick comment/uncomment with Cmd+/
- **Code Folding** - Fold code blocks for better navigation
- **Indentation** - Smart indentation support

## Supported Syntax

### Keywords
- **Control Flow**: `if`, `else`, `match`, `for`, `while`, `in`, `return`, `break`, `continue`
- **Declarations**: `fn`, `let`, `mut`, `class`, `interface`, `init`, `new`
- **Modifiers**: `public`, `private`, `protected`, `static`, `final`
- **Other**: `package`, `import`, `self`, `extends`, `implements`

### Types
- **Primitives**: `Int`, `Long`, `Float`, `Double`, `Boolean`, `String`, `Unit`
- **Collections**: `Array`, `List`, `Map`
- **Custom Types**: Any PascalCase identifier

### Features
- Annotations: `@RestController`, `@Service`, `@Autowired`, etc.
- Comments: Single-line `//` and multi-line `/* */`
- String literals with escape sequences
- Numeric literals (integers, floats, hex, binary, octal)
- Operators and punctuation

## Installation

### From Source

1. Clone the repository:
```bash
git clone https://github.com/firefly-oss/firefly-lang.git
cd firefly-lang/vscode-firefly
```

2. Copy to VS Code extensions directory:
```bash
# macOS/Linux
cp -r . ~/.vscode/extensions/firefly-language-0.4.0

# Windows
xcopy /E /I . %USERPROFILE%\.vscode\extensions\firefly-language-0.4.0
```

3. Reload VS Code

### From VSIX (Coming Soon)

```bash
code --install-extension firefly-language-0.4.0.vsix
```

## Usage

1. Create a file with `.fly` extension
2. Start writing Firefly code
3. Enjoy syntax highlighting and editor features!

## Example

```firefly
package com.example

import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/users")
class UserController {
    @Autowired
    let userService: UserService;
    
    @GetMapping("/{id}")
    fn getUser(@PathVariable id: String) -> User {
        userService.findById(id)
    }
}
```

## Language Features

| Feature | Status |
|---------|--------|
| Syntax Highlighting | ✅ Complete |
| Bracket Matching | ✅ Complete |
| Auto-Completion | ✅ Complete |
| Comment Toggle | ✅ Complete |
| Code Folding | ✅ Complete |
| Snippets | 🚧 Planned |
| IntelliSense | 🚧 Planned |
| Error Checking | 🚧 Planned |
| Debugging | 🚧 Future |

## Contributing

Contributions welcome! See [CONTRIBUTING.md](../CONTRIBUTING.md) for guidelines.

## Issues

Report issues at: https://github.com/firefly-oss/firefly-lang/issues

## License

MIT License - see [LICENSE](../LICENSE) for details

## Links

- **Repository**: https://github.com/firefly-oss/firefly-lang
- **Documentation**: https://github.com/firefly-oss/firefly-lang/blob/main/README.md
- **Language Guide**: https://github.com/firefly-oss/firefly-lang/blob/main/GUIDE.md

---

**Developed by Firefly Software Solutions Inc. 🔥**
