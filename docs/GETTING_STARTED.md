# Getting Started with Flylang

This guide walks you from installation to running your first Flylang program, setting up a project, and configuring your editor.

---

## Prerequisites

Before installing Flylang, ensure you have:
- **Java 21+** (verify with `java -version`)
- **Maven 3.8+** (verify with `mvn -version`)
- **Node.js 18+** (only if building editor plugins locally)

If you're missing any of these, install them first:
```bash
# macOS (Homebrew)
brew install openjdk@21 maven node

# Ubuntu/Debian
sudo apt install openjdk-21-jdk maven nodejs npm

# Windows (Scoop)
scoop install openjdk21 maven nodejs
```

---

## Installation

### Option 1: Build from Source (Recommended)

```bash
# Clone the repository
git clone https://github.com/your-org/firefly-lang.git
cd firefly-lang

# Build all modules (compiler, runtime, Maven plugin)
mvn clean install -DskipTests

# Optional: Install CLI globally
bash scripts/install.sh --from-source --prefix "$HOME/.local"
export PATH="$HOME/.local/bin:$PATH"

# Verify installation
fly version
# Expected: Flylang CLI 1.0-Alpha
```

### Option 2: Use Maven Plugin Only

If you only need to compile Flylang in Maven projects, skip the CLI installation and configure the plugin (see below).

---

## Your First Flylang Program

### Step 1: Run an Existing Example

```bash
# From the firefly-lang repo root
fly run examples/hello-world

# Expected output:
# Hello, Flylang!
```

### Step 2: Explore the Source

Let's look at the code:
```bash
cat examples/hello-world/src/main/firefly/examples/hello_world/Main.fly
```

```fly path=null start=null
module examples::hello_world

class Main {
  pub fn fly(args: [String]) -> Void {
    println("Hello, Flylang!");
  }
}
```

**Key points:**
- Every file starts with a `module` declaration
- Entry point is `pub fn fly(args: [String]) -> Void`
- `println` is a built-in function (no import needed)
- Semicolons terminate statements; last expression returns

### Step 3: Modify and Re-run

```bash
# Edit the file
echo 'module examples::hello_world

class Main {
  pub fn fly(args: [String]) -> Void {
    let name: String = "World";
    println("Hello, " + name + "!");
    println("Welcome to Flylang v1.0-Alpha");
  }
}' > examples/hello-world/src/main/firefly/examples/hello_world/Main.fly

# Re-run
fly run examples/hello-world
```

---

## Create a New Maven Project

### Step 1: Project Structure

```bash
mkdir my-flylang-app
cd my-flylang-app

# Create Maven structure
mkdir -p src/main/firefly/com/example/app
mkdir -p src/main/resources
```

### Step 2: Add pom.xml

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
  <modelVersion>4.0.0</modelVersion>

  <groupId>com.example</groupId>
  <artifactId>my-flylang-app</artifactId>
  <version>1.0.0</version>

  <properties>
    <maven.compiler.source>21</maven.compiler.source>
    <maven.compiler.target>21</maven.compiler.target>
    <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
  </properties>

  <dependencies>
    <dependency>
      <groupId>com.firefly</groupId>
      <artifactId>firefly-runtime</artifactId>
      <version>1.0-Alpha</version>
    </dependency>
  </dependencies>

  <build>
    <plugins>
      <plugin>
        <groupId>com.firefly</groupId>
        <artifactId>firefly-maven-plugin</artifactId>
        <version>1.0-Alpha</version>
        <executions>
          <execution>
            <goals>
              <goal>compile</goal>
            </goals>
          </execution>
        </executions>
      </plugin>
      <plugin>
        <groupId>org.apache.maven.plugins</groupId>
        <artifactId>maven-compiler-plugin</artifactId>
        <version>3.11.0</version>
      </plugin>
    </plugins>
  </build>
</project>
```

### Step 3: Write Your First App

Create `src/main/firefly/com/example/app/Main.fly`:

```fly path=null start=null
module com::example::app

class Main {
  pub fn fly(args: [String]) -> Void {
    let greeting: String = self::buildGreeting("Developer");
    println(greeting);
  }

  pub fn buildGreeting(name: String) -> String {
    "Hello, " + name + "! Welcome to Flylang."
  }
}
```

### Step 4: Build and Run

```bash
# Compile
mvn clean compile

# Run (the plugin generates a main class)
java -cp target/classes com.example.app.Main

