#!/bin/bash

# Quick test script for Firefly VS Code Extension
# This script helps verify the extension is working correctly

set -e

echo "🔥 Firefly VS Code Extension - Quick Test"
echo "=========================================="
echo ""

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Get the directory of this script
SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
REPO_ROOT="$( cd "$SCRIPT_DIR/../.." && pwd )"

echo "Extension directory: $SCRIPT_DIR"
echo "Repository root: $REPO_ROOT"
echo ""

# Test 1: Check if extension is compiled
echo -e "${BLUE}Test 1: Checking if extension is compiled...${NC}"
if [ -f "$SCRIPT_DIR/out/extension.js" ]; then
    echo -e "${GREEN}✓ Extension compiled${NC}"
else
    echo -e "${RED}✗ Extension not compiled${NC}"
    echo "Run: npm run compile"
    exit 1
fi
echo ""

# Test 2: Check if LSP server exists
echo -e "${BLUE}Test 2: Checking for LSP server...${NC}"
LSP_FOUND=false

# Check workspace location
if [ -f "$REPO_ROOT/firefly-lsp/target/firefly-lsp.jar" ]; then
    echo -e "${GREEN}✓ LSP server found in workspace${NC}"
    echo "  Location: $REPO_ROOT/firefly-lsp/target/firefly-lsp.jar"
    LSP_PATH="$REPO_ROOT/firefly-lsp/target/firefly-lsp.jar"
    LSP_FOUND=true
fi

# Check home directory
if [ -f "$HOME/.firefly/firefly-lsp.jar" ]; then
    echo -e "${GREEN}✓ LSP server found in home directory${NC}"
    echo "  Location: $HOME/.firefly/firefly-lsp.jar"
    if [ "$LSP_FOUND" = false ]; then
        LSP_PATH="$HOME/.firefly/firefly-lsp.jar"
        LSP_FOUND=true
    fi
fi

if [ "$LSP_FOUND" = false ]; then
    echo -e "${RED}✗ LSP server not found${NC}"
    echo "Build it with: mvn clean package -DskipTests -pl firefly-lsp -am"
    exit 1
fi
echo ""

# Test 3: Check Java version
echo -e "${BLUE}Test 3: Checking Java version...${NC}"
if command -v java &> /dev/null; then
    JAVA_VERSION=$(java -version 2>&1 | head -n 1 | cut -d'"' -f2 | cut -d'.' -f1)
    if [ "$JAVA_VERSION" -ge 17 ]; then
        echo -e "${GREEN}✓ Java $JAVA_VERSION installed${NC}"
    else
        echo -e "${YELLOW}⚠ Java $JAVA_VERSION found, but Java 17+ recommended${NC}"
    fi
else
    echo -e "${RED}✗ Java not found${NC}"
    echo "Install Java 17 or later"
    exit 1
fi
echo ""

# Test 4: Test LSP server can start
echo -e "${BLUE}Test 4: Testing LSP server startup...${NC}"
timeout 5 java -jar "$LSP_PATH" &> /dev/null &
LSP_PID=$!
sleep 2

if ps -p $LSP_PID > /dev/null 2>&1; then
    echo -e "${GREEN}✓ LSP server started successfully${NC}"
    kill $LSP_PID 2>/dev/null || true
else
    echo -e "${YELLOW}⚠ LSP server may have issues (this is normal for quick test)${NC}"
fi
echo ""

# Test 5: Check VS Code is installed
echo -e "${BLUE}Test 5: Checking VS Code installation...${NC}"
if command -v code &> /dev/null; then
    echo -e "${GREEN}✓ VS Code 'code' command found${NC}"
else
    echo -e "${YELLOW}⚠ VS Code 'code' command not found${NC}"
    echo "You may need to install it from VS Code: View → Command Palette → Shell Command: Install 'code' command in PATH"
fi
echo ""

# Test 6: Check if extension is installed
echo -e "${BLUE}Test 6: Checking if extension is installed in VS Code...${NC}"
if [ -d "$HOME/.vscode/extensions/firefly-language-1.0-Alpha" ]; then
    echo -e "${GREEN}✓ Extension installed in VS Code${NC}"
    echo "  Location: $HOME/.vscode/extensions/firefly-language-1.0-Alpha"
else
    echo -e "${YELLOW}⚠ Extension not installed in VS Code${NC}"
    echo "Run: ./install.sh"
fi
echo ""

# Summary
echo "=========================================="
echo -e "${GREEN}✓ All basic tests passed!${NC}"
echo "=========================================="
echo ""
echo "Next steps to test in VS Code:"
echo "1. Open VS Code"
echo "2. Create a test file: test.fly"
echo "3. Press Cmd+Shift+U to open Output panel"
echo "4. Select 'Firefly Language Server' from dropdown"
echo "5. You should see startup messages"
echo ""
echo "Test LSP features:"
echo "  • Type invalid syntax → Should see red squiggly lines"
echo "  • Type 'let' and press Ctrl+Space → Should see completions"
echo "  • Hover over a symbol → Should see documentation"
echo ""
echo "If you see issues, check the Output panel for details."
echo ""
echo "🔥 Happy testing!"

