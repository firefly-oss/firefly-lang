#!/usr/bin/env bash
# Flylang Installer - Firefly Programming Language
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
COMPONENTS="full"
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
  MAGENTA=$(tput setaf 5 2>/dev/null || echo '')
  CYAN=$(tput setaf 6 2>/dev/null || echo '')
  WHITE=$(tput setaf 7 2>/dev/null || echo '')
  ORANGE="\033[38;5;208m"
  PURPLE="\033[38;5;135m"
  PINK="\033[38;5;213m"
else
  BOLD=""; DIM=""; RESET=""; RED=""; GREEN=""; YELLOW=""; BLUE=""; MAGENTA=""; CYAN=""; WHITE=""; ORANGE=""; PURPLE=""; PINK=""
fi

# ═══════════════════════════════════════════════════════════════
# UI Functions - Developer-ish styled terminal output
# ═══════════════════════════════════════════════════════════════

bold() { printf "%b%s%b\n" "$BOLD" "$*" "$RESET"; }
info() { printf "%b[%b●%b]%b %s\n" "$DIM" "$CYAN" "$DIM" "$RESET" "$*"; }
success() { printf "%b[%b✓%b]%b %s\n" "$DIM" "$GREEN" "$DIM" "$RESET" "$*"; }
warn() { printf "%b[%b!%b]%b %s\n" "$DIM" "$YELLOW" "$DIM" "$RESET" "$*"; }
error() { printf "%b[%b✗%b]%b %s\n" "$DIM" "$RED" "$DIM" "$RESET" "$*" >&2; }
step() { printf "\n%b┌─%b %s\n%b└─────────────────────────────────────────────────────────────%b\n\n" "$DIM$CYAN" "$BOLD$WHITE" "$*" "$DIM" "$RESET"; }

