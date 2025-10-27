#!/usr/bin/env bash
# Firefly Language Installer
# Installs the Firefly compiler CLI system-wide (or to a custom prefix)
set -euo pipefail

# -----------------------------
# Defaults and globals
# -----------------------------
PREFIX="/usr/local"
INSTALL_BIN="${PREFIX}/bin"
INSTALL_LIB="${PREFIX}/lib/firefly"
REPO="https://github.com/firefly-oss/firefly-lang.git"
BRANCH="main"
CLONE_DIR=""
NONINTERACTIVE="0"
QUIET="0"
SKIP_BUILD="0"

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
Firefly Language Installer

Usage: install.sh [options]

Options:
  --prefix <dir>          Installation prefix (default: /usr/local)
  --bin-dir <dir>         Installation bin dir (default: <prefix>/bin)
  --lib-dir <dir>         Installation lib dir (default: <prefix>/lib/firefly)
  --branch <name>         Git branch or tag to install (default: main)
  --repo <url>            Git repository URL (default: ${REPO})
  --clone-dir <dir>       Reuse an existing clone/build directory
  --skip-build            Do not rebuild; only (re)install from existing build outputs
  -y, --yes               Non-interactive (assume yes)
  -q, --quiet             Less output
  -h, --help              Show this help

Examples:
  # Standard install (may prompt for sudo)
  bash install.sh

  # Install to custom location without sudo
  bash install.sh --prefix "${HOME}/.local"

  # Install a specific tag
  bash install.sh --branch v0.4.0
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
    warn "Detected Java $JAVA_VER. Firefly recommends Java 21+."
  fi
fi

bold "Installing Firefly Language (prefix=${PREFIX})"

# Resolve sudo if needed for install paths
require_writable_dir "$INSTALL_BIN"
require_writable_dir "$INSTALL_LIB"

# -----------------------------
# Obtain source
# -----------------------------
if [ -n "$CLONE_DIR" ] && [ -d "$CLONE_DIR/.git" ]; then
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
  info "Building Firefly (this may take a minute)..."
  (cd "$SRC_DIR" && mvn -q -DskipTests install)
  success "Build complete"
else
  info "Skipping build as requested"
fi

# Prepare dependencies for standalone CLI run
info "Preparing CLI dependencies..."
(cd "$SRC_DIR/firefly-compiler" && mvn -q dependency:copy-dependencies -DoutputDirectory=target/dependency -DincludeScope=runtime)

# -----------------------------
# Install artifacts
# -----------------------------
info "Installing libraries to ${INSTALL_LIB}"

# Clean lib dir first (safe)
if [ -n "${SUDO:-}" ]; then ${SUDO} rm -rf "$INSTALL_LIB"; else rm -rf "$INSTALL_LIB"; fi
require_writable_dir "$INSTALL_LIB"

# Copy main compiler jar
COMPILER_JAR=$(ls -1 "$SRC_DIR"/firefly-compiler/target/*-SNAPSHOT.jar "$SRC_DIR"/firefly-compiler/target/*.jar 2>/dev/null | head -n1 || true)
if [ -z "$COMPILER_JAR" ]; then
  error "Could not find firefly-compiler jar under firefly-compiler/target"; exit 1
fi
install_file "$COMPILER_JAR" "$INSTALL_LIB/$(basename "$COMPILER_JAR")"

# Copy runtime deps
if compgen -G "$SRC_DIR/firefly-compiler/target/dependency/*.jar" >/dev/null; then
  for j in "$SRC_DIR"/firefly-compiler/target/dependency/*.jar; do
    install_file "$j" "$INSTALL_LIB/$(basename "$j")"
  done
fi

# Create launcher script
LAUNCHER_PATH="$INSTALL_BIN/firefly"
info "Installing launcher to ${LAUNCHER_PATH}"

LAUNCHER_CONTENT='#!/usr/bin/env bash
set -euo pipefail
# Firefly CLI launcher
PREFIX_DIR="'"$INSTALL_LIB"'"
# Build classpath from all jars in lib dir
CLASSPATH=""
for jar in "$PREFIX_DIR"/*.jar; do
  if [ -z "$CLASSPATH" ]; then
    CLASSPATH="$jar"
  else
    CLASSPATH="$CLASSPATH:$jar"
  fi
done
if [ -z "$CLASSPATH" ]; then
  echo "[firefly] Error: No jars found in $PREFIX_DIR" >&2
  exit 1
fi
exec java ${JAVA_OPTS:-} -cp "$CLASSPATH" com.firefly.compiler.FireflyCompiler "$@"
'

write_file "$LAUNCHER_PATH" 0755 "$LAUNCHER_CONTENT"

success "Firefly installed"

cat <<POST

$(bold "Next steps")
  • Run:    firefly --help
  • Update: bash install.sh --branch <tag-or-branch>
  • Uninstall:
      rm -rf "$INSTALL_LIB"
      rm -f  "$LAUNCHER_PATH"

$(bold "Notes")
  • Install prefix: $PREFIX
  • Libraries at:   $INSTALL_LIB
  • Launcher at:    $LAUNCHER_PATH
  • Repository:     $REPO@$BRANCH

POST
