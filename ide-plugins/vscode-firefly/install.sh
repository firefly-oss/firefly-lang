#!/bin/bash

# Firefly VS Code Extension Installation Script
# This script installs the Firefly extension for VS Code

set -e

echo "🔥 Firefly VS Code Extension Installer"
echo "======================================"
echo ""

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Check if VS Code is installed
if ! command -v code &> /dev/null; then
    echo -e "${RED}Error: VS Code 'code' command not found.${NC}"
    echo "Please install VS Code and ensure 'code' is in your PATH."
    echo "See: https://code.visualstudio.com/docs/setup/mac#_launching-from-the-command-line"
    exit 1
fi

# Get the directory of this script
SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
REPO_ROOT="$( cd "$SCRIPT_DIR/../.." && pwd )"

echo "Extension directory: $SCRIPT_DIR"
echo "Repository root: $REPO_ROOT"
echo ""

# Step 1: Build the LSP server
echo "Step 1: Building Firefly LSP Server..."
echo "---------------------------------------"
cd "$REPO_ROOT"

if [ ! -f "firefly-lsp/target/firefly-lsp.jar" ]; then
    echo -e "${YELLOW}LSP server not found. Building...${NC}"
    mvn clean package -DskipTests -pl firefly-lsp -am
    if [ $? -ne 0 ]; then
        echo -e "${RED}Failed to build LSP server${NC}"
        exit 1
    fi
else
    echo -e "${GREEN}✓ LSP server already built${NC}"
fi

# Step 2: Install npm dependencies
echo ""
echo "Step 2: Installing npm dependencies..."
echo "---------------------------------------"
cd "$SCRIPT_DIR"
npm install
if [ $? -ne 0 ]; then
    echo -e "${RED}Failed to install npm dependencies${NC}"
    exit 1
fi
echo -e "${GREEN}✓ Dependencies installed${NC}"

# Step 3: Compile TypeScript
echo ""
echo "Step 3: Compiling TypeScript..."
echo "--------------------------------"
npm run compile
if [ $? -ne 0 ]; then
    echo -e "${RED}Failed to compile TypeScript${NC}"
    exit 1
fi
echo -e "${GREEN}✓ TypeScript compiled${NC}"

# Step 4: Install extension
echo ""
echo "Step 4: Installing VS Code extension..."
echo "----------------------------------------"

# Get VS Code extensions directory
if [[ "$OSTYPE" == "darwin"* ]]; then
    VSCODE_EXT_DIR="$HOME/.vscode/extensions"
elif [[ "$OSTYPE" == "linux-gnu"* ]]; then
    VSCODE_EXT_DIR="$HOME/.vscode/extensions"
elif [[ "$OSTYPE" == "msys" || "$OSTYPE" == "win32" ]]; then
    VSCODE_EXT_DIR="$USERPROFILE/.vscode/extensions"
else
    VSCODE_EXT_DIR="$HOME/.vscode/extensions"
fi

# Create extensions directory if it doesn't exist
mkdir -p "$VSCODE_EXT_DIR"

# Extension target directory
EXT_TARGET="$VSCODE_EXT_DIR/firefly-language-1.0-Alpha"

# Remove old version if exists
if [ -d "$EXT_TARGET" ]; then
    echo -e "${YELLOW}Removing old version...${NC}"
    rm -rf "$EXT_TARGET"
fi

# Copy extension
echo "Copying extension to $EXT_TARGET"
cp -r "$SCRIPT_DIR" "$EXT_TARGET"

# Create .firefly directory in home for LSP server
FIREFLY_HOME="$HOME/.firefly"
mkdir -p "$FIREFLY_HOME"

# Copy LSP server
echo "Copying LSP server to $FIREFLY_HOME"
cp "$REPO_ROOT/firefly-lsp/target/firefly-lsp.jar" "$FIREFLY_HOME/firefly-lsp.jar"

echo -e "${GREEN}✓ Extension installed${NC}"

# Step 5: Success message
echo ""
echo "======================================"
echo -e "${GREEN}✓ Installation complete!${NC}"
echo "======================================"
echo ""
echo "Next steps:"
echo "1. Restart VS Code"
echo "2. Open a .fly file"
echo "3. The Firefly Language Server should start automatically"
echo ""
echo "To verify installation:"
echo "  - Open VS Code"
echo "  - Press Cmd+Shift+P (or Ctrl+Shift+P)"
echo "  - Type 'Extensions: Show Installed Extensions'"
echo "  - Look for 'Firefly Language Support'"
echo ""
echo "LSP Server location: $FIREFLY_HOME/firefly-lsp.jar"
echo ""
echo "To enable LSP trace (for debugging):"
echo "  - Open VS Code settings"
echo "  - Search for 'firefly.trace.server'"
echo "  - Set to 'verbose'"
echo ""
echo "🔥 Happy coding with Firefly!"

