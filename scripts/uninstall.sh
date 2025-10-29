#!/usr/bin/env bash
# Flylang Uninstaller - Firefly Programming Language
set -euo pipefail

PREFIX="${1:-/usr/local}"
INSTALL_BIN="${PREFIX}/bin"
INSTALL_LIB="${PREFIX}/lib/firefly"
LAUNCHER="${INSTALL_BIN}/fly"

bold() { printf "\033[1m%s\033[0m\n" "$*"; }
info() { printf "[INFO] %s\n" "$*"; }
success() { printf "\033[32m[OK]\033[0m %s\n" "$*"; }
error() { printf "\033[31m[ERR]\033[0m %s\n" "$*"; }

bold "Uninstalling Flylang - Firefly Programming Language"

SUDO=""
if [ ! -w "$INSTALL_BIN" ] || [ ! -w "$(dirname "$INSTALL_LIB")" ]; then
  if command -v sudo >/dev/null 2>&1; then
    info "Requesting elevated privileges..."
    SUDO="sudo"
  else
    error "Insufficient permissions and sudo not available"
    exit 1
  fi
fi

# Remove library directory
if [ -d "$INSTALL_LIB" ]; then
  info "Removing $INSTALL_LIB"
  ${SUDO} rm -rf "$INSTALL_LIB"
else
  info "Library directory not found: $INSTALL_LIB"
fi

# Remove launcher
if [ -f "$LAUNCHER" ]; then
  info "Removing $LAUNCHER"
  ${SUDO} rm -f "$LAUNCHER"
else
  info "Launcher not found: $LAUNCHER"
fi

success "Flylang uninstalled successfully"
info "To reinstall: curl -fsSL https://raw.githubusercontent.com/firefly-oss/firefly-lang/main/scripts/install.sh | bash"
