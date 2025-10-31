#!/usr/bin/env bash
#
# Flylang Unified Test Runner
# 
# This script unifies all testing functionality for Flylang:
# - Maven unit tests (compiler, runtime, plugins)
# - Example projects (smoke tests)
# - Interactive mode for selective testing
# - CI/CD batch mode
#
# Usage:
#   ./scripts/test.sh                 # Interactive mode
#   ./scripts/test.sh --all           # Run everything
#   ./scripts/test.sh --unit          # Run Maven tests only
#   ./scripts/test.sh --examples      # Run examples only
#   ./scripts/test.sh --quick         # Quick smoke test (subset)
#   ./scripts/test.sh --ci            # CI mode (no interaction, verbose)
#

set -euo pipefail

# ==================== Configuration ====================

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"

# Detect timeout command (gtimeout on macOS via coreutils, timeout on Linux)
if command -v gtimeout &> /dev/null; then
  TIMEOUT_CMD="gtimeout"
elif command -v timeout &> /dev/null; then
  TIMEOUT_CMD="timeout"
else
  TIMEOUT_CMD=""  # No timeout available
fi

# All available examples
ALL_EXAMPLES=(
  hello-world
  async-demo
  concurrency-demo
  futures-combinators-demo
  patterns-demo
  data-patterns-demo
  java-interop-advanced
  sparks-demo
  async-pipeline-demo
  spring-boot-demo
  enum-demo
  task-manager-cli
  ranges-demo
  exceptions-demo
  std-result-demo
  std-option-demo
  std-validation-demo
)

# Quick smoke test subset (fast examples)
QUICK_EXAMPLES=(
  hello-world
  async-demo
  patterns-demo
  sparks-demo
  ranges-demo
  exceptions-demo
  std-result-demo
  std-option-demo
)

# Maven modules with tests
MAVEN_MODULES=(
  firefly-compiler
  firefly-runtime
  firefly-maven-plugin
)

# ==================== Colors & Formatting ====================

if [[ -t 1 ]]; then
  # Terminal supports colors
  BOLD=$'\033[1m'
  DIM=$'\033[2m'
  RED=$'\033[0;31m'
  GREEN=$'\033[0;32m'
  YELLOW=$'\033[1;33m'
  BLUE=$'\033[0;34m'
  CYAN=$'\033[0;36m'
  BRIGHT_RED=$'\033[91m'
  BRIGHT_GREEN=$'\033[92m'
  BRIGHT_YELLOW=$'\033[93m'
  BRIGHT_BLUE=$'\033[94m'
  BRIGHT_CYAN=$'\033[96m'
  NC=$'\033[0m'
else
  # No color support
  BOLD=''
  DIM=''
  RED=''
  GREEN=''
  YELLOW=''
  BLUE=''
  CYAN=''
  BRIGHT_RED=''
  BRIGHT_GREEN=''
  BRIGHT_YELLOW=''
  BRIGHT_BLUE=''
  BRIGHT_CYAN=''
  NC=''
fi

# ==================== UI Functions ====================

print_banner() {
  echo -e "${BRIGHT_CYAN}"
  echo "┌────────────────────────────────────────────────────────────────┐"
  echo "│                   Flylang Test Suite Runner                    │"
  echo "│                   |----------->(v1.0-Alpha)                    │"
  echo "└────────────────────────────────────────────────────────────────┘"
  echo -e "${NC}"
}

print_section() {
  echo ""
  echo -e "${BOLD}${BRIGHT_BLUE}═══════════════════════════════════════════════════════════${NC}"
  echo -e "${BOLD}${BRIGHT_BLUE}  $1${NC}"
  echo -e "${BOLD}${BRIGHT_BLUE}═══════════════════════════════════════════════════════════${NC}"
  echo ""
}

print_info() {
  echo -e "${CYAN}ℹ${NC}  $1"
}

print_success() {
  echo -e "${BRIGHT_GREEN}✓${NC} $1"
}

print_error() {
  echo -e "${BRIGHT_RED}✗${NC} $1"
}

print_warning() {
  echo -e "${YELLOW}⚠${NC}  $1"
}

print_running() {
  echo -e "${DIM}▸${NC} $1"
}

# ==================== Test Functions ====================

run_maven_tests() {
  local module=$1
  local verbose=${2:-false}
  
  print_running "Testing: ${BOLD}$module${NC}"
  
  local mvn_flags="--no-transfer-progress -q"
  if [[ "$verbose" == "true" ]]; then
    mvn_flags=""
  fi
  
  if mvn $mvn_flags -f "$ROOT_DIR/$module/pom.xml" test; then
    print_success "$module tests passed"
    return 0
  else
    print_error "$module tests failed"
    return 1
  fi
}

