package com.firefly.compiler.diagnostics;

import com.firefly.compiler.ast.SourceLocation;

/**
 * Professional compiler diagnostic system.
 * Reports errors, warnings, and information with precise location and phase information.
 */
public class CompilerDiagnostic {
    
    private final Level level;
    private final Phase phase;
    private final String message;
    private final SourceLocation location;
    private final String hint;
    
    public enum Level {
        ERROR("✗", "\u001B[31m"),      // Red
        WARNING("⚠", "\u001B[33m"),    // Yellow
        INFO("ℹ", "\u001B[34m");       // Blue
        
        private final String symbol;
        private final String color;
        
        Level(String symbol, String color) {
            this.symbol = symbol;
            this.color = color;
        }
        
        public String getSymbol() { return symbol; }
        public String getColor() { return color; }
    }
    
    public enum Phase {
        LEXING("Lexical Analysis"),
        PARSING("Syntax Analysis"),
        SEMANTIC("Semantic Analysis"),
        TYPE_CHECKING("Type Checking"),
        CODE_GENERATION("Code Generation"),
        OPTIMIZATION("Optimization");
        
        private final String displayName;
        
        Phase(String displayName) {
            this.displayName = displayName;
        }
        
        public String getDisplayName() { return displayName; }
    }
    
    private CompilerDiagnostic(Level level, Phase phase, String message, SourceLocation location, String hint) {
        this.level = level;
        this.phase = phase;
        this.message = message;
        this.location = location;
        this.hint = hint;
    }
    
    public static CompilerDiagnostic error(Phase phase, String message, SourceLocation location) {
        return new CompilerDiagnostic(Level.ERROR, phase, message, location, null);
    }
    
    public static CompilerDiagnostic error(Phase phase, String message, SourceLocation location, String hint) {
        return new CompilerDiagnostic(Level.ERROR, phase, message, location, hint);
    }
    
    public static CompilerDiagnostic warning(Phase phase, String message, SourceLocation location) {
        return new CompilerDiagnostic(Level.WARNING, phase, message, location, null);
    }
    
    public static CompilerDiagnostic info(Phase phase, String message) {
        return new CompilerDiagnostic(Level.INFO, phase, message, null, null);
    }
    
    public Level getLevel() { return level; }
    public Phase getPhase() { return phase; }
    public String getMessage() { return message; }
    public SourceLocation getLocation() { return location; }
    public String getHint() { return hint; }
    
    public boolean isError() { return level == Level.ERROR; }
    
    /**
     * Format diagnostic for console output with colors and location
     */
    public String format(boolean useColors) {
        StringBuilder sb = new StringBuilder();
        
        // Color prefix
        if (useColors) {
            sb.append(level.getColor());
        }
        
        // Level symbol and phase
        sb.append(level.getSymbol()).append(" ");
        sb.append("[").append(phase.getDisplayName()).append("] ");
        
        // Location if available
        if (location != null) {
            sb.append(location.fileName()).append(":");
            sb.append(location.line()).append(":").append(location.column());
            sb.append(" - ");
        }
        
        // Message
        sb.append(message);
        
        // Reset color
        if (useColors) {
            sb.append("\u001B[0m");
        }
        
        // Hint on next line if available
        if (hint != null) {
            sb.append("\n  ");
            if (useColors) {
                sb.append("\u001B[36m"); // Cyan for hints
            }
            sb.append("Hint: ").append(hint);
            if (useColors) {
                sb.append("\u001B[0m");
            }
        }
        
        return sb.toString();
    }
    
    @Override
    public String toString() {
        return format(false);
    }
}
