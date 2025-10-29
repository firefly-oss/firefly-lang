#!/usr/bin/env bash
# Flylang Installer - Firefly Programming Language
# Installs the Flylang compiler, CLI, runtime, and stdlib system-wide (or to a custom prefix)
set -euo pipefail

# -----------------------------
# Defaults and globals
# -----------------------------
PREFIX="/usr/local"
INSTALL_BIN=""
INSTALL_LIB=""
REPO="https://github.com/firefly-oss/firefly-lang.git"
BRANCH="main"
CLONE_DIR=""
NONINTERACTIVE="0"
QUIET="0"
SKIP_BUILD="0"
FROM_SOURCE="0"

# Colors
bold() { printf "\033[1m%s\033[0m\n" "$*"; }
info() { printf "[INFO] %s\n" "$*"; }
success() { printf "\033[32m[OK]\033[0m %s\n" "$*"; }
warn() { printf "\033[33m[WARN]\033[0m %s\n" "$*"; }
error() { printf "\033[31m[ERR]\033[0m %s\n" "$*"; }

# -----------------------------
# Helpers
# -----------------------------
need_cmd() {
  command -v "$1" >/dev/null 2>&1 || { error "Required command not found: $1"; exit 1; }
}

require_writable_dir() {
  local dir="$1"
  if [ ! -d "$dir" ]; then
    mkdir -p "$dir" 2>/dev/null || true
  fi
  if [ ! -w "$dir" ]; then
    # Check if directory is under user's home - if so, don't use sudo
    if [[ "$dir" == "$HOME"* ]]; then
      # Try to create it without sudo first
      if ! mkdir -p "$dir" 2>/dev/null; then
        error "Directory $dir is not writable. Please check permissions."
        exit 1
      fi
    else
      # Not in home directory, may need sudo
      if command -v sudo >/dev/null 2>&1; then
        SUDO="sudo"
      else
        SUDO=""
      fi
      if [ -n "${SUDO}" ]; then
        ${SUDO} mkdir -p "$dir"
      else
        error "Directory $dir is not writable and 'sudo' is not available. Run with sudo or use --prefix to a writable path."
        exit 1
      fi
    fi
  fi
}

install_file() {
  local src="$1" dst="$2"
  require_writable_dir "$(dirname "$dst")"
  if [ -n "${SUDO:-}" ]; then
    ${SUDO} cp -f "$src" "$dst"
  else
    cp -f "$src" "$dst"
  fi
}

write_file() {
  # usage: write_file <dst> <mode> <content>
  local dst="$1" mode="$2"; shift 2
  require_writable_dir "$(dirname "$dst")"
  if [ -n "${SUDO:-}" ]; then
    printf "%s" "$*" | ${SUDO} tee "$dst" >/dev/null
    ${SUDO} chmod "$mode" "$dst"
  else
    printf "%s" "$*" >"$dst"
    chmod "$mode" "$dst"
  fi
}

cleanup() {
  if [ -n "${TMP_DIR:-}" ] && [ -d "$TMP_DIR" ]; then
    rm -rf "$TMP_DIR" || true
  fi
}
trap cleanup EXIT

usage() {
  cat <<EOF
Flylang Installer - Firefly Programming Language

Usage: install.sh [options]

Options:
  --prefix <dir>          Installation prefix (default: /usr/local)
  --bin-dir <dir>         Installation bin dir (default: <prefix>/bin)
  --lib-dir <dir>         Installation lib dir (default: <prefix>/lib/firefly)
  --branch <name>         Git branch or tag to install (default: main)
  --repo <url>            Git repository URL (default: ${REPO})
  --clone-dir <dir>       Reuse an existing clone/build directory
  --from-source           Install from current directory (local sources)
  --skip-build            Do not rebuild; only (re)install from existing build outputs
  -y, --yes               Non-interactive (assume yes)
  -q, --quiet             Less output
  -h, --help              Show this help

Examples:
  # Install from local sources (current directory)
  bash scripts/install.sh --from-source

  # Standard install from GitHub (may prompt for sudo)
  bash install.sh

  # Install to custom location without sudo
  bash install.sh --prefix "${HOME}/.local"

  # Install a specific tag from GitHub
bash install.sh --branch v1.0-Alpha
EOF
}