run_example() {
  local example=$1
  local verbose=${2:-false}
  
  print_running "Example: ${BOLD}$example${NC}"
  
  local example_dir="$ROOT_DIR/examples/$example"
  if [[ ! -d "$example_dir" ]]; then
    print_warning "Example not found: $example"
    return 1
  fi
  
  local mvn_flags="--no-transfer-progress -q"
  if [[ "$verbose" == "true" ]]; then
    mvn_flags=""
  fi
  
  # Build
  if ! mvn $mvn_flags -f "$example_dir/pom.xml" -DskipTests clean package > /dev/null 2>&1; then
    print_error "$example build failed"
    return 1
  fi
  
  # Run
  if [[ "$example" == "spring-boot-demo" ]]; then
    # Use the example's endpoint test script to start, probe endpoints, and stop cleanly
    local test_script="$example_dir/test-endpoints.sh"
    if [[ ! -x "$test_script" ]]; then
      print_error "spring-boot-demo test script not found or not executable"
      return 1
    fi
    local sb_cmd="bash \"$test_script\""
    if [[ -n "$TIMEOUT_CMD" ]]; then
      sb_cmd="$TIMEOUT_CMD 60s $sb_cmd"
    fi
    if eval "$sb_cmd > /dev/null 2>&1"; then
      print_success "$example endpoints verified"
      return 0
    else
      print_error "$example endpoints test failed or timed out"
      return 1
    fi
  elif [[ "$example" == "exceptions-demo" ]]; then
    # Run via Fly CLI to ensure proper main wrapper
    local run_cmd="fly run \"$example_dir\""
    if [[ -n "$TIMEOUT_CMD" ]]; then
      run_cmd="$TIMEOUT_CMD 10s $run_cmd"
    fi
    if eval "$run_cmd > /dev/null 2>&1"; then
      print_success "$example completed"
      return 0
    else
      print_error "$example execution failed or timed out"
      return 1
    fi
  else
    # Default run via exec:java (with timeout for safety if available)
    local run_cmd="mvn $mvn_flags -f \"$example_dir/pom.xml\" exec:java"
    if [[ -n "$TIMEOUT_CMD" ]]; then
      run_cmd="$TIMEOUT_CMD 10s $run_cmd"
    fi
    if eval "$run_cmd > /dev/null 2>&1"; then
      print_success "$example completed"
      return 0
    else
      print_error "$example execution failed or timed out"
      return 1
    fi
  fi
}

run_all_maven_tests() {
  local verbose=${1:-false}
  print_section "Maven Unit Tests"
  
  local passed=0
  local failed=0
  
  for module in "${MAVEN_MODULES[@]}"; do
    if run_maven_tests "$module" "$verbose"; then
      ((passed++))
    else
      ((failed++))
    fi
  done
  
  echo ""
  echo -e "Results: ${BRIGHT_GREEN}$passed passed${NC}, ${BRIGHT_RED}$failed failed${NC}"
  
  return $failed
}

run_examples() {
  local examples=("$@")
  local last_idx=$((${#examples[@]} - 1))
  local verbose="${examples[$last_idx]}"
  unset 'examples[$last_idx]'
  
  print_section "Example Projects"
  
  local passed=0
  local failed=0
  local total=${#examples[@]}
  
  print_info "Running $total example(s)..."
  echo ""
  
  for example in "${examples[@]}"; do
    if run_example "$example" "$verbose"; then
      ((passed++))
    else
      ((failed++))
    fi
  done
  
  echo ""
  echo -e "Results: ${BRIGHT_GREEN}$passed passed${NC}, ${BRIGHT_RED}$failed failed${NC} (of $total)"
  
  return $failed
}

# ==================== Interactive Menu ====================

show_menu() {
  clear
  print_banner
  
  echo -e "${BOLD}Select test suite to run:${NC}"
  echo ""
  echo "  ${BRIGHT_CYAN}1)${NC} Run all tests (Maven + Examples)"
  echo "  ${BRIGHT_CYAN}2)${NC} Run Maven unit tests only"
  echo "  ${BRIGHT_CYAN}3)${NC} Run all examples (smoke tests)"
  echo "  ${BRIGHT_CYAN}4)${NC} Run quick examples (fast subset)"
  echo "  ${BRIGHT_CYAN}5)${NC} Select specific examples"
  echo "  ${BRIGHT_CYAN}6)${NC} Run individual example"
  echo ""
  echo "  ${DIM}0) Exit${NC}"
  echo ""
  echo -n "Enter choice [0-6]: "
}

interactive_mode() {
  while true; do
    show_menu
    read -r choice
    
    case $choice in
      1)
        echo ""
        run_all_maven_tests false
        maven_result=$?
        run_examples "${ALL_EXAMPLES[@]}" false
        examples_result=$?
        
        echo ""
        if [[ $maven_result -eq 0 && $examples_result -eq 0 ]]; then
          echo -e "${BRIGHT_GREEN}✨ All tests passed!${NC}"
        else
          echo -e "${BRIGHT_RED}❌ Some tests failed${NC}"
        fi
        
        echo ""
        read -p "Press Enter to continue..."
        ;;
      
      2)
        echo ""
        run_all_maven_tests false
        echo ""
        read -p "Press Enter to continue..."
        ;;
      
      3)
        echo ""
        run_examples "${ALL_EXAMPLES[@]}" false
        echo ""
        read -p "Press Enter to continue..."
        ;;
      
      4)
        echo ""
        run_examples "${QUICK_EXAMPLES[@]}" false
        echo ""
        read -p "Press Enter to continue..."
        ;;
      
      5)
        select_specific_examples
        ;;
      
      6)
        run_single_example
        ;;
      
      0)
        echo ""
        echo -e "${CYAN}Goodbye!${NC}"
        exit 0
        ;;
      
      *)
        echo ""
        print_error "Invalid choice. Please try again."
        sleep 2
        ;;
    esac
  done
}

