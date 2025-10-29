#!/usr/bin/env bash
# Flylang Uninstaller - Firefly Programming Language
set -euo pipefail

# -----------------------------
# Defaults and globals
# -----------------------------
PREFIX="/usr/local"
INSTALL_BIN=""
INSTALL_LIB=""
NONINTERACTIVE="0"
QUIET="0"
SUDO=""

# UI Colors - detect support properly
if [[ -t 1 ]] && [[ -z "${NO_COLOR:-}" ]] && command -v tput >/dev/null 2>&1; then
  BOLD=$(tput bold 2>/dev/null || echo '')
  DIM=$(tput dim 2>/dev/null || echo '')
  RESET=$(tput sgr0 2>/dev/null || echo '')
  RED=$(tput setaf 1 2>/dev/null || echo '')
  GREEN=$(tput setaf 2 2>/dev/null || echo '')
  YELLOW=$(tput setaf 3 2>/dev/null || echo '')
  BLUE=$(tput setaf 4 2>/dev/null || echo '')
  CYAN=$(tput setaf 6 2>/dev/null || echo '')
  WHITE=$(tput setaf 7 2>/dev/null || echo '')
  ORANGE="\033[38;5;208m"
else
  BOLD=""; DIM=""; RESET=""; RED=""; GREEN=""; YELLOW=""; BLUE=""; CYAN=""; WHITE=""; ORANGE=""
fi

# ═══════════════════════════════════════════════════════════════
# UI Functions
# ═══════════════════════════════════════════════════════════════

bold() { printf "%b%s%b\n" "$BOLD" "$*" "$RESET"; }
info() { printf "%b[%b●%b]%b %s\n" "$DIM" "$CYAN" "$DIM" "$RESET" "$*"; }
success() { printf "%b[%b✓%b]%b %s\n" "$DIM" "$GREEN" "$DIM" "$RESET" "$*"; }
warn() { printf "%b[%b!%b]%b %s\n" "$DIM" "$YELLOW" "$DIM" "$RESET" "$*"; }
error() { printf "%b[%b✗%b]%b %s\n" "$DIM" "$RED" "$DIM" "$RESET" "$*" >&2; }
step() { printf "\n%b┌─%b %s\n%b└─────────────────────────────────────────────────────────────%b\n\n" "$DIM$CYAN" "$BOLD$WHITE" "$*" "$DIM" "$RESET"; }

is_tty() { [[ -t 0 ]] && [[ -t 1 ]]; }

prompt_yes_no() {
  local question="$1" default="${2:-default_no}" answer
  local prompt
  
  if [[ "$default" == "default_yes" ]]; then
    prompt="  ${DIM}➜${RESET} ${question} ${DIM}(${GREEN}Y${RESET}${DIM}/${RESET}n${DIM})${RESET} "
    default="y"
  else
    prompt="  ${DIM}➜${RESET} ${question} ${DIM}(${RESET}y${DIM}/${RED}N${RESET}${DIM})${RESET} "
    default="n"
  fi
  
  while true; do
    read -r -p "$(printf "%b" "$prompt")" answer || return 1
    answer="${answer:-$default}"
    answer=$(echo "$answer" | tr '[:upper:]' '[:lower:]')
    case "$answer" in
      y|yes) return 0;;
      n|no) return 1;;
      *) printf "  %b[%b!%b]%b Invalid input. Please enter %by%b or %bn%b\n" "$DIM" "$YELLOW" "$DIM" "$RESET" "$GREEN" "$RESET" "$RED" "$RESET" >&2;;
    esac
  done
}

