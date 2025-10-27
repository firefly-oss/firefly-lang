package com.firefly.compiler.ast;

/**
 * Represents a location in source code for error reporting.
 */
public record SourceLocation(
    String fileName,
    int line,
    int column
) {
    @Override
    public String toString() {
        return String.format("%s:%d:%d", fileName, line, column);
    }
    
    public static SourceLocation unknown() {
        return new SourceLocation("<unknown>", 0, 0);
    }
}