select_specific_examples() {
  clear
  print_banner
  echo -e "${BOLD}Select examples to run (space-separated numbers):${NC}"
  echo ""
  
  for i in "${!ALL_EXAMPLES[@]}"; do
    echo "  ${BRIGHT_CYAN}$((i+1)))${NC} ${ALL_EXAMPLES[$i]}"
  done
  
  echo ""
  echo -n "Enter choices (e.g., 1 3 5) or 'all': "
  read -r choices
  
  if [[ "$choices" == "all" ]]; then
    run_examples "${ALL_EXAMPLES[@]}" false
  else
    local selected=()
    for num in $choices; do
      local idx=$((num - 1))
      if [[ $idx -ge 0 && $idx -lt ${#ALL_EXAMPLES[@]} ]]; then
        selected+=("${ALL_EXAMPLES[$idx]}")
      fi
    done
    
    if [[ ${#selected[@]} -gt 0 ]]; then
      run_examples "${selected[@]}" false
    else
      print_warning "No valid examples selected"
    fi
  fi
  
  echo ""
  read -p "Press Enter to continue..."
}

run_single_example() {
  clear
  print_banner
  echo -e "${BOLD}Available examples:${NC}"
  echo ""
  
  for i in "${!ALL_EXAMPLES[@]}"; do
    echo "  ${BRIGHT_CYAN}$((i+1)))${NC} ${ALL_EXAMPLES[$i]}"
  done
  
  echo ""
  echo -n "Enter example number: "
  read -r num
  
  local idx=$((num - 1))
  if [[ $idx -ge 0 && $idx -lt ${#ALL_EXAMPLES[@]} ]]; then
    echo ""
    run_example "${ALL_EXAMPLES[$idx]}" true
  else
    print_error "Invalid example number"
  fi
  
  echo ""
  read -p "Press Enter to continue..."
}

# ==================== Main ====================

main() {
  cd "$ROOT_DIR"
  
  # Parse arguments
  if [[ $# -eq 0 ]]; then
    # Interactive mode
    interactive_mode
  else
    case "$1" in
      --all)
        print_banner
        run_all_maven_tests false
        maven_result=$?
        run_examples "${ALL_EXAMPLES[@]}" false
        examples_result=$?
        
        echo ""
        print_section "Final Results"
        if [[ $maven_result -eq 0 && $examples_result -eq 0 ]]; then
          echo -e "${BRIGHT_GREEN}✨ All tests passed!${NC}"
          exit 0
        else
          echo -e "${BRIGHT_RED}❌ Some tests failed${NC}"
          exit 1
        fi
        ;;
      
      --unit)
        print_banner
        run_all_maven_tests false
        exit $?
        ;;
      
      --examples)
        print_banner
        run_examples "${ALL_EXAMPLES[@]}" false
        exit $?
        ;;
      
      --quick)
        print_banner
        run_examples "${QUICK_EXAMPLES[@]}" false
        exit $?
        ;;
      
      --ci)
        print_banner
        print_info "Running in CI mode (verbose)"
        echo ""
        run_all_maven_tests true
        maven_result=$?
        run_examples "${ALL_EXAMPLES[@]}" true
        examples_result=$?
        
        echo ""
        print_section "CI Results"
        if [[ $maven_result -eq 0 && $examples_result -eq 0 ]]; then
          echo -e "${BRIGHT_GREEN}✨ All tests passed!${NC}"
          exit 0
        else
          echo -e "${BRIGHT_RED}❌ Tests failed${NC}"
          exit 1
        fi
        ;;
      
      --help|-h)
        print_banner
        echo "Usage: $0 [OPTIONS]"
        echo ""
        echo "Options:"
        echo "  (none)       Interactive mode with menu"
        echo "  --all        Run all tests (Maven + examples)"
        echo "  --unit       Run Maven unit tests only"
        echo "  --examples   Run all example projects"
        echo "  --quick      Run quick smoke test (subset of examples)"
        echo "  --ci         CI mode (verbose, no interaction)"
        echo "  --help       Show this help message"
        echo ""
        exit 0
        ;;
      
      *)
        echo -e "${BRIGHT_RED}Error:${NC} Unknown option: $1"
        echo "Use --help for usage information"
        exit 1
        ;;
    esac
  fi
}

main "$@"