# Animated spinner
spinner() {
  local pid=$1
  local message="${2:-Processing}"
  local delay=0.1
  local spinstr='⠋⠙⠹⠸⠼⠴⠦⠧⠇⠏'
  local i=0
  
  printf "%b[%b" "$DIM" "$CYAN"
  while kill -0 $pid 2>/dev/null; do
    local temp=${spinstr:i++%${#spinstr}:1}
    printf "\r%b[%b%s%b]%b %s" "$DIM" "$CYAN" "$temp" "$DIM" "$RESET" "$message"
    sleep $delay
  done
  printf "\r%b[%b✓%b]%b %s\n" "$DIM" "$GREEN" "$DIM" "$RESET" "$message"
}

# Progress bar
progress_bar() {
  local current=$1
  local total=$2
  local width=50
  local percentage=$((current * 100 / total))
  local filled=$((current * width / total))
  local empty=$((width - filled))
  
  printf "\r%b[" "$DIM"
  printf "%b%${filled}s" "$GREEN" | tr ' ' '█'
  printf "%b%${empty}s" "$DIM" | tr ' ' '░'
  printf "%b] %b%3d%%%b" "$DIM" "$BOLD$CYAN" "$percentage" "$RESET"
}

# Box drawing
box() {
  local text="$1"
  local color="${2:-$CYAN}"
  local width=64
  
  printf "%b%s╔" "$color" "$BOLD"
  printf '═%.0s' $(seq 1 $((width-2)))
  printf "╗%b\n" "$RESET"
  
  printf "%b%s║%b %-$((width-4))s %b%s║%b\n" "$color" "$BOLD" "$WHITE" "$text" "$color" "$BOLD" "$RESET"
  
  printf "%b%s╚" "$color" "$BOLD"
  printf '═%.0s' $(seq 1 $((width-2)))
  printf "╝%b\n" "$RESET"
}

# Typing effect for dramatic flair
type_text() {
  local text="$1"
  local delay="${2:-0.03}"
  local color="${3:-$RESET}"
  
  printf "%b" "$color"
  for ((i=0; i<${#text}; i++)); do
    printf "%s" "${text:$i:1}"
    sleep "$delay"
  done
  printf "%b\n" "$RESET"
}

# -----------------------------
# Core Helpers
# -----------------------------
need_cmd() {
  if ! command -v "$1" >/dev/null 2>&1; then
    error "Required command not found: $1"
    exit 1
  fi
}

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

prompt_input() {
  local question="$1" default="$2" answer
  printf "  ${DIM}➜${RESET} %s\n" "$question"
  read -r -p "  ${DIM}│${RESET} ${DIM}[default: ${CYAN}${default}${RESET}${DIM}]${RESET} " answer || return 1
  echo "${answer:-$default}"
}

prompt_menu() {
  local title="$1" default_idx="$2"
  shift 2
  local -a items=("$@")
  local count=${#items[@]}
  
  printf "\n  %b%s%b\n" "$BOLD$WHITE" "$title" "$RESET" >&2
  printf "  %b" "$DIM" >&2
  printf '─%.0s' $(seq 1 58) >&2
  printf "%b\n\n" "$RESET" >&2
  
  local i=1
  for item in "${items[@]}"; do
    local key="${item%%:*}"
    local desc="${item#*:}"
    local marker=""
    local prefix="  ${DIM}│${RESET}"
    
    if [[ $i -eq $default_idx ]]; then
      printf "%s %b[%d]%b %b%-14s%b ${DIM}→${RESET} %s %b★%b\n" \
        "$prefix" "$BOLD$GREEN" "$i" "$RESET" "$BOLD$CYAN" "$key" "$RESET" "$desc" "$YELLOW" "$RESET" >&2
    else
      printf "%s %b[%d]%b %b%-14s%b ${DIM}→${RESET} %s\n" \
        "$prefix" "$DIM" "$i" "$RESET" "$CYAN" "$key" "$RESET" "$desc" >&2
    fi
    ((i++))
  done
  
  printf "  %b" "$DIM" >&2
  printf '─%.0s' $(seq 1 58) >&2
  printf "%b\n" "$RESET" >&2
  
  local choice
  while true; do
    read -r -p "$(printf "  ${DIM}➜${RESET} ${BOLD}Select${RESET} ${DIM}[${GREEN}%d${RESET}${DIM}]${RESET} (1-%d): " "$default_idx" "$count")" choice || return 1
    choice="${choice:-$default_idx}"
    
    if [[ "$choice" =~ ^[0-9]+$ ]] && (( choice >= 1 && choice <= count )); then
      printf "  %b[%b✓%b]%b Selected: %b%s%b\n" "$DIM" "$GREEN" "$DIM" "$RESET" "$BOLD$CYAN" "${items[$((choice-1))]%%:*}" "$RESET" >&2
      echo "${items[$((choice-1))]%%:*}"
      return 0
    else
      printf "  %b[%b!%b]%b Invalid. Enter a number between %b1%b and %b%d%b.\n" "$DIM" "$YELLOW" "$DIM" "$RESET" "$GREEN" "$RESET" "$GREEN" "$count" "$RESET" >&2
    fi
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
      printf "  %b│%b  %bFirefly Programming Language%b - Installation Wizard   %b│%b\n" "$DIM" "$RESET" "$BOLD$WHITE" "$RESET" "$DIM" "$RESET"
      printf "  %b│%b  %bVersion 1.0-Alpha%b • %b$(date +%Y)%b Firefly Software Solutions  %b│%b\n" "$DIM" "$RESET" "$CYAN" "$RESET" "$DIM" "$RESET" "$DIM" "$RESET"
      printf "  %b╰──────────────────────────────────────────────────────────╯%b\n\n" "$DIM" "$RESET"
      return 0
    fi
  done
  
  # Fallback banner with gradient effect
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
  printf "  %b│%b  %bFirefly Programming Language%b - Installation Wizard   %b│%b\n" "$DIM" "$RESET" "$BOLD$WHITE" "$RESET" "$DIM" "$RESET"
  printf "  %b│%b  %bVersion 1.0-Alpha%b • %b$(date +%Y)%b Firefly Software Solutions  %b│%b\n" "$DIM" "$RESET" "$CYAN" "$RESET" "$DIM" "$RESET" "$DIM" "$RESET"
  printf "  %b╰──────────────────────────────────────────────────────────╯%b\n\n" "$DIM" "$RESET"
}

require_writable_dir() {
  local dir="$1"
  
  # Try to create directory
  if [[ ! -d "$dir" ]]; then
    if ! mkdir -p "$dir" 2>/dev/null; then
      # Need elevated permissions
      if [[ "$dir" == "$HOME"* ]]; then
        error "Cannot create directory: $dir"
        error "Check permissions for your home directory"
        exit 1
      elif command -v sudo >/dev/null 2>&1; then
        info "Creating system directory (requires sudo): $dir"
        sudo mkdir -p "$dir" || exit 1
        SUDO="sudo"
      else
        error "Cannot create directory: $dir"
        error "Use --prefix to install to a user-writable location"
        exit 1
      fi
    fi
  fi
  
  # Check if writable
  if [[ ! -w "$dir" ]]; then
    if [[ "$dir" == "$HOME"* ]]; then
      error "Directory not writable: $dir"
      exit 1
    elif command -v sudo >/dev/null 2>&1; then
      SUDO="sudo"
    else
      error "Directory not writable: $dir"
      error "Use --prefix to install to a user-writable location"
      exit 1
    fi
  fi
}

# -----------------------------
# OS / Package manager detection
# -----------------------------
detect_platform() {
  OS="$(uname -s 2>/dev/null || echo unknown)"
  case "$OS" in
    Darwin) PLATFORM="mac" ;;
    Linux)  PLATFORM="linux" ;;
    MINGW*|MSYS*|CYGWIN*) PLATFORM="windows" ;;
    *) PLATFORM="unknown" ;;
  esac
}

detect_pkg_mgr() {
  PKG_MGR=""; WIN_PKG_MGR="";
  if [ "$PLATFORM" = "mac" ]; then
    if command -v brew >/dev/null 2>&1; then PKG_MGR="brew"; fi
  elif [ "$PLATFORM" = "linux" ]; then
    if   command -v apt-get >/dev/null 2>&1; then PKG_MGR="apt";
    elif command -v dnf >/devnull 2>&1; then PKG_MGR="dnf";
    elif command -v dnf >/dev/null 2>&1; then PKG_MGR="dnf";
    elif command -v pacman >/dev/null 2>&1; then PKG_MGR="pacman";
    elif command -v apk >/dev/null 2>&1; then PKG_MGR="apk";
    elif command -v zypper >/dev/null 2>&1; then PKG_MGR="zypper";
    fi
  elif [ "$PLATFORM" = "windows" ]; then
    if command -v winget >/dev/null 2>&1; then WIN_PKG_MGR="winget";
    elif command -v choco >/dev/null 2>&1; then WIN_PKG_MGR="choco";
    elif command -v scoop >/dev/null 2>&1; then WIN_PKG_MGR="scoop";
    fi
  fi
}

install_pkg_windows() {
  local name="$1" winget_id="$2" choco_pkg="$3" scoop_pkg="$4"
  
  if [[ "$WIN_PKG_MGR" == "winget" ]] && command -v winget >/dev/null 2>&1; then
    info "Installing $name via winget..."
    winget install --id "$winget_id" -e --silent --accept-package-agreements --accept-source-agreements
  elif [[ "$WIN_PKG_MGR" == "choco" ]] && command -v choco >/dev/null 2>&1; then
    info "Installing $name via Chocolatey..."
    choco install -y "$choco_pkg"
  elif [[ "$WIN_PKG_MGR" == "scoop" ]] && command -v scoop >/dev/null 2>&1; then
    info "Installing $name via Scoop..."
    scoop install "$scoop_pkg"
  else
    error "No Windows package manager found"
    error "Install winget, chocolatey, or scoop first"
    return 1
  fi
}

install_pkg() {
  local name="$1" brew_pkg="$2" apt_pkg="$3" dnf_pkg="$4" pac_pkg="$5" apk_pkg="${6:-}" zyp_pkg="${7:-}"
  
  [[ "$PLATFORM" == "windows" ]] && { warn "Cannot install $name on Windows via this function"; return 1; }
  
  local sudo_cmd=""
  [[ -n "$SUDO" ]] && sudo_cmd="sudo"
  
  case "$PKG_MGR" in
    brew)
      info "Installing $name via Homebrew..."
      brew install "$brew_pkg" 2>/dev/null || brew install "$brew_pkg"
      ;;
    apt)
      info "Installing $name via apt..."
      $sudo_cmd apt-get update -qq || true
      $sudo_cmd apt-get install -y "$apt_pkg"
      ;;
    dnf)
      info "Installing $name via dnf..."
      $sudo_cmd dnf install -y "$dnf_pkg"
      ;;
    pacman)
      info "Installing $name via pacman..."
      $sudo_cmd pacman -Sy --noconfirm "$pac_pkg"
      ;;
    apk)
      [[ -n "$apk_pkg" ]] || apk_pkg="$brew_pkg"
      info "Installing $name via apk..."
      $sudo_cmd apk add --no-cache "$apk_pkg"
      ;;
    zypper)
      [[ -n "$zyp_pkg" ]] || zyp_pkg="$dnf_pkg"
      info "Installing $name via zypper..."
      $sudo_cmd zypper --non-interactive install "$zyp_pkg"
      ;;
    *)
      error "No supported package manager found"
      error "Please install $name manually and rerun"
      return 1
      ;;
  esac
}

