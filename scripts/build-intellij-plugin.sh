#!/usr/bin/env bash

# Firefly IntelliJ IDEA Plugin Builder
# This script builds the Firefly IntelliJ IDEA plugin

set -e

echo "🔥 Firefly IntelliJ IDEA Plugin Builder"
echo "========================================"
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
cd "$PROJECT_ROOT/ide-plugins/intellij-firefly"

# Check if Gradle is installed
if ! command -v gradle &> /dev/null; then
    echo -e "${RED}✗${NC} Gradle not found. Please install Gradle first."
    echo ""
    echo "Install Gradle:"
    echo "  macOS:   brew install gradle"
    echo "  Linux:   sudo apt install gradle"
    echo "  Windows: choco install gradle"
    exit 1
fi

echo -e "${BLUE}[1/2]${NC} Building IntelliJ IDEA plugin..."

if gradle buildPlugin; then
    echo -e "${GREEN}✓${NC} Plugin built successfully"
else
    echo -e "${RED}✗${NC} Failed to build plugin"
    exit 1
fi

echo ""
echo -e "${BLUE}[2/2]${NC} Locating plugin artifact..."

PLUGIN_ZIP=$(find build/distributions -name "*.zip" | head -n 1)

if [ -z "$PLUGIN_ZIP" ]; then
    echo -e "${RED}✗${NC} Plugin ZIP not found"
    exit 1
fi

echo -e "${GREEN}✓${NC} Plugin artifact: $PLUGIN_ZIP"

echo ""
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo -e "${GREEN}✅ Build Complete!${NC}"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo ""
echo -e "${BLUE}📦 Plugin Built Successfully:${NC}"
echo "  Location: $PLUGIN_ZIP"
echo "  Version: 1.0-Alpha"
echo "  Compatible with: IntelliJ IDEA 2023.2+"
echo ""
echo -e "${BLUE}🚀 Installation Steps:${NC}"
echo ""
echo "  ${YELLOW}Step 1:${NC} Open IntelliJ IDEA"
echo ""
echo "  ${YELLOW}Step 2:${NC} Go to Settings/Preferences"
echo "    • macOS: IntelliJ IDEA → Preferences (Cmd+,)"
echo "    • Windows/Linux: File → Settings (Ctrl+Alt+S)"
echo ""
echo "  ${YELLOW}Step 3:${NC} Navigate to Plugins"
echo "    • Click 'Plugins' in the left sidebar"
echo ""
echo "  ${YELLOW}Step 4:${NC} Install from Disk"
echo "    • Click the gear icon (⚙️) at the top"
echo "    • Select 'Install Plugin from Disk...'"
echo "    • Navigate to: $PLUGIN_ZIP"
echo "    • Click 'OK'"
echo ""
echo "  ${YELLOW}Step 5:${NC} Restart IntelliJ IDEA"
echo "    • Click 'Restart IDE' when prompted"
echo ""
echo -e "${BLUE}✅ Verification:${NC}"
echo "  After restart:"
echo "    1. Create a new .fly file"
echo "    2. Verify syntax highlighting works"
echo "    3. Try creating files: Right-click → New → Firefly File"
echo ""
echo -e "${BLUE}📚 Documentation:${NC}"
echo "  • Installation Guide: $PROJECT_ROOT/ide-plugins/intellij-firefly/INSTALL.md"
echo "  • Plugin README: $PROJECT_ROOT/ide-plugins/intellij-firefly/README.md"
echo "  • Language Reference: $PROJECT_ROOT/docs/language-reference.md"
echo ""
echo "🔥 Enjoy Firefly in IntelliJ IDEA!"