# -----------------------------
# Parse arguments
# -----------------------------
while [ $# -gt 0 ]; do
  case "$1" in
    --prefix) PREFIX="$2"; shift 2;;
    --bin-dir) INSTALL_BIN="$2"; shift 2;;
    --lib-dir) INSTALL_LIB="$2"; shift 2;;
    --branch) BRANCH="$2"; shift 2;;
    --repo) REPO="$2"; shift 2;;
    --clone-dir) CLONE_DIR="$2"; shift 2;;
    --from-source) FROM_SOURCE="1"; shift 1;;
    --skip-build) SKIP_BUILD="1"; shift 1;;
    -y|--yes) NONINTERACTIVE="1"; shift 1;;
    -q|--quiet) QUIET="1"; shift 1;;
    -h|--help) usage; exit 0;;
    *) error "Unknown option: $1"; usage; exit 1;;
  esac
done

INSTALL_BIN="${INSTALL_BIN:-${PREFIX}/bin}"
INSTALL_LIB="${INSTALL_LIB:-${PREFIX}/lib/firefly}"

# -----------------------------
# Pre-flight checks
# -----------------------------
need_cmd git
need_cmd java
need_cmd mvn
need_cmd bash
need_cmd uname

JAVA_VER=$(java -version 2>&1 | head -n1 | sed -E 's/.*version "([0-9]+).*/\1/') || true
if ! [[ "$JAVA_VER" =~ ^[0-9]+$ ]]; then
  warn "Could not detect Java version; continuing"
else
  if [ "$JAVA_VER" -lt 17 ]; then
    warn "Detected Java $JAVA_VER. Flylang recommends Java 21+."
  fi
fi

bold "Installing Flylang - Firefly Programming Language (prefix=${PREFIX})"

# Resolve sudo if needed for install paths
require_writable_dir "$INSTALL_BIN"
require_writable_dir "$INSTALL_LIB"

# -----------------------------
# Obtain source
# -----------------------------
if [ "$FROM_SOURCE" = "1" ]; then
  # Install from current directory
  SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
  SRC_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"

  # Verify we're in a flylang/firefly-lang directory
  if [ ! -f "$SRC_DIR/pom.xml" ] || ! grep -q "firefly-lang" "$SRC_DIR/pom.xml" 2>/dev/null; then
    error "Not in a Flylang source directory. Run from the repository root or use --clone-dir"
    exit 1
  fi

  info "Installing from local sources: $SRC_DIR"
elif [ -n "$CLONE_DIR" ] && [ -d "$CLONE_DIR/.git" ]; then
  SRC_DIR="$CLONE_DIR"
  info "Using existing clone: $SRC_DIR"
else
  TMP_DIR=$(mktemp -d 2>/dev/null || mktemp -d -t firefly-install)
  SRC_DIR="$TMP_DIR/firefly-lang"
  info "Cloning ${REPO} (${BRANCH})..."
  git clone --depth 1 --branch "$BRANCH" "$REPO" "$SRC_DIR" >/dev/null
fi

# -----------------------------
# Build
# -----------------------------
if [ "$SKIP_BUILD" = "0" ]; then
  info "Building Flylang (this may take a minute)..."
  (cd "$SRC_DIR" && mvn -q -DskipTests clean install)
  success "Build complete"
else
  info "Skipping build as requested"
fi

# -----------------------------
# Install artifacts
# -----------------------------
info "Installing libraries to ${INSTALL_LIB}"

# Clean lib dir first (safe)
if [ -n "${SUDO:-}" ]; then ${SUDO} rm -rf "$INSTALL_LIB"; else rm -rf "$INSTALL_LIB"; fi
require_writable_dir "$INSTALL_LIB"

# Find and install the fly-cli fat JAR
CLI_JAR="$SRC_DIR/firefly-cli/target/fly-cli.jar"
if [ ! -f "$CLI_JAR" ]; then
  error "Could not find fly-cli.jar at $CLI_JAR"
  error "Make sure the build completed successfully"
  exit 1