install_java_sdkman() {
  info "Attempting Java 21 installation via SDKMAN..."
  if [ ! -d "$HOME/.sdkman" ]; then
    curl -s "https://get.sdkman.io" | bash || return 1
  fi
  # shellcheck disable=SC1090
  source "$HOME/.sdkman/bin/sdkman-init.sh" || true
  sdk install java 21.0.5-tem || sdk install java 21-tem || return 1
}

ensure_dep() {
  local cmd="$1" display_name="$2" auto_install="${3:-}"
  
  if command -v "$cmd" >/dev/null 2>&1; then
    success "$display_name found"
    return 0
  fi
  
  warn "$display_name not found"
  
  if [[ "$NONINTERACTIVE" == "1" ]]; then
    error "$display_name is required but not installed"
    exit 1
  fi
  
  if ! prompt_yes_no "Install $display_name now?" default_yes; then
    error "$display_name is required to continue"
    exit 1
  fi
  
  case "$cmd" in
    java)
      if [[ "$PLATFORM" == "windows" ]]; then
        install_pkg_windows "Java JDK 21" "EclipseAdoptium.Temurin.21.JDK" "temurin21" "temurin-lts-jdk"
      else
        install_pkg "Java JDK 21" "openjdk@21" "openjdk-21-jdk" "java-21-openjdk-devel" "jdk21-openjdk" "openjdk21" "java-21-openjdk-devel" || {
          warn "Package manager install failed, trying SDKMAN..."
          install_java_sdkman
        }
      fi
      ;;
    mvn)
      if [[ "$PLATFORM" == "windows" ]]; then
        install_pkg_windows "Maven" "Apache.Maven" "maven" "maven"
      else
        install_pkg "Maven" "maven" "maven" "maven" "maven" "maven" "maven"
      fi
      ;;
    git)
      if [[ "$PLATFORM" == "windows" ]]; then
        install_pkg_windows "Git" "Git.Git" "git" "git"
      else
        install_pkg "Git" "git" "git" "git" "git" "git" "git"
      fi
      ;;
    gradle)
      if [[ "$PLATFORM" == "windows" ]]; then
        install_pkg_windows "Gradle" "Gradle.Gradle" "gradle" "gradle"
      else
        install_pkg "Gradle" "gradle" "gradle" "gradle" "gradle" "gradle" "gradle"
      fi
      ;;
    brew)
      info "Installing Homebrew..."
      /bin/bash -c "$(curl -fsSL https://raw.githubusercontent.com/Homebrew/install/HEAD/install.sh)"
      ;;
    *)
      error "Unknown dependency: $cmd"
      return 1
      ;;
  esac
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
# Graceful Ctrl-C
trap 'echo; warn "Installation aborted by user"; exit 130' INT

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