print_banner() {
  local script_dir repo_root
  script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
  repo_root="$(cd "$script_dir/.." && pwd)"
  
  local -a candidates=(
    "$repo_root/firefly-cli/src/main/resources/firefly-logo.txt"
    "$repo_root/firefly-compiler/src/main/resources/firefly-logo.txt"
  )
  
  clear
  printf "\n"
  
  for banner_file in "${candidates[@]}"; do
    if [[ -f "$banner_file" ]]; then
      printf "%b" "$ORANGE"
      cat "$banner_file"
      printf "%b\n" "$RESET"
      printf "  %b╭──────────────────────────────────────────────────────────╮%b\n" "$DIM" "$RESET"
      printf "  %b│%b  %bFirefly Programming Language%b - Uninstall Wizard     %b│%b\n" "$DIM" "$RESET" "$BOLD$WHITE" "$RESET" "$DIM" "$RESET"
      printf "  %b│%b  %bVersion 1.0-Alpha%b • %b$(date +%Y)%b Firefly Software Solutions  %b│%b\n" "$DIM" "$RESET" "$CYAN" "$RESET" "$DIM" "$RESET" "$DIM" "$RESET"
      printf "  %b╰──────────────────────────────────────────────────────────╯%b\n\n" "$DIM" "$RESET"
      return 0
    fi
  done
  
  # Fallback banner
  printf "%b" "$ORANGE"
  cat <<'BANNER'
  _____.__         .__
_/ ____\  | ___.__.|  | _____    ____    ____
\   __\|  |<   |  ||  | \__  \  /    \  / ___\
 |  |  |  |_\___  ||  |__/ __ \|   |  \/ /_/  >
 |__|  |____/ ____||____(____  /___|  /\___  /
            \/               \/     \//_____/

BANNER
  printf "%b\n" "$RESET"
  printf "  %b╭──────────────────────────────────────────────────────────╮%b\n" "$DIM" "$RESET"
  printf "  %b│%b  %bFirefly Programming Language%b - Uninstall Wizard     %b│%b\n" "$DIM" "$RESET" "$BOLD$WHITE" "$RESET" "$DIM" "$RESET"
  printf "  %b│%b  %bVersion 1.0-Alpha%b • %b$(date +%Y)%b Firefly Software Solutions  %b│%b\n" "$DIM" "$RESET" "$CYAN" "$RESET" "$DIM" "$RESET" "$DIM" "$RESET"
  printf "  %b╰──────────────────────────────────────────────────────────╯%b\n\n" "$DIM" "$RESET"
}

# Graceful Ctrl-C
trap 'echo; warn "Uninstall aborted by user"; exit 130' INT

usage() {
  cat <<EOF
Flylang Uninstaller - Firefly Programming Language

Usage: uninstall.sh [options]

Options:
  --prefix <dir>          Installation prefix (default: /usr/local)
  --bin-dir <dir>         Bin directory (default: <prefix>/bin)
  --lib-dir <dir>         Lib directory (default: <prefix>/lib/firefly)
  -y, --yes               Non-interactive (assume yes)
  -q, --quiet             Less output
  -h, --help              Show this help

Examples:
  # Uninstall from default location
  bash scripts/uninstall.sh

  # Uninstall from custom location
  bash scripts/uninstall.sh --prefix "${HOME}/.local"
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
    -y|--yes) NONINTERACTIVE="1"; shift 1;;
    -q|--quiet) QUIET="1"; shift 1;;
    -h|--help) usage; exit 0;;
    *) error "Unknown option: $1"; usage; exit 1;;
  esac
done

INSTALL_BIN="${INSTALL_BIN:-${PREFIX}/bin}"
INSTALL_LIB="${INSTALL_LIB:-${PREFIX}/lib/firefly}"
LAUNCHER="${INSTALL_BIN}/fly"

# Print banner
print_banner

# -----------------------------
# Interactive confirmation
# -----------------------------
if [[ "$NONINTERACTIVE" == "0" ]] && [[ "$QUIET" == "0" ]] && is_tty; then
  step "${BOLD}Uninstall Confirmation${RESET}"
  
  printf "  %b╭─ Uninstall Summary ──────────────────────────────────────╮%b\n" "$DIM" "$RESET"
  printf "  %b│%b\n" "$DIM" "$RESET"
  printf "  %b│%b  %b%-20s%b %b%s%b\n" "$DIM" "$RESET" "$CYAN" "Install Location" "$RESET" "$BOLD" "$PREFIX" "$RESET"
  printf "  %b│%b  %b%-20s%b %b%s%b\n" "$DIM" "$RESET" "$CYAN" "Binaries" "$RESET" "$WHITE" "${INSTALL_BIN}" "$RESET"
  printf "  %b│%b  %b%-20s%b %b%s%b\n" "$DIM" "$RESET" "$CYAN" "Libraries" "$RESET" "$WHITE" "${INSTALL_LIB}" "$RESET"
  printf "  %b│%b  %b%-20s%b %b%s%b\n" "$DIM" "$RESET" "$CYAN" "Launcher" "$RESET" "$WHITE" "${LAUNCHER}" "$RESET"
  printf "  %b│%b\n" "$DIM" "$RESET"
  printf "  %b╰──────────────────────────────────────────────────────────╯%b\n\n" "$DIM" "$RESET"
  
  if ! prompt_yes_no "Proceed with uninstallation?" default_no; then
    printf "\n  %b[%b✗%b]%b Uninstall cancelled by user\n\n" "$DIM" "$YELLOW" "$DIM" "$RESET"
    exit 0
  fi
  
  printf "\n  %b[%b●%b]%b Starting uninstallation...\n\n" "$DIM" "$CYAN" "$DIM" "$RESET"
fi

bold "Uninstalling Flylang - Firefly Programming Language (prefix=${PREFIX})"

# Check permissions
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
  info "Removing libraries from ${INSTALL_LIB}"
  ${SUDO} rm -rf "$INSTALL_LIB"
  success "Libraries removed"
else
  warn "Library directory not found: $INSTALL_LIB"
fi

# Remove launcher
if [ -f "$LAUNCHER" ]; then
  info "Removing launcher from ${LAUNCHER}"
  ${SUDO} rm -f "$LAUNCHER"
  success "Launcher removed"
else
  warn "Launcher not found: $LAUNCHER"
fi

cat <<POST

$(bold "🎉 Uninstall Complete!")

$(bold "🔥 Flylang - Firefly Programming Language")
   Copyright © 2025 Firefly Software Solutions Inc.
   https://fireflyframework.com/flylang

$(bold "What was removed:")
  ✓ Flylang Libraries  - ${INSTALL_LIB}
  ✓ Flylang Launcher   - ${LAUNCHER}

$(bold "To reinstall Flylang:")
  curl -fsSL https://raw.githubusercontent.com/firefly-oss/firefly-lang/main/scripts/install.sh | bash

  Or from local sources:
  bash scripts/install.sh --from-source

Thank you for using Flylang! 🔥

POST
