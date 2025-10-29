# Flylang Scripts

Utility scripts for building, testing, and installing Flylang.

---

## test.sh

**Unified test runner** for all Flylang tests and examples.

### Interactive Mode
```bash
./scripts/test.sh
```

Launches an interactive menu to selectively run:
- All tests (Maven + Examples)
- Maven unit tests only
- All example projects
- Quick smoke test (subset)
- Specific examples
- Individual examples with verbose output

### Command-Line Options

```bash
./scripts/test.sh --all        # Run all tests and examples
./scripts/test.sh --unit       # Maven unit tests only
./scripts/test.sh --examples   # All example projects
./scripts/test.sh --quick      # Quick smoke test (4 fast examples)
./scripts/test.sh --ci         # CI mode (verbose, no interaction)
./scripts/test.sh --help       # Show usage
```

### Examples

```bash
# Quick verification after building
./scripts/test.sh --quick

# Full test suite before release
./scripts/test.sh --all

# CI/CD pipeline
./scripts/test.sh --ci
```

---

## install.sh

**Installs Flylang CLI** globally from built artifacts or source.

### Usage

```bash
# Install from source to default location (/usr/local)
./scripts/install.sh --from-source

# Install to custom location
./scripts/install.sh --from-source --prefix "$HOME/.local"

# Add to PATH
export PATH="$HOME/.local/bin:$PATH"
echo 'export PATH="$HOME/.local/bin:$PATH"' >> ~/.zshrc  # or ~/.bashrc
```

### Requirements
- Maven artifacts built: `mvn clean install`
- Java 21+ available in PATH

### What Gets Installed
- `fly` CLI executable
- `firefly-compiler.jar`, `firefly-runtime.jar`
- Shell completion scripts (if available)

---

## install-vscode-extension.sh

**Builds and installs VS Code extension** for Flylang.

### Usage

```bash
./scripts/install-vscode-extension.sh
```

This script:
1. Navigates to `ide-plugins/vscode-firefly`
2. Installs dependencies (`npm ci`)
3. Compiles TypeScript (`npm run compile`)
4. Packages extension (`.vsix`)
5. Installs to VS Code

### Requirements
- Node.js 18+
- VS Code CLI (`code` command)
- Optional: `vsce` for packaging (`npm install -g @vscode/vsce`)

---

## build-intellij-plugin.sh

**Builds IntelliJ IDEA plugin** for Flylang.

### Usage

```bash
./scripts/build-intellij-plugin.sh
```

This script:
1. Navigates to `ide-plugins/intellij-firefly`
2. Runs Gradle build task
3. Produces plugin ZIP in `build/distributions/`

### Installation
After building, install manually:
1. Open IntelliJ IDEA
2. **Settings → Plugins → Install Plugin from Disk**
3. Select `build/distributions/intellij-firefly-1.0-Alpha.zip`
4. Restart IntelliJ

### Requirements
- Java 21+
- Gradle wrapper included in project

---

## uninstall.sh

**Removes globally installed Flylang CLI**.

### Usage

```bash
./scripts/uninstall.sh
```

Removes:
- `fly` executable
- Flylang JARs
- Configuration files

Use `--prefix` to specify custom installation location:
```bash
./scripts/uninstall.sh --prefix "$HOME/.local"
```

---

## Summary

| Script | Purpose | Usage |
|--------|---------|-------|
| **test.sh** | Run tests & examples | `./scripts/test.sh` (interactive) or `./scripts/test.sh --all` |
| **install.sh** | Install Flylang CLI | `./scripts/install.sh --from-source --prefix ~/.local` |
| **install-vscode-extension.sh** | Build & install VS Code plugin | `./scripts/install-vscode-extension.sh` |
| **build-intellij-plugin.sh** | Build IntelliJ plugin | `./scripts/build-intellij-plugin.sh` |
| **uninstall.sh** | Remove Flylang CLI | `./scripts/uninstall.sh` |

---

**For detailed documentation**, see:
- [GETTING_STARTED.md](../docs/GETTING_STARTED.md) — Installation and setup
- [EXAMPLES.md](../docs/EXAMPLES.md) — Running examples
- [PUBLISH_CHECKLIST_1.0-Alpha.md](../docs/PUBLISH_CHECKLIST_1.0-Alpha.md) — Release process