# Print banner early
print_banner

# -----------------------------
# Interactive wizard
# -----------------------------
if [[ "$NONINTERACTIVE" == "0" ]] && [[ "$QUIET" == "0" ]] && is_tty; then
  
  # Step 1: Installation prefix
  step "${BOLD}[1/5]${RESET} Installation Location ${DIM}━━━━━━━━━━━━━━━━━━━━━━${RESET}"
  local_prefix="$HOME/.local"
  sel=$(prompt_menu "Where should Flylang be installed?" 1 \
    "user:$local_prefix (recommended, no sudo required)" \
    "system:/usr/local (system-wide, may need sudo)" \
    "custom:Specify custom location")
  
  case "$sel" in
    user) PREFIX="$local_prefix";;
    system) PREFIX="/usr/local";;
    custom) 
      printf "\n"
      PREFIX=$(prompt_input "Enter installation prefix" "$PREFIX")
      ;;
  esac
  
  # Step 2: Installation source
  step "${BOLD}[2/5]${RESET} Installation Source ${DIM}━━━━━━━━━━━━━━━━━━━━━━━━${RESET}"
  sel=$(prompt_menu "Where should Flylang be installed from?" 1 \
    "local:Current directory (fastest, for developers)" \
    "github:Clone from GitHub (latest release)" \
    "existing:Use an existing local clone")
  
  case "$sel" in
    local) FROM_SOURCE="1";;
    github)
      FROM_SOURCE="0"
      printf "\n"
      BRANCH=$(prompt_input "Git branch or tag" "$BRANCH")
      ;;
    existing)
      FROM_SOURCE="0"
      printf "\n"
      CLONE_DIR=$(prompt_input "Path to existing clone" "")
      ;;
  esac
  
  # Step 3: Build options
  step "${BOLD}[3/5]${RESET} Build Configuration ${DIM}━━━━━━━━━━━━━━━━━━━━━━━━━${RESET}"
  printf "\n"
  if prompt_yes_no "Build from source?" default_yes; then
    SKIP_BUILD="0"
    printf "  %b[%b✓%b]%b Will compile from source\n" "$DIM" "$GREEN" "$DIM" "$RESET"
  else
    SKIP_BUILD="1"
    printf "  %b[%b!%b]%b Skipping build - will use existing artifacts\n" "$DIM" "$YELLOW" "$DIM" "$RESET"
  fi
  
  # Step 4: Components
  step "${BOLD}[4/5]${RESET} Components Selection ${DIM}━━━━━━━━━━━━━━━━━━━━━━━━${RESET}"
  sel=$(prompt_menu "Which components to install?" 1 \
    "full:Complete installation (CLI, Runtime, Compiler, Stdlib)" \
    "cli:CLI only (minimal, for CI/CD environments)")
  
  case "$sel" in
    full) COMPONENTS="full";;
    cli) COMPONENTS="cli";;
  esac
  
  # Step 5: Confirmation
  step "${BOLD}[5/5]${RESET} Review Configuration ${DIM}━━━━━━━━━━━━━━━━━━━━━━━━━${RESET}"
  printf "\n"
  printf "  %b╭─ Configuration Summary ──────────────────────────────────╮%b\n" "$DIM" "$RESET"
  printf "  %b│%b\n" "$DIM" "$RESET"
  printf "  %b│%b  %b%-20s%b %b%s%b\n" "$DIM" "$RESET" "$CYAN" "Install Location" "$RESET" "$BOLD" "$PREFIX" "$RESET"
  printf "  %b│%b  %b%-20s%b %b%s%b\n" "$DIM" "$RESET" "$CYAN" "Binaries" "$RESET" "$WHITE" "${PREFIX}/bin" "$RESET"
  printf "  %b│%b  %b%-20s%b %b%s%b\n" "$DIM" "$RESET" "$CYAN" "Libraries" "$RESET" "$WHITE" "${PREFIX}/lib/firefly" "$RESET"
  printf "  %b│%b  %b%-20s%b %b%s%b\n" "$DIM" "$RESET" "$CYAN" "Source" "$RESET" "$WHITE" "$([[ "$FROM_SOURCE" == "1" ]] && echo "Local" || echo "GitHub/$BRANCH")" "$RESET"
  printf "  %b│%b  %b%-20s%b %b%s%b\n" "$DIM" "$RESET" "$CYAN" "Build" "$RESET" "$WHITE" "$([[ "$SKIP_BUILD" == "0" ]] && echo "Yes" || echo "No")" "$RESET"
  printf "  %b│%b  %b%-20s%b %b%s%b\n" "$DIM" "$RESET" "$CYAN" "Components" "$RESET" "$WHITE" "$COMPONENTS" "$RESET"
  printf "  %b│%b\n" "$DIM" "$RESET"
  printf "  %b╰──────────────────────────────────────────────────────────╯%b\n\n" "$DIM" "$RESET"
  
  if ! prompt_yes_no "Proceed with installation?" default_yes; then
    printf "\n  %b[%b✗%b]%b Installation cancelled by user\n\n" "$DIM" "$RED" "$DIM" "$RESET"
    exit 0
  fi
  
  printf "\n  %b[%b●%b]%b Starting installation...\n\n" "$DIM" "$GREEN" "$DIM" "$RESET"
