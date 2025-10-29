#!/usr/bin/env bash

# Firefly VS Code Extension Installer
# This script builds and installs the Firefly VS Code extension

set -e

echo "🔥 Firefly VS Code Extension Installer"
echo "======================================="
echo ""

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Get script directory and project root
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
cd "$PROJECT_ROOT"

# Step 1: Build the Language Server
echo -e "${BLUE}[1/4]${NC} Building Firefly Language Server..."
if mvn clean package -DskipTests -pl firefly-lsp -am -q; then
    echo -e "${GREEN}✓${NC} Language Server built successfully"
else
    echo -e "${RED}✗${NC} Failed to build Language Server"
    exit 1
fi

# Check if LSP JAR exists
if [ ! -f "firefly-lsp/target/firefly-lsp.jar" ]; then
    echo -e "${RED}✗${NC} Language Server JAR not found"
    exit 1
fi

echo ""

# Step 2: Install Node.js dependencies
echo -e "${BLUE}[2/4]${NC} Installing Node.js dependencies..."
cd ide-plugins/vscode-firefly

if command -v npm &> /dev/null; then
    if npm install --silent; then
        echo -e "${GREEN}✓${NC} Dependencies installed"
    else
        echo -e "${RED}✗${NC} Failed to install dependencies"
        exit 1
    fi
else
    echo -e "${RED}✗${NC} npm not found. Please install Node.js first."
    echo -e "${YELLOW}ℹ${NC}  Install from: https://nodejs.org/"
    echo -e "${YELLOW}ℹ${NC}  Or using Homebrew: brew install node"
    exit 1
fi

echo ""

# Step 3: Compile TypeScript
echo -e "${BLUE}[3/4]${NC} Compiling TypeScript..."
if npm run compile --silent; then
    echo -e "${GREEN}✓${NC} TypeScript compiled"
else
    echo -e "${RED}✗${NC} Failed to compile TypeScript"
    exit 1
fi

echo ""

# Step 4: Install extension
echo -e "${BLUE}[4/4]${NC} Installing VS Code extension..."

# Determine VS Code extensions directory
if [[ "$OSTYPE" == "darwin"* ]]; then
    # macOS
    VSCODE_EXT_DIR="$HOME/.vscode/extensions"
elif [[ "$OSTYPE" == "linux-gnu"* ]]; then
    # Linux
    VSCODE_EXT_DIR="$HOME/.vscode/extensions"
elif [[ "$OSTYPE" == "msys" || "$OSTYPE" == "win32" ]]; then
    # Windows
    VSCODE_EXT_DIR="$USERPROFILE/.vscode/extensions"
else
    echo -e "${RED}✗${NC} Unsupported operating system: $OSTYPE"
    exit 1
fi

# Create extensions directory if it doesn't exist
mkdir -p "$VSCODE_EXT_DIR"

# Extension directory name
EXT_NAME="firefly-language-1.0-Alpha"
EXT_DIR="$VSCODE_EXT_DIR/$EXT_NAME"

# Remove old version if exists
if [ -d "$EXT_DIR" ]; then
    echo -e "${YELLOW}⚠${NC}  Removing old version..."
    rm -rf "$EXT_DIR"
fi

# Copy extension
echo -e "   Copying extension to $EXT_DIR..."
cp -r . "$EXT_DIR"

# Remove node_modules from installed extension (will be reinstalled by VS Code)
rm -rf "$EXT_DIR/node_modules"

echo -e "${GREEN}✓${NC} Extension installed"

echo ""
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo -e "${GREEN}✅ Installation Complete!${NC}"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo ""
echo -e "${BLUE}📦 What was installed:${NC}"
echo "  ✓ Firefly Language Support extension"
echo "  ✓ Syntax highlighting for .fly files"
echo "  ✓ 25+ code snippets"
echo "  ✓ Language Server Protocol integration"
echo "  ✓ Auto-completion and bracket matching"
echo ""
echo -e "${BLUE}📍 Installation Details:${NC}"
echo "  Extension: $EXT_DIR"
echo "  LSP Server: $PROJECT_ROOT/firefly-lsp/target/firefly-lsp.jar"
echo ""
echo -e "${BLUE}🚀 Next Steps:${NC}"
echo "  1. Reload VS Code:"
echo "     • Press Cmd+Shift+P (macOS) or Ctrl+Shift+P (Windows/Linux)"
echo "     • Type 'Reload Window' and press Enter"
echo ""
echo "  2. Create a test file:"
echo "     • Create a new file with .fly extension"
echo "     • Start typing Firefly code"
echo "     • Try snippets: type 'fn' and press Tab"
echo ""
echo "  3. Configure (optional):"
echo "     • Open VS Code settings (Cmd+, or Ctrl+,)"
echo "     • Add:"
echo ""
echo "       {"
echo "         \"firefly.languageServer.path\": \"$PROJECT_ROOT/firefly-lsp/target/firefly-lsp.jar\","
echo "         \"firefly.trace.server\": \"off\""
echo "       }"
echo ""
echo -e "${BLUE}📚 Documentation:${NC}"
echo "  • Installation Guide: $PROJECT_ROOT/ide-plugins/vscode-firefly/INSTALL.md"
echo "  • Language Reference: $PROJECT_ROOT/docs/language-reference.md"
echo "  • Examples: $PROJECT_ROOT/examples/"
echo ""
echo "🔥 Happy coding with Firefly!"