fi

info "Installing Flylang CLI..."
install_file "$CLI_JAR" "$INSTALL_LIB/fly-cli.jar"

# Also install individual components for reference/development
info "Installing Flylang components..."

# Install runtime
RUNTIME_JAR=$(ls -1 "$SRC_DIR"/firefly-runtime/target/firefly-runtime-*.jar 2>/dev/null | grep -v sources | grep -v javadoc | head -n1 || true)
if [ -n "$RUNTIME_JAR" ] && [ -f "$RUNTIME_JAR" ]; then
  install_file "$RUNTIME_JAR" "$INSTALL_LIB/$(basename "$RUNTIME_JAR")"
fi

# Install stdlib
STDLIB_JAR=$(ls -1 "$SRC_DIR"/firefly-stdlib/target/firefly-stdlib-*.jar 2>/dev/null | grep -v sources | grep -v javadoc | head -n1 || true)
if [ -n "$STDLIB_JAR" ] && [ -f "$STDLIB_JAR" ]; then
  install_file "$STDLIB_JAR" "$INSTALL_LIB/$(basename "$STDLIB_JAR")"
fi

# Install compiler
COMPILER_JAR=$(ls -1 "$SRC_DIR"/firefly-compiler/target/firefly-compiler-*.jar 2>/dev/null | grep -v sources | grep -v javadoc | head -n1 || true)
if [ -n "$COMPILER_JAR" ] && [ -f "$COMPILER_JAR" ]; then
  install_file "$COMPILER_JAR" "$INSTALL_LIB/$(basename "$COMPILER_JAR")"
fi

# Create launcher script
LAUNCHER_PATH="$INSTALL_BIN/fly"
info "Installing launcher to ${LAUNCHER_PATH}"

LAUNCHER_CONTENT='#!/usr/bin/env bash
set -euo pipefail
# Flylang CLI launcher - Firefly Programming Language
INSTALL_DIR="'"$INSTALL_LIB"'"
CLI_JAR="$INSTALL_DIR/fly-cli.jar"

if [ ! -f "$CLI_JAR" ]; then
  echo "[fly] Error: CLI jar not found at $CLI_JAR" >&2
  exit 1
fi

exec java ${JAVA_OPTS:-} -jar "$CLI_JAR" "$@"
'

write_file "$LAUNCHER_PATH" 0755 "$LAUNCHER_CONTENT"

success "Flylang installed successfully!"

cat <<POST

$(bold "🎉 Installation Complete!")

$(bold "🔥 Flylang - Firefly Programming Language")
   Copyright © 2025 Firefly Software Solutions Inc.
   https://fireflyframework.com/flylang

$(bold "Quick Start:")
  • Check version:     fly version
  • Get help:          fly help
  • Compile a file:    fly compile myfile.fly
  • Run a file:        fly run myfile.fly

$(bold "What was installed:")
  ✓ Flylang CLI        - Full-featured command-line interface
  ✓ Flylang Compiler   - Compiles .fly files to JVM bytecode
  ✓ Flylang Runtime    - Runtime support (actors, futures, collections)
  ✓ Flylang Stdlib     - Standard library modules

$(bold "Installation Details:")
  • Install prefix:    $PREFIX
  • Libraries at:      $INSTALL_LIB
  • Launcher at:       $LAUNCHER_PATH
  • Source:            $([ "$FROM_SOURCE" = "1" ] && echo "Local sources" || echo "$REPO@$BRANCH")

$(bold "Next Steps:")
  • Try the examples:  cd $SRC_DIR/examples && fly run hello-world/Main.fly
  • Read the docs:     https://fireflyframework.com/flylang
  • Update Flylang:    bash install.sh --branch <tag-or-branch>
  • Uninstall:         bash $SRC_DIR/scripts/uninstall.sh

$(bold "Environment:")
  • Add to PATH if needed: export PATH="\$PATH:$INSTALL_BIN"
  • Java version:      $(java -version 2>&1 | head -n1)

Happy coding with Flylang! 🔥

POST