fi

INSTALL_BIN="${INSTALL_BIN:-${PREFIX}/bin}"
INSTALL_LIB="${INSTALL_LIB:-${PREFIX}/lib/firefly}"

# -----------------------------
# Pre-flight checks
# -----------------------------
detect_platform
detect_pkg_mgr

# Distro name (for Linux)
if [ -f /etc/os-release ]; then
  . /etc/os-release
  DISTRO_NAME="$PRETTY_NAME"
fi

# On mac, offer to install Homebrew if missing and user agrees
if [ "$PLATFORM" = "mac" ] && ! command -v brew >/dev/null 2>&1; then
  if [ "$NONINTERACTIVE" = "0" ] && is_tty; then
    if prompt_yes_no "Homebrew not found. Install Homebrew now?" default_yes; then
      ensure_dep brew "Homebrew"
      detect_pkg_mgr
    fi
  fi
fi

# Ensure required tools
ensure_dep git "Git"
# Java 21 preferred; if java present, warn if <21
if command -v java >/dev/null 2>&1; then
  JAVA_VER_STR=$(java -version 2>&1 | head -n1)
  JAVA_MAJOR=$(echo "$JAVA_VER_STR" | sed -n 's/.*"\([0-9]*\).*/\1/p')
  if [ -n "$JAVA_MAJOR" ] && [ "$JAVA_MAJOR" -lt 21 ]; then
    warn "Java $JAVA_MAJOR detected; Java 21+ is recommended."
    if [ "$NONINTERACTIVE" = "0" ] && is_tty; then
      if prompt_yes_no "Install Java 21 now?" default_no; then
        ensure_dep java "Java (JDK)"
      fi
    fi
  fi
