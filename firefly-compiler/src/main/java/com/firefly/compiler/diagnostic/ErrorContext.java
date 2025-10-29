package com.firefly.compiler.diagnostic;

/**
 * Enhanced error context with detailed source information.
 * Provides line, column, and source snippet for better error reporting.
 */
public class ErrorContext {
    
    private final String sourceFile;
    private final int line;
    private final int column;
    private final String sourceCode;
    private final String[] sourceLines;
    private final int contextLines;
    
    public ErrorContext(String sourceFile, int line, int column, 
                       String sourceCode) {
        this(sourceFile, line, column, sourceCode, 2);
    }
    
    public ErrorContext(String sourceFile, int line, int column, 
                       String sourceCode, int contextLines) {
        this.sourceFile = sourceFile;
        this.line = Math.max(1, line);
        this.column = Math.max(1, column);
        this.sourceCode = sourceCode;
        this.contextLines = contextLines;
        this.sourceLines = sourceCode != null ? sourceCode.split("\n") : new String[0];
    }
    
    public String getSourceFile() {
        return sourceFile;
    }
    
    public int getLine() {
        return line;
    }
    
    public int getColumn() {
        return column;
    }
    
    /**
     * Get detailed error message with source context
     */
    public String getDetailedMessage(String message) {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("%s:%d:%d: %s\n", sourceFile, line, column, message));
        
        // Add source context
        if (sourceLines.length > 0) {
            int startLine = Math.max(0, line - contextLines - 1);
            int endLine = Math.min(sourceLines.length, line + contextLines);
            
            for (int i = startLine; i < endLine; i++) {
                if (i < sourceLines.length) {
                    String codeLine = sourceLines[i];
                    int lineNum = i + 1;
                    
                    // Highlight the error line
                    if (lineNum == line) {
                        sb.append(String.format(" --> %4d | %s\n", lineNum, codeLine));
                        sb.append(String.format("         | "));
                        // Add caret pointing to column
                        for (int j = 0; j < column - 1; j++) {
                            sb.append(codeLine.charAt(j) == '\t' ? '\t' : ' ');
                        }
                        sb.append("^\n");
                    } else {
                        sb.append(String.format("      %d | %s\n", lineNum, codeLine));
                    }
                }
            }
        }
        
        return sb.toString();
    }
    
    /**
     * Get source snippet
     */
    public String getSourceSnippet() {
        if (line < 1 || line > sourceLines.length) {
            return "";
        }
        return sourceLines[line - 1];
    }
    
    @Override
    public String toString() {
        return String.format("%s:%d:%d", sourceFile, line, column);
    }
}