# Expected output:
# Hello, Developer! Welcome to Flylang.
```

---

## Editor Setup

### VS Code

1. **Build the extension:**
   ```bash
   cd ide-plugins/vscode-firefly
   npm ci
   npm run compile
   vsce package  # or: npx vsce package
   ```

2. **Install the VSIX:**
   ```bash
   code --install-extension firefly-lang-*.vsix
   ```

3. **Configure LSP path (if needed):**
   Add to `.vscode/settings.json`:
   ```json
   {
     "firefly.lspPath": "/path/to/firefly-lang/firefly-lsp/target/firefly-lsp.jar"
   }
   ```

4. **Verify:**
   - Open a `.fly` file
   - Syntax highlighting should appear
   - Hover over symbols for documentation
   - Press `Ctrl+Space` for completions

### IntelliJ IDEA

1. **Build the plugin:**
   ```bash
   cd ide-plugins/intellij-firefly
   ./gradlew buildPlugin
   ```

2. **Install:**
   - Open IntelliJ
   - Go to **Settings → Plugins → Install Plugin from Disk**
   - Select `build/distributions/intellij-firefly-1.0-Alpha.zip`
   - Restart IntelliJ

3. **Verify:**
   - Open a `.fly` file
   - Syntax highlighting and basic navigation should work

---

## Troubleshooting

### "fly: command not found"
→ Ensure `$HOME/.local/bin` is in your PATH:
```bash
export PATH="$HOME/.local/bin:$PATH"
echo 'export PATH="$HOME/.local/bin:$PATH"' >> ~/.zshrc  # or ~/.bashrc
```

### "Could not find artifact com.firefly:firefly-runtime"
→ Run `mvn clean install` from the firefly-lang repo root to install locally.

### "LSP not starting in VS Code"
→ Check that Java 21+ is available:
```bash
java -version
```
→ Verify LSP jar exists:
```bash
ls firefly-lsp/target/firefly-lsp.jar
```
→ Check VS Code output panel (**View → Output → Firefly Language Server**).

### "Maven compilation fails with syntax errors"
→ Validate your `.fly` file syntax:
```bash
fly compile src/main/firefly/path/to/File.fly
```
→ Check for:
- Missing `module` declaration at the top
- Incorrect `::` vs `.` usage (methods use `::`, fields use `.`)
- Type annotations on `let` bindings

### "No syntax highlighting in editor"
→ Ensure the plugin is installed and the file extension is `.fly`.

---

## Running Tests

Flylang includes a unified, interactive test runner for running all tests and examples:

### Interactive Mode
```bash
./scripts/test.sh
```

This launches an interactive menu where you can:
- Run all tests (Maven + Examples)
- Run Maven unit tests only
- Run all example projects
- Run a quick smoke test (subset of examples)
- Select specific examples to run
- Run individual examples with verbose output

### Command-Line Mode

For automation or CI/CD:

```bash
# Run everything
./scripts/test.sh --all

# Run Maven unit tests only
./scripts/test.sh --unit

# Run all examples
./scripts/test.sh --examples

# Quick smoke test (fast examples)
./scripts/test.sh --quick

# CI mode (verbose, no interaction)
./scripts/test.sh --ci
```

### Verify Your Installation

After building from source, verify everything works:

```bash
# Quick verification (runs 4 fast examples)
./scripts/test.sh --quick

# Full verification
./scripts/test.sh --all
```

---

## Next Steps

### Quick Wins
1. **Learn the syntax:** Read [LANGUAGE_GUIDE.md](LANGUAGE_GUIDE.md) (start with Overview through Data Types)
2. **Try patterns:** Copy snippets from [RECIPES.md](RECIPES.md)
3. **Run examples:** Explore [EXAMPLES.md](EXAMPLES.md) for hands-on code

### Intermediate Projects
4. **Build a REST API:** Follow [SPRING_BOOT_GUIDE.md](SPRING_BOOT_GUIDE.md)
5. **Explore async:** Study concurrency examples (`async-demo`, `futures-combinators-demo`)
6. **Pattern matching:** Try `data-patterns-demo` and `patterns-demo`

### Deep Dive
7. **Understand internals:** Read [ARCHITECTURE.md](ARCHITECTURE.md)
8. **Compiler details:** Study [IMPLEMENTATION_GUIDE.md](IMPLEMENTATION_GUIDE.md)
9. **Contribute:** Check GitHub issues and the contribution guide

---

*Happy coding with Flylang! 🚀*
