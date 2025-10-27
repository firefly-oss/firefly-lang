#!/bin/bash
set -e

FIREFLY_HOME="/Users/ancongui/Development/firefly/firefly-lang"
SOURCE_DIR="$1"
OUTPUT_DIR="$2"

echo "Compiling Firefly sources..."
echo "Source: $SOURCE_DIR"
echo "Output: $OUTPUT_DIR"

# Create output directory
mkdir -p "$OUTPUT_DIR"

# Compile each .fly file
for file in "$SOURCE_DIR"/*.fly; do
    if [ -f "$file" ]; then
        echo "  Compiling $(basename $file)..."
        "$FIREFLY_HOME/firefly" "$file"
        
        # Move generated .class files to output directory
        # Find and move .class files from current directory
        find . -maxdepth 3 -name "*.class" -type f -exec sh -c '
            for classfile; do
                # Extract package path from class file
                dir=$(dirname "$classfile")
                mkdir -p "'"$OUTPUT_DIR"'/$dir"
                mv "$classfile" "'"$OUTPUT_DIR"'/$dir/"
            done
        ' sh {} +
    fi
done

echo "✓ Compilation complete"