else
  ensure_dep java "Java (JDK)"
fi
ensure_dep mvn "Maven"
# Gradle optional; offer install if missing
if ! command -v gradle >/dev/null 2>&1; then
  if [ "$NONINTERACTIVE" = "0" ] && is_tty; then
    if prompt_yes_no "Gradle not found. Install Gradle? (optional)" default_no; then
      ensure_dep gradle "Gradle"
    fi
  fi
fi
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

# Install components (optional)
if [ "$COMPONENTS" = "full" ]; then
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
else
  info "Skipping runtime/stdlib/compiler (CLI only)"
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

# Optionally add to PATH
if [ "$NONINTERACTIVE" = "0" ] && is_tty; then
  case ":$PATH:" in
    *:"$INSTALL_BIN":*) :;;
    *)
      if prompt_yes_no "Add $INSTALL_BIN to your PATH in your shell profile?" default_yes; then
        SHELL_NAME=$(basename "${SHELL:-}")
        RC_FILE=""
        case "$SHELL_NAME" in
          zsh) RC_FILE="$HOME/.zshrc";;
          bash) RC_FILE="$HOME/.bashrc";;
          fish) RC_FILE="$HOME/.config/fish/config.fish";;
          *) RC_FILE="$HOME/.profile";;
        esac
        require_writable_dir "$(dirname "$RC_FILE")"
        if [ "$SHELL_NAME" = "fish" ]; then
          echo "set -gx PATH \"$INSTALL_BIN\" \$PATH" >> "$RC_FILE"
        else
          echo "export PATH=\"$INSTALL_BIN:\$PATH\"" >> "$RC_FILE"
        fi
        success "Updated PATH in $RC_FILE"
      fi
      ;;
  esac
fi

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

